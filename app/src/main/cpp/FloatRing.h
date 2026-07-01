#pragma once
//
// FloatRing — a wait-free, lock-free single-producer / single-consumer ring
// buffer of floats.
//
// Producer: the Oboe input callback (real-time audio thread).
// Consumer: the pitch-detection worker thread.
//
// Real-time safety: no allocations, no locks, no system calls. All operations
// are just atomic loads/stores + array indexing.
//
// Overflow policy: if the consumer stalls and the ring fills up, `push` returns
// short and the caller sees dropped samples. For a tuner this is the honest
// choice — we'd rather lose a few frames than block the audio thread. In
// practice the ring is 16384 samples (~340ms at 48kHz) and the detector runs
// every ~85ms, so this never happens unless something is very wrong.
//
// Monotonic counters: writeIdx_/readIdx_ are total-samples-ever counters, not
// modular indices. `(writeIdx_ - readIdx_)` gives the current fill in signed
// arithmetic without a "full vs empty" ambiguity. size_t is 64-bit on our
// targets, so overflow is a non-issue (thousands of years at 48kHz).
//
#include <array>
#include <atomic>
#include <cstddef>
#include <cstring>

template <std::size_t Capacity>
class FloatRing {
    static_assert((Capacity & (Capacity - 1)) == 0,
                  "FloatRing capacity must be a power of two");

public:
    FloatRing() { buf_.fill(0.0f); }

    // Producer side. Returns the number of samples actually written.
    // Real-time safe.
    std::size_t push(const float* src, std::size_t n) noexcept {
        const std::size_t w = writeIdx_.load(std::memory_order_relaxed);
        const std::size_t r = readIdx_.load(std::memory_order_acquire);
        const std::size_t space = Capacity - (w - r);
        if (n > space) n = space;
        for (std::size_t i = 0; i < n; ++i) {
            buf_[(w + i) & MASK] = src[i];
        }
        writeIdx_.store(w + n, std::memory_order_release);
        return n;
    }

    // Consumer side. Returns the number of samples actually read.
    std::size_t pop(float* dst, std::size_t n) noexcept {
        const std::size_t r = readIdx_.load(std::memory_order_relaxed);
        const std::size_t w = writeIdx_.load(std::memory_order_acquire);
        const std::size_t avail = w - r;
        if (n > avail) n = avail;
        for (std::size_t i = 0; i < n; ++i) {
            dst[i] = buf_[(r + i) & MASK];
        }
        readIdx_.store(r + n, std::memory_order_release);
        return n;
    }

    std::size_t available() const noexcept {
        return writeIdx_.load(std::memory_order_acquire) -
               readIdx_.load(std::memory_order_relaxed);
    }

    // Drop-in reset used between start/stop cycles. Not safe while either
    // thread is running.
    void reset() noexcept {
        writeIdx_.store(0, std::memory_order_relaxed);
        readIdx_.store(0, std::memory_order_relaxed);
    }

private:
    static constexpr std::size_t MASK = Capacity - 1;
    std::array<float, Capacity> buf_{};
    std::atomic<std::size_t> writeIdx_{0};
    std::atomic<std::size_t> readIdx_{0};
};
