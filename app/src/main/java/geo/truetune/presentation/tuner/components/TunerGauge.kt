package geo.truetune.presentation.tuner.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import geo.truetune.domain.model.AccuracyBand
import geo.truetune.presentation.theme.InTuneGreen
import geo.truetune.presentation.theme.NearWarn
import geo.truetune.presentation.theme.OnSurfaceMuted
import geo.truetune.presentation.theme.PrimaryDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ARC_START_DEG = 150f   // 0° = 3-o'clock; 150° = bottom-left
private const val ARC_SWEEP_DEG = 240f   // total arc span
private const val CENTS_RANGE = 50f      // ±50 cents maps to full sweep

/**
 * The tuner gauge: a 240° arc with tick marks and a smooth needle that
 * points to the current cents deviation. The center zone glows green
 * when in tune; the needle color reflects the accuracy band.
 *
 * The needle uses a spring animation so it feels physical — it overshoots
 * slightly and settles, which reads as "precise instrument" rather than
 * "jumpy digital readout."
 */
@Composable
fun TunerGauge(
    cents: Float,
    accuracy: AccuracyBand,
    hasPitch: Boolean,
    modifier: Modifier = Modifier,
) {
    // Map cents (-50..+50) to a 0..1 fraction of the arc
    val targetFraction = if (hasPitch) {
        ((cents.coerceIn(-CENTS_RANGE, CENTS_RANGE) + CENTS_RANGE) / (2f * CENTS_RANGE))
    } else {
        0.5f // center when idle
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "needle",
    )

    val needleColor by animateColorAsState(
        targetValue = when (accuracy) {
            AccuracyBand.IN_TUNE -> InTuneGreen
            AccuracyBand.NEAR    -> NearWarn
            AccuracyBand.OFF     -> PrimaryDark
            AccuracyBand.NONE    -> OnSurfaceMuted
        },
        animationSpec = tween(200),
        label = "needleColor",
    )

    // Glow intensity for the in-tune zone: 1.0 when in tune, 0.0 otherwise
    val glowAlpha by animateFloatAsState(
        targetValue = if (accuracy == AccuracyBand.IN_TUNE) 1f else 0f,
        animationSpec = tween(300),
        label = "glow",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f) // slightly wider than tall
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.55f // push center down so arc sits nicely
        val radius = size.width * 0.42f

        drawArcTrack(cx, cy, radius)
        drawTickMarks(cx, cy, radius)
        drawInTuneZone(cx, cy, radius, glowAlpha)
        drawNeedle(cx, cy, radius, animatedFraction, needleColor)
        drawCenterDot(cx, cy, needleColor)
    }
}

private fun DrawScope.drawArcTrack(cx: Float, cy: Float, radius: Float) {
    val trackWidth = 6.dp.toPx()
    drawArc(
        color = OnSurfaceMuted.copy(alpha = 0.2f),
        startAngle = ARC_START_DEG,
        sweepAngle = ARC_SWEEP_DEG,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = trackWidth, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawTickMarks(cx: Float, cy: Float, radius: Float) {
    // 21 tick marks: one every 5 cents from -50 to +50
    val tickCount = 21
    val smallLen = 8.dp.toPx()
    val bigLen = 14.dp.toPx()
    val tickWidth = 1.5f.dp.toPx()

    for (i in 0 until tickCount) {
        val fraction = i.toFloat() / (tickCount - 1)
        val angleDeg = ARC_START_DEG + fraction * ARC_SWEEP_DEG
        val angleRad = angleDeg * PI.toFloat() / 180f

        val isBigTick = i % 5 == 0 // every 25 cents + center
        val len = if (isBigTick) bigLen else smallLen
        val alpha = if (isBigTick) 0.5f else 0.25f

        val outerX = cx + (radius + 4.dp.toPx()) * cos(angleRad)
        val outerY = cy + (radius + 4.dp.toPx()) * sin(angleRad)
        val innerX = cx + (radius + 4.dp.toPx() + len) * cos(angleRad)
        val innerY = cy + (radius + 4.dp.toPx() + len) * sin(angleRad)

        drawLine(
            color = OnSurfaceMuted.copy(alpha = alpha),
            start = Offset(outerX, outerY),
            end = Offset(innerX, innerY),
            strokeWidth = tickWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A subtle glowing band around the center of the arc (the ±3-cent zone)
 * that brightens when the pitch is in tune.
 */
private fun DrawScope.drawInTuneZone(
    cx: Float, cy: Float, radius: Float, alpha: Float,
) {
    if (alpha <= 0.01f) return

    val zoneCents = 3f
    val zoneFractionStart = (CENTS_RANGE - zoneCents) / (2f * CENTS_RANGE)
    val zoneFractionEnd = (CENTS_RANGE + zoneCents) / (2f * CENTS_RANGE)

    val startAngle = ARC_START_DEG + zoneFractionStart * ARC_SWEEP_DEG
    val sweepAngle = (zoneFractionEnd - zoneFractionStart) * ARC_SWEEP_DEG

    val glowWidth = 12.dp.toPx()
    drawArc(
        color = InTuneGreen.copy(alpha = alpha * 0.5f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = glowWidth, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawNeedle(
    cx: Float, cy: Float, radius: Float,
    fraction: Float, color: Color,
) {
    val angleDeg = ARC_START_DEG + fraction * ARC_SWEEP_DEG
    val angleRad = angleDeg * PI.toFloat() / 180f

    val needleLen = radius * 0.85f
    val tipX = cx + needleLen * cos(angleRad)
    val tipY = cy + needleLen * sin(angleRad)

    // Thin near tip, thicker at base — drawn as a single line with round cap
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(tipX, tipY),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawCenterDot(cx: Float, cy: Float, color: Color) {
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = Offset(cx, cy),
    )
    // Inner dot for depth
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = 3.dp.toPx(),
        center = Offset(cx, cy),
    )
}
