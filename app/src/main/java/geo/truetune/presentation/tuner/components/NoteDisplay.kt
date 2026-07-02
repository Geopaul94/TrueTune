package geo.truetune.presentation.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geo.truetune.domain.model.AccuracyBand
import geo.truetune.domain.model.TunerState
import geo.truetune.presentation.theme.InTuneGreen
import geo.truetune.presentation.theme.NearWarn
import geo.truetune.presentation.theme.OnSurfaceMuted
import geo.truetune.presentation.theme.PrimaryDark
import kotlin.math.abs

/**
 * Big note name + octave in the center of the gauge, plus Hz and cents readout
 * below. Color-coded by accuracy band.
 */
@Composable
fun NoteDisplay(
    state: TunerState,
    modifier: Modifier = Modifier,
) {
    val noteColor by animateColorAsState(
        targetValue = when (state.accuracy) {
            AccuracyBand.IN_TUNE -> InTuneGreen
            AccuracyBand.NEAR    -> NearWarn
            AccuracyBand.OFF     -> PrimaryDark
            AccuracyBand.NONE    -> OnSurfaceMuted
        },
        animationSpec = tween(200),
        label = "noteColor",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Note name + octave
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.hasPitch) state.noteName else "—",
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = noteColor,
            )
            if (state.hasPitch) {
                Text(
                    text = state.octave.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = noteColor.copy(alpha = 0.7f),
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Frequency readout
        Text(
            text = if (state.hasPitch) "%.1f Hz".format(state.frequencyHz) else "listening…",
            fontSize = 16.sp,
            color = OnSurfaceMuted,
        )

        Spacer(Modifier.height(2.dp))

        // Cents readout
        if (state.hasPitch) {
            val sign = if (state.cents >= 0f) "+" else ""
            val centsText = "$sign%.1f ¢".format(state.cents)
            Text(
                text = centsText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = noteColor,
            )
        }
    }
}

/**
 * Flat/Sharp indicators flanking the gauge — large arrows that fade in
 * based on how far off-pitch the reading is.
 */
@Composable
fun FlatSharpIndicator(
    cents: Float,
    hasPitch: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasPitch) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Flat indicator (left)
        val flatAlpha = if (cents < -3f) {
            (abs(cents) / 50f).coerceIn(0.2f, 1f)
        } else 0f

        Text(
            text = "♭",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark.copy(alpha = flatAlpha),
        )

        Spacer(Modifier.width(120.dp))

        // Sharp indicator (right)
        val sharpAlpha = if (cents > 3f) {
            (abs(cents) / 50f).coerceIn(0.2f, 1f)
        } else 0f

        Text(
            text = "♯",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryDark.copy(alpha = sharpAlpha),
        )
    }
}
