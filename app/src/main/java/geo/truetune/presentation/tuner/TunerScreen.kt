package geo.truetune.presentation.tuner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import geo.truetune.domain.model.AccuracyBand
import geo.truetune.presentation.theme.InTuneGreen
import geo.truetune.presentation.theme.OnSurfaceMuted
import geo.truetune.presentation.tuner.components.A4Sheet
import geo.truetune.presentation.tuner.components.FlatSharpIndicator
import geo.truetune.presentation.tuner.components.NoteDisplay
import geo.truetune.presentation.tuner.components.TunerGauge

@Composable
fun TunerScreen(
    vm: TunerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by vm.tunerState.collectAsStateWithLifecycle()
    val permGranted by vm.permissionGranted.collectAsStateWithLifecycle()
    val error by vm.errorMessage.collectAsStateWithLifecycle()
    val showA4 by vm.showA4Sheet.collectAsStateWithLifecycle()

    // Check existing permission on first composition
    val initiallyGranted = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { vm.onPermissionResult(initiallyGranted) }

    val requestMic = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> vm.onPermissionResult(granted) },
    )

    // Haptic pulse when entering in-tune band
    var wasInTune by remember { mutableStateOf(false) }
    LaunchedEffect(state.accuracy) {
        if (state.accuracy == AccuracyBand.IN_TUNE && !wasInTune) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasInTune = state.accuracy == AccuracyBand.IN_TUNE
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: A4 reference button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    modifier = Modifier.clickable { vm.toggleA4Sheet() },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "A4 = %.0f Hz".format(state.a4Hz),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        color = OnSurfaceMuted,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // The gauge — the hero visual
            Box(contentAlignment = Alignment.Center) {
                TunerGauge(
                    cents = state.cents,
                    accuracy = state.accuracy,
                    hasPitch = state.hasPitch,
                )

                // Note display sits inside the gauge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    NoteDisplay(state = state)
                }
            }

            // Flat/Sharp indicators
            FlatSharpIndicator(
                cents = state.cents,
                hasPitch = state.hasPitch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )

            Spacer(Modifier.weight(1f))

            // In-tune celebration text
            if (state.accuracy == AccuracyBand.IN_TUNE) {
                Text(
                    text = "In Tune",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InTuneGreen,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Start/stop button
            Button(
                onClick = {
                    when {
                        !permGranted -> requestMic.launch(Manifest.permission.RECORD_AUDIO)
                        else -> vm.toggleListening()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = if (state.isListening) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                val label = when {
                    !permGranted       -> "Grant Microphone Access"
                    state.isListening  -> "Stop"
                    else               -> "Start Tuning"
                }
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            // Error message
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // A4 adjustment sheet
    if (showA4) {
        A4Sheet(
            currentA4 = state.a4Hz,
            onA4Changed = { vm.setA4(it) },
            onDismiss = { vm.toggleA4Sheet() },
        )
    }
}
