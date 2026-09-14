package app.sereno.weather

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.sereno.weather.data.ForecastResource
import app.sereno.weather.data.net.Http
import app.sereno.weather.data.prefs.SerenoSettings
import app.sereno.weather.design.AtmosphericBackdrop
import app.sereno.weather.design.Mood
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.SerenoTheme
import app.sereno.weather.design.ThemeMode
import app.sereno.weather.domain.mock.MockWeather
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.days.DaysScreen
import app.sereno.weather.ui.debug.DebugScreen
import app.sereno.weather.ui.map.MapScreen
import app.sereno.weather.ui.daydetail.DayDetailScreen
import app.sereno.weather.ui.lab.LabScreen
import app.sereno.weather.ui.places.PlacesScreen
import app.sereno.weather.ui.settings.SettingsScreen
import app.sereno.weather.ui.today.TodayScreen
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to a PNG so the design can actually be looked at.
 *
 * This is not an assertion suite — nothing fails on a pixel diff. It exists
 * because judging spacing, hierarchy, contrast and typography requires *seeing*
 * the result, and Robolectric's native graphics mode renders real Compose
 * output on the JVM without needing a device.
 *
 * Two mechanics matter here. `setContent` may only be called once per rule, so
 * the screen under test is swapped through a piece of state instead. And the
 * clock is advanced manually, because a design with ambient animation in it
 * never becomes "idle" and `waitForIdle` would simply time out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/screenshots").apply { mkdirs() }
    private val nowEpoch = 1_757_840_400L

    private data class Shot(
        val name: String,
        val mood: Mood,
        val theme: ThemeMode,
        val scrollTo: Int = 0,
        val content: @Composable () -> Unit,
    )

    private fun stateFor(
        mock: MockWeather.State,
        language: Lang = Lang.It,
        theme: ThemeMode = ThemeMode.System,
    ): AppState {
        val copy = Copy(language)
        val bundle = MockWeather.bundle(mock, MockWeather.correggio, nowEpoch, copy)
        return AppState(
            settings = SerenoSettings(themeMode = theme, language = language, reduceMotion = true),
            copy = copy,
            places = listOf(MockWeather.correggio),
            selectedPlaceId = MockWeather.correggio.id,
            forecast = ForecastResource.Data(bundle, refreshing = false),
        )
    }

    private fun AppState.formatter() = Formatter(settings, copy, bundle?.utcOffsetSeconds ?: 7200)

    private fun run(shots: List<Shot>) {
        var index by mutableIntStateOf(0)

        compose.setContent {
            val shot = shots[index]
            SerenoTheme(mood = shot.mood, themeMode = shot.theme, reduceMotionOverride = true) {
                Box(Modifier.fillMaxSize().background(Sereno.atmosphere.skyMid)) {
                    AtmosphericBackdrop(Modifier.fillMaxSize())
                    shot.content()
                }
            }
        }

        shots.forEachIndexed { i, shot ->
            index = i
            // The clock is left auto-advancing: capture needs a real draw pass
            // to complete, which a paused clock never delivers. Ambient motion
            // is off (reduceMotionOverride), so composition still reaches idle.
            compose.waitForIdle()
            val image = captureDecorView()
            File(outputDir, "${shot.name}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            println("wrote ${shot.name}.png (${image.width}x${image.height})")
        }
    }

    /**
     * Draws the window straight into a bitmap.
     *
     * Compose's own `captureToImage` waits on a real draw callback that never
     * arrives under Robolectric, so the view hierarchy is rendered directly —
     * which with native graphics mode produces genuine Skia output.
     */
    private fun captureDecorView(): Bitmap {
        val view: View = compose.activity.window.decorView
        val width = view.width.takeIf { it > 0 } ?: 1080
        val height = view.height.takeIf { it > 0 } ?: 2340
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    @Composable
    private fun today(state: AppState, scrollTo: Int = 0) {
        val scroll = rememberScrollState()
        TodayScreen(
            state = state,
            formatter = state.formatter(),
            nowEpoch = nowEpoch,
            scrollState = scroll,
            onOpenPlaces = {}, onOpenSettings = {}, onOpenDay = {},
        )
        if (scrollTo > 0) LaunchedEffect(Unit) { scroll.scrollTo(scrollTo) }
    }

    @Test
    fun renderEveryScreen() {
        val rain = stateFor(MockWeather.State.Rain)
        val clear = stateFor(MockWeather.State.ClearDay)
        val storm = stateFor(MockWeather.State.Storm)
        val night = stateFor(MockWeather.State.ClearNight)
        val snow = stateFor(MockWeather.State.Snow)
        val fog = stateFor(MockWeather.State.Fog)
        val partly = stateFor(MockWeather.State.PartlyCloudy)
        val english = stateFor(MockWeather.State.Rain, language = Lang.En)
        val dark = stateFor(MockWeather.State.ClearDay, theme = ThemeMode.Dark)

        run(
            listOf(
                Shot("today-rain", Mood.Rain, ThemeMode.System) { today(rain) },
                Shot("today-clear", Mood.ClearDay, ThemeMode.System) { today(clear) },
                Shot("today-storm", Mood.Thunder, ThemeMode.System) { today(storm) },
                Shot("today-night", Mood.ClearNight, ThemeMode.System) { today(night) },
                Shot("today-snow", Mood.Snow, ThemeMode.System) { today(snow) },
                Shot("today-fog", Mood.Fog, ThemeMode.System) { today(fog) },
                Shot("today-dark", Mood.ClearDay, ThemeMode.Dark) { today(dark) },
                Shot("today-english", Mood.Rain, ThemeMode.Light) { today(english) },
                Shot("today-scroll-1", Mood.Rain, ThemeMode.System) { today(rain, scrollTo = 900) },
                Shot("today-scroll-2", Mood.Rain, ThemeMode.System) { today(rain, scrollTo = 1900) },
                Shot("today-scroll-3", Mood.Rain, ThemeMode.System) { today(rain, scrollTo = 2900) },
                Shot("days", Mood.PartlyDay, ThemeMode.System) {
                    DaysScreen(partly, partly.formatter(), nowEpoch, rememberScrollState(), {})
                },
                Shot("lab", Mood.PartlyDay, ThemeMode.System) {
                    LabScreen(partly, partly.formatter(), nowEpoch, rememberScrollState())
                },
                Shot("lab-scrolled", Mood.PartlyDay, ThemeMode.System) {
                    val scroll = rememberScrollState()
                    LabScreen(partly, partly.formatter(), nowEpoch, scroll)
                    LaunchedEffect(Unit) { scroll.scrollTo(900) }
                },
                Shot("day-detail", Mood.PartlyDay, ThemeMode.System) {
                    DayDetailScreen(
                        bundle = partly.bundle,
                        dayEpoch = partly.bundle!!.days[1].epochSeconds,
                        formatter = partly.formatter(),
                        copy = partly.copy,
                        nowEpoch = nowEpoch,
                        onClose = {},
                    )
                },
                Shot("places", Mood.PartlyDay, ThemeMode.System) {
                    PlacesScreen(partly, partly.formatter(), {}, {}, {}, {}, {}, {})
                },
                Shot("settings", Mood.PartlyDay, ThemeMode.System) {
                    SettingsScreen(
                        state = partly,
                        onClose = {}, onTheme = {}, onTemperatureUnit = {}, onSpeedUnit = {},
                        onPrecipUnit = {}, onPressureUnit = {}, onLanguage = {},
                        onReduceMotion = {}, onRainNotifications = {},
                        onSevereNotifications = {}, onChartAxes = {},
                        onDeveloperMode = {}, onOpenDebug = {},
                    )
                },
                // The map and the debug screen round out the set: neither is
                // reachable from the others, and both would otherwise ship
                // never having been composed once.
                Shot("map", Mood.PartlyDay, ThemeMode.System) {
                    MapScreen(
                        state = partly,
                        formatter = partly.formatter(),
                        http = Http(File("build/tmp/test-http-cache").apply { mkdirs() }),
                        nowEpoch = nowEpoch,
                    )
                },
                Shot("debug", Mood.PartlyDay, ThemeMode.System) {
                    DebugScreen(
                        state = partly,
                        formatter = partly.formatter(),
                        nowEpoch = nowEpoch,
                        onClose = {}, onMockState = {}, onClearCache = {}, onRefresh = {},
                        cacheSize = { 0L },
                    )
                },
                Shot("loading", Mood.PartlyDay, ThemeMode.System) {
                    val empty = partly.copy(forecast = ForecastResource.Loading)
                    today(empty)
                },
            ),
        )
    }
}
