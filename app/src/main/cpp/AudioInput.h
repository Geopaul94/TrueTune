#pragma once
//
// AudioInput — owns the Oboe input (microphone) stream and the pitch-detection
// worker thread.
//
// Threading model:
//   * Audio thread (Oboe input callback): drops raw float mono samples into
//     `ring_`. RT-safe. Never blocks, never allocates, never logs.
//   * Worker thread: pops samples from the ring into a sliding window, runs the
//     detector every `kHop` samples, publishes results into std::atomics.
//   * JNI/UI thread: calls `start()`/`stop()` (serialized upstream by a mutex
//     in native-lib.cpp), and reads the atomic reading at ~30 Hz.
//
// This split keeps the audio callback bounded and predictable, which is the
// whole point of a low-latency input path.
//
#include <atomic>
#include <memory>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

#include "FloatRing.h"
#include "PitchDetector.h"

class AudioInput final : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    AudioInput();
    ~AudioInput() override;

    AudioInput(const AudioInput&)            = delete;
    AudioInput& operator=(const AudioInput&) = delete;

    // Open the mic stream + spawn the worker. Idempotent — returns true if
    // already running. Returns false if Oboe refuses to open the stream.
    bool start();

    // Close the stream + join the worker. Idempotent.
    void stop();

    // ---- Readings (call from any thread) ---------------------------------
    float   lastFrequency()  const { return lastFrequency_.load(std::memory_order_acquire); }
    float   lastClarity()    const { return lastClarity_  .load(std::memory_order_acquire); }
    float   lastRmsDb()      const { return lastRmsDb_    .load(std::memory_order_acquire); }
    int32_t sampleRate()     const { return sampleRate_   .load(std::memory_order_relaxed); }
    int32_t framesPerBurst() const { return burst_        .load(std::memory_order_relaxed); }
    int32_t bufferSize()     const { return bufferSize_   .load(std::memory_order_relaxed); }
    int32_t xruns()          const { return xruns_        .load(std::memory_order_relaxed); }
    int32_t apiUsed()        const { return apiUsed_      .load(std::memory_order_relaxed); }
    bool    lowLatencyPath() const { return lowLatencyPath_.load(std::memory_order_relaxed); }
    bool    isRunning()      const { return running_      .load(std::memory_order_acquire); }

    // ---- Oboe callbacks --------------------------------------------------
    // Real-time-safe. See the note in native-lib.cpp / class doc-comment above.
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;

    // Error callbacks — called on Oboe's own thread, not the audio thread, so
    // logging is allowed here.
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose (oboe::AudioStream* stream, oboe::Result error) override;

private:
    // Worker entry point. Runs while `workerRunning_` is true.
    void workerLoop();

    // Window and hop drive the detector cadence.
    //   Window 4096 samples @ 48kHz ≈ 85 ms — enough to hold 2+ periods of a
    //   30 Hz bass low-B, which is where every phone-based tuner falls apart.
    //   Hop 1024 → new pitch estimate every ~21 ms — the needle feels fast
    //   without being twitchy after smoothing.
    static constexpr std::size_t kWindow = 4096;
    static constexpr std::size_t kHop    = 1024;

    std::shared_ptr<oboe::AudioStream> stream_;
    FloatRing<16384> ring_;   // ~340ms of slack; producer never blocks

    std::vector<float>             window_;   // sized kWindow, allocated once
    std::unique_ptr<PitchDetector> detector_;

    std::thread             worker_;
    std::atomic<bool>       workerRunning_{false};
    std::atomic<bool>       running_{false};

    // Latest publishable reading. On ARM64 + x86_64 (our only two ABIs) these
    // atomics are lock-free — a single MOV on load/store.
    std::atomic<float>   lastFrequency_{-1.0f};
    std::atomic<float>   lastClarity_  {0.0f};
    std::atomic<float>   lastRmsDb_    {-120.0f};

    // Stream properties, populated at open time so the diagnostics screen can
    // show what we actually got (not just what we asked for).
    std::atomic<int32_t> sampleRate_    {0};
    std::atomic<int32_t> burst_         {0};
    std::atomic<int32_t> bufferSize_    {0};
    std::atomic<int32_t> xruns_         {0};
    std::atomic<int32_t> apiUsed_       {0};  // 0=Unspecified, 1=OpenSLES, 2=AAudio
    std::atomic<bool>    lowLatencyPath_{false};
};
