package geo.truetune

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt-annotated Application class. All Hilt injection graphs are rooted here.
 */
@HiltAndroidApp
class TrueTuneApp : Application()
