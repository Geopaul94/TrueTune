# TrueTune — Project Brief

> Global rules: **personal/hobby project** → apply global rules lightly (clean
> architecture, concise, recommend-with-reason, useful CLAUDE.md). Skip the heavy
> ceremony (sprints/daily-push/formal audits) unless GP asks. Package is a
> personal namespace (`geo.*`), NOT the Laennec/employer namespace.

## Mission
The tuner + metronome musicians actually keep — every tuning and every
instrument free, rock-solid pitch detection on cheap phones, no dark patterns.
Beating GuitarTuna precisely where users resent it (paywalled tunings, ads
on top of the needle, inconsistent mic accuracy). Built strictly **phase by
phase**; stop and report against each phase's acceptance criteria before the
next.

The core insight: **this is a DSP problem, not a UI problem.** The tuner lives
or dies on whether the needle is accurate and stable on a real cheap phone
with a bass low B (~31 Hz). Prove that first; everything else follows.

## Stack
- Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt
- Native audio: **Oboe input** (LowLatency + Shared + Float + Unprocessed
  preset) + a lock-free SPSC ring, feeding an **MPM** (McLeod Pitch Method)
  detector running on a dedicated worker thread.
- minSdk 26 · compileSdk/targetSdk 35 · NDK 27.1.12297006 · CMake 3.22.1
- Oboe pulled as a **prefab AAR** (`com.google.oboe:oboe:1.9.0`), consumed in
  CMake via `find_package(oboe CONFIG)` — needs `buildFeatures.prefab = true`
  and `-DANDROID_STL=c++_shared` (Oboe is a shared prefab).

## Locked decisions (from build brief §10)
- App name: **TrueTune** (working title; can be rebranded before release)
- Package: **geo.truetune** (personal, per feedback rule)
- Pitch algorithm: **MPM** (McLeod)
- Engine reuse: **standalone C++** for now; extract a shared `:audio-engine`
  module later once both TrueTune and Aurora Piano have proven real-time code.
- Instrument list = build brief §5.2 (chromatic + guitar 6/7/12 + bass 4/5 +
  ukulele + violin/viola/cello + mandolin + banjo + all common alt tunings)
- Free/Pro split = build brief §8 (whole tuner + full metronome free; drone +
  pitch-history + advanced stats + no-ads as Pro)
- Practice tracker: **included in v1**

## Build & run
```bash
# The Homebrew `gradle`/`java` on PATH resolve to JDK 25, which Gradle can't parse.
# Always pin JDK 17 for any Gradle command:
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home
cd ~/TrueTune
./gradlew :app:assembleDebug          # build (native + Kotlin)
./gradlew :app:installDebug           # install to a connected device
```
- SDK path is in `local.properties` (gitignored).
- **Audio MUST be tested on a real device** — emulators lie about mic
  behavior, sample rates, and low-latency paths. Cheap Android phones are the
  target — that's where every rival fails.
