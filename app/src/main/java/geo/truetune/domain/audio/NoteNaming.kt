package geo.truetune.domain.audio

import kotlin.math.ln
import kotlin.math.round

/**
 * Minimal frequency → note-name mapping used by the Phase 0 diagnostics
 * screen. Phase 1 will replace this with the real domain/music module: proper
 * `Note` type, cents deviation, configurable A4, transposition, etc.
 *
 * Kept intentionally lean so the diagnostics screen has a friendly readout
 * without prematurely designing the note API.
 */
object NoteNaming {

    private val names = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /**
     * Convert a frequency to (name, octave, centsOffset). Reference: A4 = 440 Hz,
     * 12-tone equal temperament. Returns null for non-positive freqs.
     */
    fun name(frequencyHz: Float, a4: Float = 440f): Triple<String, Int, Float>? {
        if (frequencyHz <= 0f) return null
        // Semitones from A4. Adding 69 puts us on MIDI numbers, where 69 = A4.
        val midi = 69.0 + 12.0 * ln((frequencyHz / a4).toDouble()) / ln(2.0)
        val nearestMidi = round(midi).toInt()
        val cents = ((midi - nearestMidi) * 100.0).toFloat()
        val idx = ((nearestMidi % 12) + 12) % 12
        val octave = nearestMidi / 12 - 1
        return Triple(names[idx], octave, cents)
    }
}
