package geo.truetune.presentation.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geo.truetune.domain.audio.PitchStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM behind the Phase 0 diagnostics screen. Holds two pieces of state:
 * whether the mic permission has been granted (updated by the screen after
 * the runtime prompt), and whether we're currently listening.
 *
 * The pitch reading itself lives in the [PitchStream] and is exposed to the
 * screen through a merged [uiState] flow.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val stream: PitchStream,
) : ViewModel() {

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val reading = stream.reading

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        _permissionGranted, reading
    ) { granted, r ->
        DiagnosticsUiState(
            permissionGranted = granted,
            reading = r,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (!granted) _errorMessage.value = "Mic access is required to detect pitch."
    }

    fun toggleListening() {
        viewModelScope.launch {
            if (reading.value.isRunning) {
                stream.stop()
            } else {
                val ok = stream.start()
                if (!ok) _errorMessage.value = "Couldn't open the mic. Try closing other audio apps."
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        // Fire-and-forget stop; the stream is a singleton so cancelling scope
        // here doesn't kill the underlying native worker, but we do want the
        // reading to reset if the VM is torn down.
        viewModelScope.launch { stream.stop() }
    }
}

data class DiagnosticsUiState(
    val permissionGranted: Boolean = false,
    val reading: geo.truetune.domain.audio.PitchReading = geo.truetune.domain.audio.PitchReading(),
)
