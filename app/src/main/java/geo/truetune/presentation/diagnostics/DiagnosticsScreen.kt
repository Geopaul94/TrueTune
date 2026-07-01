package geo.truetune.presentation.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geo.truetune.domain.audio.AudioApi
import geo.truetune.domain.audio.NoteNaming
import geo.truetune.presentation.theme.InTuneGreen
import geo.truetune.presentation.theme.NearWarn
import geo.truetune.presentation.theme.OnSurfaceMuted
import kotlin.math.abs

/**
 * Phase 0 diagnostics — the only screen in the app. It exists to prove:
 *  1. we can obtain RECORD_AUDIO at runtime,
 *  2. Oboe opens an input stream on this device,
 *  3. the native MPM detector produces a stable, plausible frequency for a
 *     played note, and reports "no pitch" during silence.
 *
 * There is intentionally no needle / gauge here — that's the Phase 1 tuner
 * screen. This is a purely-textual live readout so we can see the raw numbers.
 */
@Composable
fun DiagnosticsScreen(
    vm: DiagnosticsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()

    // Sync the VM's permission state with the current OS state on first
    // composition and whenever we come back from the system permission prompt.
    val initiallyGranted = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { vm.onPermissionResult(initiallyGranted) }

    val requestMic = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> vm.onPermissionResult(granted) }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TrueTune · Phase 0 diagnostics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Live mic → MPM pitch detector",
                fontSize = 12.sp,
                color = OnSurfaceMuted,
            )

            Spacer(Modifier.height(24.dp))

            NoteReadout(uiState.reading)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    when {
                        !uiState.permissionGranted -> requestMic.launch(Manifest.permission.RECORD_AUDIO)
                        else -> vm.toggleListening()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    !uiState.permissionGranted -> "Grant microphone access"
                    uiState.reading.isRunning  -> "Stop listening"
                    else                       -> "Start listening"
                }
                Text(label)
            }

            Spacer(Modifier.height(16.dp))

            DiagnosticsCard(uiState)

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NoteReadout(reading: geo.truetune.domain.audio.PitchReading) {
    // The big readout: nearest note + octave, cents deviation, Hz. Green when
    // we're within ±5 cents (the "in tune" band); amber outside; muted when
    // there's no confident pitch.
    val hasPitch = reading.hasPitch
    val naming = if (hasPitch) NoteNaming.name(reading.frequencyHz) else null
    val cents = naming?.third ?: 0f

    val readoutColor: Color = when {
        !hasPitch                -> OnSurfaceMuted
        abs(cents) <= 5f         -> InTuneGreen
        abs(cents) <= 20f        -> NearWarn
        else                     -> MaterialTheme.colorScheme.primary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = naming?.let { "${it.first}${it.second}" } ?: "—",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = readoutColor,
        )
        Text(
            text = if (hasPitch) "%.2f Hz".format(reading.frequencyHz) else "waiting for input…",
            fontSize = 16.sp,
            color = OnSurfaceMuted,
        )
        if (hasPitch) {
            val sign = if (cents >= 0f) "+" else ""
            Text(
                text = "$sign%.1f ¢".format(cents),
                fontSize = 20.sp,
                color = readoutColor,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(state: DiagnosticsUiState) {
    val r = state.reading
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row("Running",       if (r.isRunning) "yes" else "no")
            Row("Frequency",     if (r.hasPitch) "%.3f Hz".format(r.frequencyHz) else "—")
            Row("Clarity",       "%.3f".format(r.clarity))
            Row("Input level",   "%.1f dBFS".format(r.rmsDb))
            Row("Sample rate",   if (r.sampleRate > 0) "${r.sampleRate} Hz" else "—")
            Row("Frames/burst",  r.framesPerBurst.toString())
            Row("Buffer size",   r.bufferSize.toString())
            Row("Xruns",         r.xruns.toString())
            Row("Audio API",     when (r.api) {
                AudioApi.AAUDIO      -> "AAudio (preferred)"
                AudioApi.OPENSL_ES   -> "OpenSL ES (fallback)"
                AudioApi.UNSPECIFIED -> "—"
            })
            Row("Low-latency path", if (r.lowLatencyPath) "yes" else "no")
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
