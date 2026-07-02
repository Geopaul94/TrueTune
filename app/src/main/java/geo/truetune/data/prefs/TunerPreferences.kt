package geo.truetune.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tuner_prefs")

@Singleton
class TunerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY_A4 = floatPreferencesKey("a4_hz")

    val a4Hz: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_A4] ?: DEFAULT_A4
    }

    suspend fun setA4Hz(hz: Float) {
        val clamped = hz.coerceIn(MIN_A4, MAX_A4)
        context.dataStore.edit { prefs -> prefs[KEY_A4] = clamped }
    }

    companion object {
        const val DEFAULT_A4 = 440f
        const val MIN_A4 = 415f
        const val MAX_A4 = 466f
    }
}
