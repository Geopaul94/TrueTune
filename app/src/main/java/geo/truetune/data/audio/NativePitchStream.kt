package geo.truetune.data.audio

import geo.truetune.domain.audio.AudioApi
import geo.truetune.domain.audio.PitchReading
import geo.truetune.domain.audio.PitchStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one and only implementation of [PitchStream]. Owns the JNI bridge and
 * a coroutine that polls it at ~30 Hz while the stream is running.
 *
 * We poll instead of pushing from native because JNI upcalls (native → JVM)
 * are hostile to real-time code, and the audio callback must never touch the
 * JVM. Native writes to lock-free atomics; Kotlin reads them at UI rate.
 */
@Singleton
class NativePitchStream @Inject constructor(
    private val bridge: PitchStreamBridge,
) : PitchStream {

    private val _reading = MutableStateFlow(PitchReading())
    override val reading: StateFlow<PitchReading> = _reading.asStateFlow()

    // Dedicated scope — we cancel its children on stop() without disturbing
    // any caller-provided scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    override suspend fun start(): Boolean = withContext(Dispatchers.Default) {
        val ok = bridge.nativeStart()
        if (ok) {
            // Cancel any lingering poller from a previous cycle (defensive).
            pollJob?.cancel()
            pollJob = scope.launch { pollLoop() }
        }
        ok
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        pollJob?.cancel()
        pollJob = null
        bridge.nativeStop()
        // Reset the observable state to a clean "idle" so the UI doesn't
        // keep showing the last-detected note after we stop listening.
        _reading.value = PitchReading()
    }

    private suspend fun pollLoop() {
        // ~30 Hz — smoother than the eye needs, cheap in JNI cost. Every tick
        // is ~11 atomic loads across the fence.
        while (true) {
            _reading.value = PitchReading(
                frequencyHz    = bridge.nativeGetFrequency(),
                clarity        = bridge.nativeGetClarity(),
                rmsDb          = bridge.nativeGetRmsDb(),
                sampleRate     = bridge.nativeGetSampleRate(),
                framesPerBurst = bridge.nativeGetFramesPerBurst(),
                bufferSize     = bridge.nativeGetBufferSize(),
                xruns          = bridge.nativeGetXRuns(),
                api            = AudioApi.fromCode(bridge.nativeGetApi()),
                lowLatencyPath = bridge.nativeIsLowLatency(),
                isRunning      = bridge.nativeIsRunning(),
            )
            delay(33L)
        }
    }
}
