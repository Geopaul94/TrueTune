#include "PitchDetector.h"

#include <algorithm>
#include <cmath>

PitchDetector::PitchDetector(std::size_t W, int sr, float minFreq, float maxFreq)
    : W_(W), sr_(sr) {
    // Convert frequency bounds to autocorrelation-lag bounds. A note of
    // frequency f has period sr/f samples; NSDF picks the peak at that lag.
    minLag_ = static_cast<std::size_t>(std::floor(sr_ / maxFreq));
    maxLag_ = static_cast<std::size_t>(std::ceil (sr_ / minFreq));
    // Guardrails: never let maxLag get so long that the effective correlation
    // window (W - maxLag) becomes tiny — MPM breaks down when there are only a
    // handful of overlapping samples.
    if (maxLag_ > W_ / 2) maxLag_ = W_ / 2;
    if (minLag_ < 2)      minLag_ = 2;
    nsdf_.assign(maxLag_ + 1, 0.0f);
}

PitchDetector::Result PitchDetector::analyze(const float* x) {
    Result out{-1.0f, 0.0f, -120.0f};

    // ---- Level check ------------------------------------------------------
    // Compute RMS in one pass. Below `silenceDb` we don't even try — a jittery
    // needle on quiet input is the single biggest way tuners feel broken.
    double energy = 0.0;
    for (std::size_t i = 0; i < W_; ++i) energy += static_cast<double>(x[i]) * x[i];
    const double rms = std::sqrt(energy / static_cast<double>(W_));
    out.rmsDb = (rms > 1e-6) ? 20.0f * std::log10(static_cast<float>(rms))
                             : -120.0f;
    if (out.rmsDb < silenceDb) return out;

    // ---- NSDF (McLeod's Normalized Square Difference Function) -----------
    //
    // n(τ) = 2 · Σ x[j]·x[j+τ]  /  Σ ( x[j]² + x[j+τ]² )      j = 0..W-1-τ
    //
    // At τ = 0 this is 1.0. It stays high for a while, dips negative, then
    // spikes near each period boundary. The true fundamental period is the
    // location of the first sufficiently-tall positive peak after the initial
    // negative crossing.
    //
    // This double loop is the whole CPU cost of the detector. With W=4096 and
    // maxLag~1700 it is ~3.5M multiply-adds — a couple of ms on a modern ARM
    // core. We accept the naive form for clarity; if a cheap phone struggles
    // we can switch to an FFT-based autocorrelation later without changing
    // the interface.
    for (std::size_t tau = 0; tau <= maxLag_; ++tau) {
        double acf = 0.0;
        double sqSum = 0.0;
        const std::size_t end = W_ - tau;
        for (std::size_t j = 0; j < end; ++j) {
            const float a = x[j];
            const float b = x[j + tau];
            acf   += static_cast<double>(a) * b;
            sqSum += static_cast<double>(a) * a + static_cast<double>(b) * b;
        }
        nsdf_[tau] = (sqSum > 1e-12) ? static_cast<float>(2.0 * acf / sqSum)
                                     : 0.0f;
    }

    // ---- Peak picking (the anti-octave-error trick) ----------------------
    //
    // 1. Skip the initial positive lobe near τ=0 (NSDF starts at 1.0). We wait
    //    for the first zero crossing into negative territory.
    // 2. From there, walk positive lobes; the maximum inside each lobe is a
    //    peak candidate. Remember the global maximum peak value.
    // 3. Pick the FIRST peak whose value is ≥ peakRatio × globalMax. This
    //    prefers the shorter-lag (higher-frequency) peak when there's near-tie,
    //    which is what we want — a harmonic signal has a peak at the true
    //    period AND at every integer multiple; we want the smallest one.
    // 4. Parabolic-interpolate around the chosen peak's neighbors for
    //    sub-sample precision on τ (and therefore on the reported frequency).

    // Step 1: walk past the initial hump.
    std::size_t tau = 1;
    while (tau <= maxLag_ && nsdf_[tau] > 0.0f) ++tau;
    if (tau > maxLag_) return out;  // no zero-crossing ⇒ no clear periodicity

    // Step 2: collect local maxima. Bounded by max ~ maxLag_/2 in theory —
    // 512 is a generous upper bound that never allocates.
    struct Peak { std::size_t tau; float value; };
    Peak peaks[512];
    std::size_t nPeaks = 0;
    float globalMax = 0.0f;

    while (tau <= maxLag_ && nPeaks < 512) {
        // Skip to the start of the next positive lobe.
        while (tau <= maxLag_ && nsdf_[tau] <= 0.0f) ++tau;
        if (tau > maxLag_) break;

        // Inside a positive lobe: track the argmax.
        std::size_t peakTau = tau;
        float peakVal = nsdf_[tau];
        while (tau <= maxLag_ && nsdf_[tau] > 0.0f) {
            if (nsdf_[tau] > peakVal) {
                peakVal = nsdf_[tau];
                peakTau = tau;
            }
            ++tau;
        }
        // Only accept peaks in the frequency band we care about.
        if (peakTau >= minLag_) {
            peaks[nPeaks++] = {peakTau, peakVal};
            if (peakVal > globalMax) globalMax = peakVal;
        }
    }

    if (nPeaks == 0 || globalMax < minClarity) return out;

    // Step 3: first peak clearing the ratio gate.
    const float gate = peakRatio * globalMax;
    Peak chosen = peaks[0];
    for (std::size_t i = 0; i < nPeaks; ++i) {
        if (peaks[i].value >= gate) { chosen = peaks[i]; break; }
    }

    // Step 4: parabolic interpolation. Fit a parabola through
    // (tau-1, y0), (tau, y1), (tau+1, y2); its vertex is at:
    //     shift = 0.5 · (y0 - y2) / (y0 - 2y1 + y2)
    // Clamped to ±1 to guard against a nearly-flat top.
    float refinedTau = static_cast<float>(chosen.tau);
    if (chosen.tau > 0 && chosen.tau < maxLag_) {
        const float y0 = nsdf_[chosen.tau - 1];
        const float y1 = nsdf_[chosen.tau];
        const float y2 = nsdf_[chosen.tau + 1];
        const float denom = y0 - 2.0f * y1 + y2;
        if (std::fabs(denom) > 1e-12f) {
            const float shift = 0.5f * (y0 - y2) / denom;
            if (shift > -1.0f && shift < 1.0f) {
                refinedTau = static_cast<float>(chosen.tau) + shift;
            }
        }
    }

    out.frequency = static_cast<float>(sr_) / refinedTau;
    out.clarity   = chosen.value;
    return out;
}
