#pragma once
//
// PitchDetector — monophonic fundamental-frequency estimator based on the
// McLeod Pitch Method (MPM).
//
// Why MPM (not FFT, not YIN):
//   * Accurate at low frequencies. Bass guitar low B ≈ 31 Hz. An FFT with a
//     4096-sample window at 48kHz has bin spacing ≈ 11.7 Hz — too coarse.
//     Time-domain autocorrelation-family methods interpolate to sub-sample
//     precision and are naturally low-freq friendly.
//   * Octave-error resistant. The classical tuner failure ("E2 shown as E3")
//     comes from picking the strongest autocorrelation peak, which for a
//     harmonic signal is often at half the true period. MPM's "first peak
//     above 0.9 × global-max" rule picks the true fundamental.
//   * Cheap enough for a phone. Naive NSDF is O(W · maxLag) but with W=4096
//     and maxLag ~1700 it lands in the low-single-digit ms on a modern ARM
//     core — well below the ~85ms cadence at which we invoke it.
//
// Real-time safety: all buffers are preallocated in the constructor. `analyze`
// does no allocations, no I/O, no locks. It IS NOT thread-safe against itself
// — call from a single thread (the worker).
//
#include <cstddef>
#include <vector>

class PitchDetector {
public:
    struct Result {
        float frequency;  // Hz. -1.0f if no confident pitch was detected.
        float clarity;    // 0..1 — the NSDF value at the chosen peak.
        float rmsDb;      // Root-mean-square level of the window, in dBFS.
    };

    // W: analysis window length in samples. Should be at least ~2× the period
    //    of the lowest note you want to detect (i.e. sr/minFreq).
    // sr: sample rate of the input signal in Hz.
    // minFreq/maxFreq: bounds the analyzer will accept, in Hz. Anything outside
    //    is rejected (returned as -1). Defaults cover bass low B (30.87 Hz) up
    //    to well above the top of a violin's fundamental range.
    PitchDetector(std::size_t W, int sr,
                  float minFreq = 27.5f,   // A0
                  float maxFreq = 4200.0f); // above high-C on piccolo fundamental

    // Analyze W samples of mono float PCM. Real-time safe.
    Result analyze(const float* window);

    // Tunable thresholds. Kept public so a settings screen can adjust them
    // per-user later. Defaults are conservative (favor stability over
    // sensitivity) — a tuner that flickers on quiet noise reads as "broken".
    float silenceDb   = -50.0f; // below this RMS, report no pitch
    float minClarity  =  0.5f;  // NSDF peak below this ⇒ untrustworthy
    float peakRatio   =  0.9f;  // McLeod's k: accept first peak ≥ k · maxPeak

private:
    std::size_t W_;
    int         sr_;
    std::size_t minLag_;        // sr / maxFreq — smallest lag we consider
    std::size_t maxLag_;        // sr / minFreq — largest lag we consider
    std::vector<float> nsdf_;   // preallocated: maxLag_ + 1 entries
};
