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
  - `diagnostics/` — Phase 0 text-only mic → detector readout
- `domain/audio/` — `PitchStream` interface, `PitchReading` model,
  `NoteNaming` (minimal freq → note helper; real music module lands in Phase 1)
- `data/audio/` — `NativePitchStream` (polls the bridge at ~30 Hz into a
  `StateFlow`) + `PitchStreamBridge` (JNI `external fun`s, `System.loadLibrary`)
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

## Resume here (current status — 2026-07-01)

**Phase 0 — Add Oboe input + prove MPM pitch detection — CODE COMPLETE.**
APK builds and packages `libtruetune_audio.so` + `liboboe.so` + `libc++_shared.so`
for arm64-v8a & x86_64 (13MB debug APK). Kotlin/Compose text-only diagnostics
screen: Start/Stop mic + live readout of frequency, note-guess, cents,
clarity, dBFS, sample rate, burst, buffer, xruns, API, low-latency path.
Runtime RECORD_AUDIO permission handled.

### Pending before Phase 1 starts
1. **On-device verification** — no device was connected during code-complete.
   Run `./gradlew :app:installDebug` on real hardware — ideally the target
   cheap phone (Redmi / Realme). Then confirm:
   - Whistle A4 → reads ~440 Hz ± a few cents, stable (not wobbling).
   - Guitar low E (~82 Hz) → reads correctly, no octave errors.
   - **Bass low B (~31 Hz)** → the make-or-break test (competitors fail here).
   - Silence → no false pitch (silence gate < -50 dBFS holds).
   - `lowLatencyPath = true` in diagnostics on Pixels; acceptable fallback on cheap phones.
2. GP review → sign off Phase 0 before starting Phase 1.

## Phase roadmap
- **Phase 1 (NEXT) — Tuner UI.** Replace the text diagnostics with the real tuner face:
  - Analog gauge / needle (Compose Canvas), note label + cents deviation, in-tune band (±5¢).
  - Smoothing (rolling median of last N readings) to kill needle jitter without adding lag.
  - Configurable A4 reference (432 / 440 / 442 Hz).
  - Clean transition when clarity drops (fade needle to grey, don't freeze on old value).
  - Acceptance: guitar tune-up feel matches or beats GuitarTuna on a cheap phone.
- Phase 2 — Instruments + all tunings + custom tunings (the anti-GuitarTuna wedge:
  chromatic + guitar 6/7/12 + bass 4/5 + ukulele + violin/viola/cello + mandolin +
  banjo + all common alt tunings, ALL free).
- Phase 3 — Metronome (scheduler in native for jitter-free timing; UI, subdivisions,
  accents, tempo tap, setlists).
- Phase 4 — Drone (sustain a reference pitch) + pitch-history graph + practice tracker
  (session logs, streaks).
- Phase 5 — Onboarding + AdMob + Pro (drone/history/stats/no-ads) + OEM/mic device
  matrix + compileSdk/target 36 bump + release prep.

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
