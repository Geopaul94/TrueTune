package geo.truetune.domain.audio

/**
 * Snapshot of everything the UI wants to show about the current mic input.
 *
 * `frequencyHz` is -1 when the detector has no confident answer (silence or
 * noisy input). Kotlin callers should treat any negative value as "no pitch".
 *
 * `clarity` is MPM's peak-normalized score in [0, 1]. Above ~0.9 is strong;
 * below 0.5 the detector refuses to publish and this field will be stale.
 */
data class PitchReading(
    val frequencyHz: Float = -1f,
    val clarity: Float = 0f,
    val rmsDb: Float = -120f,
    val sampleRate: Int = 0,
    val framesPerBurst: Int = 0,
    val bufferSize: Int = 0,
    val xruns: Int = 0,
    val api: AudioApi = AudioApi.UNSPECIFIED,
    val lowLatencyPath: Boolean = false,
    val isRunning: Boolean = false,
) {
    /** True when frequencyHz is a real, trustworthy reading. */
    val hasPitch: Boolean get() = frequencyHz > 0f
}

enum class AudioApi(val code: Int) {
    UNSPECIFIED(0),
    OPENSL_ES(1),
    AAUDIO(2);

    companion object {
        fun fromCode(c: Int): AudioApi = entries.firstOrNull { it.code == c } ?: UNSPECIFIED
    }
}
