package geo.truetune.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class NoteMapperTest {

    @Test
    fun `A4 at 440 Hz maps to A4 with zero cents`() {
        val result = NoteMapper.map(440f)!!
        assertEquals("A", result.nameAscii)
        assertEquals(4, result.octave)
        assertEquals(69, result.midiNumber)
        assertEquals(0f, result.cents, 0.1f)
        assertEquals(440f, result.exactFrequencyHz, 0.01f)
    }

    @Test
    fun `C4 (middle C) at 261_63 Hz`() {
        val result = NoteMapper.map(261.63f)!!
        assertEquals("C", result.nameAscii)
        assertEquals(4, result.octave)
        assertEquals(60, result.midiNumber)
        assert(abs(result.cents) < 1f) // should be very close to 0
    }

    @Test
    fun `E2 (guitar low E) at 82_41 Hz`() {
        val result = NoteMapper.map(82.41f)!!
        assertEquals("E", result.nameAscii)
        assertEquals(2, result.octave)
        assertEquals(40, result.midiNumber)
        assert(abs(result.cents) < 1f)
    }

    @Test
    fun `B0 (bass low B) at 30_87 Hz`() {
        val result = NoteMapper.map(30.87f)!!
        assertEquals("B", result.nameAscii)
        assertEquals(0, result.octave)
        assert(abs(result.cents) < 1f)
    }

    @Test
    fun `sharp note shows positive cents`() {
        // 445 Hz is above A4 = 440 Hz
        val result = NoteMapper.map(445f)!!
        assertEquals("A", result.nameAscii)
        assertEquals(4, result.octave)
        assert(result.cents > 0f) { "Expected positive cents for sharp pitch" }
    }

    @Test
    fun `flat note shows negative cents`() {
        // 435 Hz is below A4 = 440 Hz
        val result = NoteMapper.map(435f)!!
        assertEquals("A", result.nameAscii)
        assertEquals(4, result.octave)
        assert(result.cents < 0f) { "Expected negative cents for flat pitch" }
    }

    @Test
    fun `custom A4 reference at 432 Hz`() {
        val result = NoteMapper.map(432f, a4Hz = 432f)!!
        assertEquals("A", result.nameAscii)
        assertEquals(4, result.octave)
        assertEquals(0f, result.cents, 0.1f)
    }

    @Test
    fun `changing A4 shifts all notes`() {
        // At A4=440, 440 Hz is A4 with 0 cents
        val standard = NoteMapper.map(440f, a4Hz = 440f)!!
        assertEquals(0f, standard.cents, 0.1f)

        // At A4=442, 440 Hz is slightly flat of A4
        val concert = NoteMapper.map(440f, a4Hz = 442f)!!
        assertEquals("A", concert.nameAscii)
        assert(concert.cents < 0f) { "Should be flat when reference is higher" }
    }

    @Test
    fun `cents stay within minus50 to plus50 range`() {
        // Test a frequency exactly between two semitones
        // A4 = 440, A#4 = 466.16, midpoint ≈ 452.89
        val result = NoteMapper.map(452.89f)!!
        assert(result.cents >= -50f && result.cents <= 50f) {
            "Cents ${result.cents} outside [-50, 50]"
        }
    }

    @Test
    fun `negative frequency returns null`() {
        assertNull(NoteMapper.map(-100f))
    }

    @Test
    fun `zero frequency returns null`() {
        assertNull(NoteMapper.map(0f))
    }

    @Test
    fun `midiToFrequency round-trips with map`() {
        for (midi in 21..108) { // piano range A0 to C8
            val freq = NoteMapper.midiToFrequency(midi)
            val result = NoteMapper.map(freq)!!
            assertEquals(midi, result.midiNumber)
            assertEquals(0f, result.cents, 0.01f)
        }
    }

    @Test
    fun `all 12 note names appear in one octave`() {
        val expected = setOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val actual = (60..71).map { midi ->
            NoteMapper.map(NoteMapper.midiToFrequency(midi))!!.nameAscii
        }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `exactFrequencyHz matches midiToFrequency`() {
        val result = NoteMapper.map(445f)!!
        val expected = NoteMapper.midiToFrequency(result.midiNumber)
        assertEquals(expected, result.exactFrequencyHz, 0.01f)
    }
}
