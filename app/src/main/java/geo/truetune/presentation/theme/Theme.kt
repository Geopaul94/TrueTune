package geo.truetune.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = OnSurface,
    onSurface = OnSurface,
    error = ErrorRed,
)

// Phase 0 stays dark-only. Light theme lands in Phase 1 alongside the real
// tuner UI where color-contrast for the needle matters more.
private val LightColors = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    error = ErrorRed,
)

@Composable
fun TrueTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
