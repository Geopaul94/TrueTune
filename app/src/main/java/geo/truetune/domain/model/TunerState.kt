package geo.truetune.domain.model

/**
 * How close the detected pitch is to the target note. Drives the visual
 * feedback: color, animation intensity, haptic trigger.
 */
enum class AccuracyBand {
    /** Within ±3 cents — practically perfect. Trigger the "in tune" celebration. */
    IN_TUNE,
    /** Within ±10 cents — close, almost there. */
    NEAR,
    /** Beyond ±10 cents — clearly off. */
    OFF,
    /** No confident pitch detected (silence, noise, low clarity). */
    NONE,
}

/**
 * Processed tuner state ready for the UI. The ViewModel produces this by
 * combining raw [PitchReading] data with note mapping and smoothing.
 */
data class TunerState(
    val noteName: String = "",
    val octave: Int = 4,
    val cents: Float = 0f,
    val frequencyHz: Float = 0f,
    val targetFrequencyHz: Float = 0f,
    val accuracy: AccuracyBand = AccuracyBand.NONE,
    val isListening: Boolean = false,
    val hasPitch: Boolean = false,
    val a4Hz: Float = 440f,
)
