package geo.truetune.presentation.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import geo.truetune.data.prefs.TunerPreferences
import geo.truetune.domain.model.AccuracyBand
import geo.truetune.domain.model.TunerState
import geo.truetune.domain.music.NoteMapper
import geo.truetune.domain.audio.PitchStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class TunerViewModel @Inject constructor(
    private val stream: PitchStream,
    private val prefs: TunerPreferences,
) : ViewModel() {

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Whether the A4 adjustment sheet is showing. */
    private val _showA4Sheet = MutableStateFlow(false)
    val showA4Sheet: StateFlow<Boolean> = _showA4Sheet.asStateFlow()

    // --- Smoothing state ---
    // EMA (exponential moving average) on cents to stabilize the needle.
    // Alpha near 1.0 = responsive; near 0.0 = sluggish. 0.3 is a good
    // balance: settles in ~5 readings (~170ms) but doesn't dance on noise.
    private var smoothedCents: Float = 0f
    private var lastNoteName: String = ""
    private var lastOctave: Int = 4
    private var noiseFrames: Int = 0

    val tunerState: StateFlow<TunerState> = combine(
        stream.reading,
        prefs.a4Hz,
    ) { reading, a4 ->
        if (!reading.hasPitch) {
            // After a few frames of silence, reset smoothing so the needle
            // doesn't stick at the last position when the player stops.
            noiseFrames++
            if (noiseFrames > SILENCE_RESET_FRAMES) {
                smoothedCents = 0f
                lastNoteName = ""
            }
            TunerState(
                noteName = lastNoteName,
                octave = lastOctave,
                cents = smoothedCents,
                accuracy = AccuracyBand.NONE,
                isListening = reading.isRunning,
                hasPitch = false,
                a4Hz = a4,
            )
        } else {
            noiseFrames = 0
            val note = NoteMapper.map(reading.frequencyHz, a4)!!

            // When the detected note changes, snap the smoother to avoid
            // the needle sweeping across the whole gauge between notes.
            if (note.name != lastNoteName || note.octave != lastOctave) {
                smoothedCents = note.cents
                lastNoteName = note.name
                lastOctave = note.octave
            } else {
                smoothedCents = EMA_ALPHA * note.cents + (1f - EMA_ALPHA) * smoothedCents
            }

            val absCents = abs(smoothedCents)
            val accuracy = when {
                absCents <= IN_TUNE_CENTS -> AccuracyBand.IN_TUNE
                absCents <= NEAR_CENTS    -> AccuracyBand.NEAR
                else                      -> AccuracyBand.OFF
            }

            TunerState(
                noteName = note.name,
                octave = note.octave,
                cents = smoothedCents,
                frequencyHz = reading.frequencyHz,
                targetFrequencyHz = note.exactFrequencyHz,
                accuracy = accuracy,
                isListening = reading.isRunning,
                hasPitch = true,
                a4Hz = a4,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerState())

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (!granted) _errorMessage.value = "Microphone access is needed to tune."
    }

    fun toggleListening() {
        viewModelScope.launch {
            if (stream.reading.value.isRunning) {
                stream.stop()
            } else {
                val ok = stream.start()
                if (!ok) _errorMessage.value = "Couldn't open the mic. Close other audio apps and retry."
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    fun toggleA4Sheet() { _showA4Sheet.value = !_showA4Sheet.value }

    fun setA4(hz: Float) {
        viewModelScope.launch { prefs.setA4Hz(hz) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { stream.stop() }
    }

    companion object {
        private const val EMA_ALPHA = 0.3f
        private const val SILENCE_RESET_FRAMES = 10 // ~330ms of silence before resetting
        private const val IN_TUNE_CENTS = 3f
        private const val NEAR_CENTS = 10f
    }
}
