#include "AudioInput.h"

#include <android/log.h>
#include <chrono>
#include <cstring>

#define LOG_TAG "TrueTune"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioInput::AudioInput()
    : window_(kWindow, 0.0f) {
    // Detector is (re)built in start() once we know the real sample rate.
}

AudioInput::~AudioInput() {
    stop();
}

bool AudioInput::start() {
    if (running_.load(std::memory_order_acquire)) return true;

    // Build the input stream.
    //
    // Preset::Unprocessed is the single most important request here. It tells
    // the framework to skip built-in noise suppression, AGC, and echo
    // cancellation — all of which mangle pitch by design. Without this, a
    // guitar reads several cents flat and wobbles.
    //
    // SharingMode::Shared is used for input on Android because Exclusive input
    // is not supported on the vast majority of devices. LowLatency performance
    // mode still gets us the AAudio fast path where available.
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Shared)
     ->setInputPreset(oboe::InputPreset::Unprocessed)
     ->setFormat(oboe::AudioFormat::Float)
     ->setChannelCount(oboe::ChannelCount::Mono)
     ->setSampleRate(48000)
     ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
     ->setDataCallback(this)
     ->setErrorCallback(this);

    oboe::Result r = b.openStream(stream_);
    if (r != oboe::Result::OK || !stream_) {
        LOGE("openStream failed: %s", oboe::convertToText(r));
        return false;
    }

    // Record the actual params.
    sampleRate_    .store(stream_->getSampleRate());
    burst_         .store(stream_->getFramesPerBurst());
    bufferSize_    .store(stream_->getBufferSizeInFrames());
    apiUsed_       .store(static_cast<int32_t>(stream_->getAudioApi()));
    lowLatencyPath_.store(stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency);

    // Reset detector state to match the actual sample rate — the device might
    // not have honoured 48000 (some cheap SoCs cap at 44100).
    detector_ = std::make_unique<PitchDetector>(kWindow, sampleRate_.load());
    ring_.reset();
    lastFrequency_.store(-1.0f);
    lastClarity_  .store(0.0f);
    lastRmsDb_    .store(-120.0f);
    xruns_        .store(0);

    // Spawn the worker BEFORE requesting Start — the callback may fire the
    // instant the stream leaves Started state and we don't want the ring to
    // fill with nobody consuming.
    workerRunning_.store(true, std::memory_order_release);
    worker_ = std::thread(&AudioInput::workerLoop, this);

    r = stream_->requestStart();
    if (r != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(r));
        workerRunning_.store(false, std::memory_order_release);
        if (worker_.joinable()) worker_.join();
        stream_->close();
        stream_.reset();
        return false;
    }

    running_.store(true, std::memory_order_release);
    LOGI("Input started: sr=%d, burst=%d, buf=%d, api=%d, lowLatency=%d",
         sampleRate_.load(), burst_.load(), bufferSize_.load(),
         apiUsed_.load(), (int)lowLatencyPath_.load());
    return true;
}

void AudioInput::stop() {
    if (!running_.load(std::memory_order_acquire) && !workerRunning_.load()) return;

    // Stop the stream first so no more callbacks arrive.
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }

    // Then shut the worker down cleanly.
    workerRunning_.store(false, std::memory_order_release);
    if (worker_.joinable()) worker_.join();

    running_.store(false, std::memory_order_release);
    LOGI("Input stopped");
}

oboe::DataCallbackResult AudioInput::onAudioReady(
    oboe::AudioStream* /*stream*/, void* audioData, int32_t numFrames) {
    // RT-safe: single memcpy-equivalent into the ring. If the ring is full the
    // extra samples are dropped — a stalled worker is a bug we'd see in xruns
    // and diagnostics, not a reason to block the audio thread.
    const float* src = static_cast<const float*>(audioData);
    ring_.push(src, static_cast<std::size_t>(numFrames));
    return oboe::DataCallbackResult::Continue;
}

void AudioInput::onErrorBeforeClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("onErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioInput::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    // Phase 0: we don't auto-restart. Phase 5 will handle disconnects (mic
    // route changes, headset unplug, etc.) by rebuilding the stream.
    LOGE("onErrorAfterClose: %s", oboe::convertToText(error));
    running_.store(false, std::memory_order_release);
}

void AudioInput::workerLoop() {
    // Analysis cadence. When the ring has at least `kHop` new samples we
    // shift the window left by `kHop` and pull `kHop` fresh samples in — a
    // classic sliding-window overlap. Each shift triggers one analysis pass.
    //
    // Bootstrapping: at start there is nothing in the window; we need to
    // accumulate a full `kWindow` before the first analysis. `bootstrapNeeded`
    // tracks how many samples of the initial fill remain.
    std::size_t bootstrapNeeded = kWindow;
    // Position where the next batch of samples goes during bootstrap.
    std::size_t bootstrapWriteAt = 0;

    // Poll xruns roughly once per second — cheap, and useful for the
    // diagnostics screen. AAudio's xrun counter is safe to read from any
    // thread.
    auto lastXrunPoll = std::chrono::steady_clock::now();

    while (workerRunning_.load(std::memory_order_acquire)) {
        // Phase A: bootstrap — fill the window from empty.
        if (bootstrapNeeded > 0) {
            const std::size_t want = std::min<std::size_t>(bootstrapNeeded, ring_.available());
            if (want > 0) {
                const std::size_t got = ring_.pop(window_.data() + bootstrapWriteAt, want);
                bootstrapWriteAt += got;
                bootstrapNeeded  -= got;
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
                continue;
            }
            if (bootstrapNeeded == 0) {
                // First full window — analyze it.
                auto r = detector_->analyze(window_.data());
                lastFrequency_.store(r.frequency, std::memory_order_release);
                lastClarity_  .store(r.clarity,   std::memory_order_release);
                lastRmsDb_    .store(r.rmsDb,     std::memory_order_release);
            }
            continue;
        }

        // Phase B: steady state — slide by kHop when we have that many new samples.
        if (ring_.available() >= kHop) {
            // Shift left: window[0 .. W-kHop) = window[kHop .. W).
            std::memmove(window_.data(), window_.data() + kHop,
                         (kWindow - kHop) * sizeof(float));
            // Pull fresh samples into the tail.
            std::size_t got = ring_.pop(window_.data() + (kWindow - kHop), kHop);
            if (got < kHop) {
                // Underrun on pop after we said available() was enough. This
                // only happens if the audio thread wrote very few samples
                // between our check and pop — rare. Zero-pad the rest.
                std::memset(window_.data() + (kWindow - kHop) + got, 0,
                            (kHop - got) * sizeof(float));
            }

            auto r = detector_->analyze(window_.data());
            lastFrequency_.store(r.frequency, std::memory_order_release);
            lastClarity_  .store(r.clarity,   std::memory_order_release);
            lastRmsDb_    .store(r.rmsDb,     std::memory_order_release);
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
        }

        // Xrun polling (once per second is plenty).
        auto now = std::chrono::steady_clock::now();
        if (now - lastXrunPoll > std::chrono::seconds(1)) {
            if (stream_) {
                auto rr = stream_->getXRunCount();
                if (rr) xruns_.store(rr.value(), std::memory_order_relaxed);
            }
            lastXrunPoll = now;
        }
    }
}
