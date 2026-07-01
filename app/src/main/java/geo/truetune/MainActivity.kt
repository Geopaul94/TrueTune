package geo.truetune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import geo.truetune.presentation.diagnostics.DiagnosticsScreen
import geo.truetune.presentation.theme.TrueTuneTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 0 entry point. There is only one screen — the diagnostics screen.
 *
 * The real tuner UI arrives in Phase 1. This screen exists so we can prove
 * end-to-end that the mic → native detector → StateFlow → Compose pipeline
 * works before we build anything pretty on top of it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrueTuneTheme {
                DiagnosticsScreen()
            }
        }
    }
}
