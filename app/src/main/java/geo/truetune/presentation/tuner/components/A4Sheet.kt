package geo.truetune.presentation.tuner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geo.truetune.data.prefs.TunerPreferences
import geo.truetune.presentation.theme.OnSurfaceMuted

/**
 * Bottom sheet for adjusting the A4 reference pitch. Shows a slider
 * (415–466 Hz) with the current value and a reset-to-440 button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A4Sheet(
    currentA4: Float,
    onA4Changed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var sliderValue by remember(currentA4) { mutableFloatStateOf(currentA4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Reference Pitch",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Standard concert pitch is A4 = 440 Hz",
                fontSize = 13.sp,
                color = OnSurfaceMuted,
            )

            Spacer(Modifier.height(24.dp))

            // Big Hz readout
            Text(
                text = "A4 = %.1f Hz".format(sliderValue),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onA4Changed(sliderValue) },
                valueRange = TunerPreferences.MIN_A4..TunerPreferences.MAX_A4,
                steps = 0, // continuous
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${TunerPreferences.MIN_A4.toInt()} Hz", fontSize = 12.sp, color = OnSurfaceMuted)
                Text("${TunerPreferences.MAX_A4.toInt()} Hz", fontSize = 12.sp, color = OnSurfaceMuted)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = {
                    sliderValue = TunerPreferences.DEFAULT_A4
                    onA4Changed(TunerPreferences.DEFAULT_A4)
                }) {
                    Text("Reset to 440")
                }
                FilledTonalButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    }
}
