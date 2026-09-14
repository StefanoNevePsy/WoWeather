package app.sereno.weather

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sereno.weather.core.Container
import app.sereno.weather.design.AtmosphericBackdrop
import app.sereno.weather.design.SerenoTheme
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.i18n.Lang
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.Overlay
import app.sereno.weather.ui.OverlayHost
import app.sereno.weather.ui.SerenoRail
import app.sereno.weather.ui.SerenoViewModel
import app.sereno.weather.ui.Tab
import app.sereno.weather.ui.TabHost
import app.sereno.weather.ui.days.DaysScreen
import app.sereno.weather.ui.daydetail.DayDetailScreen
import app.sereno.weather.ui.debug.DebugScreen
import app.sereno.weather.ui.lab.LabScreen
import app.sereno.weather.ui.map.MapScreen
import app.sereno.weather.ui.places.PlacesScreen
import app.sereno.weather.ui.rememberNavigator
import app.sereno.weather.ui.settings.SettingsScreen
import app.sereno.weather.ui.today.TodayScreen
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = (application as SerenoApplication).container
        setContent { SerenoApp(container) }
    }
}

/**
 * The app shell.
 *
 * One backdrop behind everything, tabs that cross-fade over it, and overlays
 * that rise on top. The backdrop lives here rather than inside each screen so
 * that switching tabs does not rebuild the sky — the atmosphere is continuous,
 * which is a large part of why the app feels like one place rather than five.
 */
@Composable
fun SerenoApp(container: Container) {
    // Read through LocalConfiguration rather than Context.resources: the
    // Compose local recomposes on a locale change, the Context field does not.
    val configuration = LocalConfiguration.current
    val deviceLanguage = remember(configuration) {
        Lang.fromTag(configuration.locales[0]?.language ?: "en")
    }

    val viewModel: SerenoViewModel = viewModel(
        factory = SerenoViewModel.factory(container, deviceLanguage),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberNavigator()

    // A single clock for the whole UI. Ticking once a minute keeps "updated 3
    // minutes ago" and the now-marker honest without recomposing constantly.
    var nowEpoch by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowEpoch = System.currentTimeMillis() / 1000
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.resolveLocation() }

    SerenoTheme(
        mood = state.mood,
        themeMode = state.settings.themeMode,
        reduceMotionOverride = state.settings.reduceMotion,
    ) {
        val atmosphere = Sereno.atmosphere
        val view = LocalView.current

        // System bar icons have to invert with the atmosphere, not with the
        // system's light/dark setting: a clear night is dark even in light mode.
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !atmosphere.dark
                isAppearanceLightNavigationBars = !atmosphere.dark
            }
        }

        val formatter = remember(state.settings, state.copy, state.bundle?.utcOffsetSeconds) {
            Formatter(
                settings = state.settings,
                copy = state.copy,
                utcOffsetSeconds = state.bundle?.utcOffsetSeconds ?: 0,
            )
        }

        BackHandler(enabled = navigator.overlays.isNotEmpty()) { navigator.pop() }

        val todayScroll = rememberScrollState()
        val daysScroll = rememberScrollState()
        val labScroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {

            AtmosphericBackdrop(
                modifier = Modifier.fillMaxSize(),
                parallax = (todayScroll.value / 1400f).coerceIn(0f, 1f),
            )

            TabHost(navigator.tab) { tab ->
                when (tab) {
                    Tab.Today -> TodayScreen(
                        state = state,
                        formatter = formatter,
                        nowEpoch = nowEpoch,
                        scrollState = todayScroll,
                        onOpenPlaces = { navigator.push(Overlay.Places) },
                        onOpenSettings = { navigator.push(Overlay.Settings) },
                        onOpenDay = { navigator.push(Overlay.DayDetail(it)) },
                    )

                    Tab.Days -> DaysScreen(
                        state = state,
                        formatter = formatter,
                        nowEpoch = nowEpoch,
                        scrollState = daysScroll,
                        onOpenDay = { navigator.push(Overlay.DayDetail(it)) },
                    )

                    Tab.Models -> LabScreen(
                        state = state,
                        formatter = formatter,
                        nowEpoch = nowEpoch,
                        scrollState = labScroll,
                    )

                    Tab.Map -> MapScreen(
                        state = state,
                        formatter = formatter,
                        http = container.http,
                        nowEpoch = nowEpoch,
                    )
                }
            }

            SerenoRail(
                selected = navigator.tab,
                copy = state.copy,
                onSelect = { navigator.select(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Space.lg, start = Space.pageMargin, end = Space.pageMargin),
            )

            OverlayHost(navigator.current) { overlay ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(atmosphere.skyMid),
                ) {
                    AtmosphericBackdrop(Modifier.fillMaxSize())
                    when (overlay) {
                        is Overlay.DayDetail -> DayDetailScreen(
                            bundle = state.bundle,
                            dayEpoch = overlay.dayEpoch,
                            formatter = formatter,
                            copy = state.copy,
                            nowEpoch = nowEpoch,
                            onClose = { navigator.pop() },
                        )

                        Overlay.Places -> PlacesScreen(
                            state = state,
                            formatter = formatter,
                            onClose = { navigator.pop() },
                            onSearch = viewModel::search,
                            onSelect = { viewModel.selectPlace(it); navigator.pop() },
                            onAdd = { viewModel.addPlace(it); navigator.pop() },
                            onRemove = viewModel::removePlace,
                            onRequestLocation = {
                                locationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            },
                        )

                        Overlay.Settings -> SettingsScreen(
                            state = state,
                            onClose = { navigator.pop() },
                            onTheme = viewModel::setTheme,
                            onTemperatureUnit = viewModel::setTemperatureUnit,
                            onSpeedUnit = viewModel::setSpeedUnit,
                            onPrecipUnit = viewModel::setPrecipUnit,
                            onPressureUnit = viewModel::setPressureUnit,
                            onLanguage = viewModel::setLanguage,
                            onReduceMotion = viewModel::setReduceMotion,
                            onRainNotifications = viewModel::setRainNotifications,
                            onSevereNotifications = viewModel::setSevereNotifications,
                            onDeveloperMode = viewModel::setDeveloperMode,
                            onOpenDebug = { navigator.push(Overlay.Debug) },
                        )

                        Overlay.Debug -> DebugScreen(
                            state = state,
                            formatter = formatter,
                            nowEpoch = nowEpoch,
                            onClose = { navigator.pop() },
                            onMockState = viewModel::setMockState,
                            onClearCache = viewModel::clearCache,
                            onRefresh = viewModel::refresh,
                            cacheSize = { container.repository.cacheSizeBytes() },
                        )
                    }
                }
            }
        }
    }
}
