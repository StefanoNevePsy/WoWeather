package app.sereno.weather.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import app.sereno.weather.design.ThemeMode
import app.sereno.weather.domain.model.PrecipUnit
import app.sereno.weather.domain.model.PressureUnit
import app.sereno.weather.domain.model.SpeedUnit
import app.sereno.weather.domain.model.TemperatureUnit
import app.sereno.weather.i18n.Lang

/** Everything the user can change. Small enough to pass around whole. */
data class SerenoSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val speedUnit: SpeedUnit = SpeedUnit.KmH,
    val precipUnit: PrecipUnit = PrecipUnit.Mm,
    val pressureUnit: PressureUnit = PressureUnit.HPa,
    val language: Lang? = null,
    val reduceMotion: Boolean? = null,
    val rainNotifications: Boolean = false,
    val severeNotifications: Boolean = true,
    val developerMode: Boolean = false,
    /** Overrides the real weather with a synthetic state, for design work. */
    val mockState: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sereno_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val temperature = stringPreferencesKey("unit_temperature")
        val speed = stringPreferencesKey("unit_speed")
        val precip = stringPreferencesKey("unit_precip")
        val pressure = stringPreferencesKey("unit_pressure")
        val language = stringPreferencesKey("language")
        val reduceMotion = stringPreferencesKey("reduce_motion")
        val rainNotifications = booleanPreferencesKey("notify_rain")
        val severeNotifications = booleanPreferencesKey("notify_severe")
        val developerMode = booleanPreferencesKey("developer_mode")
        val mockState = stringPreferencesKey("mock_state")
    }

    val settings: Flow<SerenoSettings> = context.dataStore.data
        // A corrupt preferences file must not stop the app from launching.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            SerenoSettings(
                themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
                temperatureUnit = prefs[Keys.temperature]?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.Celsius,
                speedUnit = prefs[Keys.speed]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: SpeedUnit.KmH,
                precipUnit = prefs[Keys.precip]?.let { runCatching { PrecipUnit.valueOf(it) }.getOrNull() } ?: PrecipUnit.Mm,
                pressureUnit = prefs[Keys.pressure]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() } ?: PressureUnit.HPa,
                language = prefs[Keys.language]?.let { runCatching { Lang.valueOf(it) }.getOrNull() },
                reduceMotion = prefs[Keys.reduceMotion]?.let { it.toBooleanStrictOrNull() },
                rainNotifications = prefs[Keys.rainNotifications] ?: false,
                severeNotifications = prefs[Keys.severeNotifications] ?: true,
                developerMode = prefs[Keys.developerMode] ?: false,
                mockState = prefs[Keys.mockState],
            )
        }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = edit { it[Keys.temperature] = unit.name }
    suspend fun setSpeedUnit(unit: SpeedUnit) = edit { it[Keys.speed] = unit.name }
    suspend fun setPrecipUnit(unit: PrecipUnit) = edit { it[Keys.precip] = unit.name }
    suspend fun setPressureUnit(unit: PressureUnit) = edit { it[Keys.pressure] = unit.name }
    suspend fun setLanguage(lang: Lang?) = edit { prefs ->
        if (lang == null) prefs.remove(Keys.language) else prefs[Keys.language] = lang.name
    }
    suspend fun setReduceMotion(value: Boolean?) = edit { prefs ->
        if (value == null) prefs.remove(Keys.reduceMotion) else prefs[Keys.reduceMotion] = value.toString()
    }
    suspend fun setRainNotifications(value: Boolean) = edit { it[Keys.rainNotifications] = value }
    suspend fun setSevereNotifications(value: Boolean) = edit { it[Keys.severeNotifications] = value }
    suspend fun setDeveloperMode(value: Boolean) = edit { it[Keys.developerMode] = value }
    suspend fun setMockState(value: String?) = edit { prefs ->
        if (value == null) prefs.remove(Keys.mockState) else prefs[Keys.mockState] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