- compileSdk/targetSdk = 36 is a **Phase 5 release-prep** bump (matches
  Aurora Piano's pattern). Phase 0 uses 35 to stay on the known-good toolchain.

## Architecture (layering — domain has no Android imports)
- `presentation/` — Compose + ViewModels
  - `theme/` — Material 3 palette (cool sky/steel, signal cyan accent)
  - `tuner/` — Phase 1 tuner screen: gauge/needle, note display, A4 sheet
    - `TunerScreen.kt` — main screen (mic permission, start/stop, A4 button)
    - `TunerViewModel.kt` — EMA smoothing, maps PitchReading → TunerState
    - `components/TunerGauge.kt` — 240° Canvas arc with spring-animated needle
    - `components/NoteDisplay.kt` — big note name + octave + Hz + cents readout
    - `components/A4Sheet.kt` — bottom sheet slider for A4 reference (415–466 Hz)
  - `diagnostics/` — Phase 0 text-only readout (kept for debugging, not wired)
- `domain/music/` — `NoteMapper` (freq → note + cents, configurable A4, 14 unit tests)
- `domain/model/` — `TunerState`, `AccuracyBand` (IN_TUNE ±3¢, NEAR ±10¢, OFF, NONE)
- `domain/audio/` — `PitchStream` interface, `PitchReading` model, `NoteNaming` (Phase 0 legacy)
- `data/audio/` — `NativePitchStream` (polls bridge at ~30 Hz into StateFlow)
  + `PitchStreamBridge` (JNI `external fun`s, `System.loadLibrary`)
- `data/prefs/` — `TunerPreferences` (DataStore for A4 reference pitch)
- `di/` — Hilt bindings for `PitchStream`
- `cpp/` — the real-time detector
  - `AudioInput.*` — owns the Oboe input stream + the worker; the callback
    only pushes samples into `FloatRing`, everything else runs off-thread
  - `PitchDetector.*` — MPM (NSDF + peak picking with McLeod's `k=0.9` rule +
    parabolic interpolation for sub-sample τ)
  - `FloatRing.h` — lock-free SPSC ring of floats (audio → worker)
  - `native-lib.cpp` — JNI entry points (bound to `PitchStreamBridge`)

### The one rule that matters most
**Never allocate, lock, log, or call into the JVM inside the Oboe input
callback.** The callback only appends to the ring buffer. The worker thread
does the MPM math off the audio thread; results reach Kotlin via lock-free
atomics polled at UI rate. Wobbly needles or dropouts = suspect a callback-
safety violation first.

### Detector defaults (worth knowing when tuning behavior)
- Window: **4096 samples** (~85ms at 48kHz) — 2+ periods of bass low B.
- Hop: **1024 samples** (~21ms cadence).
- Silence gate: **-50 dBFS** RMS — below this, report no pitch.
- Clarity gate: **NSDF peak ≥ 0.5** to publish; **0.9 · max** ratio for peak selection.
- Frequency band: **27.5 Hz (A0) → 4200 Hz** — covers bass to piccolo fundamentals.

## Resume here (current status — 2026-07-02)

**Phase 0 — CODE COMPLETE.** Oboe input + MPM pitch detector + diagnostics screen.
On-device verification still pending (no device connected during dev).

**Phase 1 — Tuner UI — CODE COMPLETE.**
- `NoteMapper` in `domain/music/` — freq → nearest note + cents, configurable A4,
  14 unit tests (all passing). Covers A0–C8, handles custom A4 (415–466 Hz).
- Tuner gauge: 240° Canvas arc with 21 tick marks, spring-animated needle,
  green glow in the ±3¢ in-tune zone, smooth color transitions per accuracy band.
- Note display: large note name + octave, Hz readout, cents deviation.
- EMA smoothing (α=0.3) in TunerViewModel — settles in ~5 readings (~170ms).
  Snaps on note change to avoid sweeping across the gauge.
- Silence handling: after 10 silent frames (~330ms) the needle resets to center.
- A4 reference pitch: adjustable via bottom sheet slider (415–466 Hz), persisted
  in DataStore. Shown as a tappable chip in the top-right corner.
- Haptic pulse on entering in-tune band.
- In-tune accuracy = ±3¢, near = ±10¢ (tighter than most competitors).
- Diagnostics screen kept for debugging but not wired in main activity.

### Pending before Phase 2 starts
1. **On-device verification** — install on real hardware and confirm:
   - Gauge needle is smooth and responsive when playing notes
   - In-tune state triggers reliably at ±3¢
   - Silence → needle fades to center, no garbage
   - A4 adjustment shifts targets correctly
   - Low bass notes lock cleanly
2. GP review → sign off before Phase 2.

## Phase roadmap
- Phase 2 (NEXT) — Instruments + all tunings + custom tunings (the anti-GuitarTuna
  wedge: chromatic + guitar 6/7/12 + bass 4/5 + ukulele + violin/viola/cello +
  mandolin + banjo + all common alt tunings, ALL free). Auto mode + manual/string mode.
- Phase 3 — Metronome (scheduler in native; UI, subdivisions, accents, tempo tap,
  setlists).
- Phase 4 — Drone + pitch-history graph + practice tracker (session logs, streaks).
- Phase 5 — Onboarding + AdMob + Pro + OEM/mic matrix + compileSdk/target 36 + release.

## Known gotchas (save future-me the debugging)
- **JDK 17 is mandatory** for Gradle here (see Build & run). Homebrew `java`
  is JDK 25 which Gradle can't parse.
- **`InputPreset::Unprocessed`** is not optional. Without it the framework
  runs NS/AGC/AEC on the mic stream, which flattens transients and drags the
  fundamental frequency around — a guitar reads several cents flat and wobbles.
- **SharingMode Shared for input**: Exclusive input isn't supported on the
  vast majority of Android devices. Shared + LowLatency still gets the AAudio
  fast path where available.
- **Ring is `pushDropNewest`** (not drop-oldest). If the worker stalls we lose
  brand-new samples. Since the ring is ~340ms deep and the detector fires every
  ~85ms, overflow means something's seriously wrong.
