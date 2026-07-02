package geo.truetune.domain.music

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.round

/**
 * Maps a detected frequency to the nearest musical note using 12-tone equal
 * temperament. The reference pitch (A4) is configurable — default 440 Hz,
 * adjustable 415–466 Hz for baroque/concert/custom tuning.
 *
 * All math is pure Kotlin with no Android imports so it can be unit-tested
 * on the JVM.
 */
object NoteMapper {

    private val NOTE_NAMES = arrayOf(
        "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"
    )

    // For display contexts that need ASCII-only (file names, logs)
    private val NOTE_NAMES_ASCII = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    private const val LN2 = 0.6931471805599453 // ln(2)

    data class NoteResult(
        val name: String,
        val nameAscii: String,
        val octave: Int,
        val midiNumber: Int,
        val cents: Float,
        val exactFrequencyHz: Float,
    )

    /**
     * Map [frequencyHz] to the nearest note in equal temperament.
     *
     * Returns null for non-positive frequencies.
     *
     * [cents] in the result is the deviation from the nearest note, in the
     * range (-50, +50]. Negative = flat, positive = sharp.
     *
     * [exactFrequencyHz] is the theoretical frequency of the nearest note
     * at the given [a4Hz] reference, useful for showing "target frequency."
     */
    fun map(frequencyHz: Float, a4Hz: Float = 440f): NoteResult? {
        if (frequencyHz <= 0f || a4Hz <= 0f) return null

        // Semitones from A4. MIDI note 69 = A4.
        val semitones = 12.0 * ln((frequencyHz / a4Hz).toDouble()) / LN2
        val midi = 69 + round(semitones).toInt()
        val cents = ((semitones - round(semitones)) * 100.0).toFloat()

        val noteIndex = ((midi % 12) + 12) % 12
        val octave = midi / 12 - 1

        // The exact frequency this note should be at the current A4 reference
        val exactFreq = (a4Hz * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

        return NoteResult(
            name = NOTE_NAMES[noteIndex],
            nameAscii = NOTE_NAMES_ASCII[noteIndex],
            octave = octave,
            midiNumber = midi,
            cents = cents,
            exactFrequencyHz = exactFreq,
        )
    }

    /**
     * Frequency of a given MIDI note number at the specified A4 reference.
     * Useful for generating drone tones or showing target frequencies.
     */
    fun midiToFrequency(midiNumber: Int, a4Hz: Float = 440f): Float {
        return (a4Hz * Math.pow(2.0, (midiNumber - 69) / 12.0)).toFloat()
    }
}
