package geo.truetune.domain.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Pure-Kotlin contract for a live pitch-detection stream. The domain layer
 * intentionally has no Android or JNI imports — the native implementation
 * lives in the data layer and is bound here via Hilt.
 *
 * Phase 0 surface is deliberately minimal: start the mic, stop it, and expose
 * the latest reading. Phase 1 will add A4 configuration and smoothing hooks.
 */
interface PitchStream {

    /** Snapshot of the latest reading; the UI observes this. */
    val reading: StateFlow<PitchReading>

    /**
     * Open the mic and start detecting pitches. Returns true on success. The
     * caller MUST have RECORD_AUDIO permission granted before calling.
     */
    suspend fun start(): Boolean

    /** Close the mic and stop the detection worker. Idempotent. */
    suspend fun stop()
}
