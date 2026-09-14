package app.sereno.weather.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.design.ThemeMode
import app.sereno.weather.design.Touch
import app.sereno.weather.domain.model.PrecipUnit
import app.sereno.weather.domain.model.PressureUnit
import app.sereno.weather.domain.model.SpeedUnit
import app.sereno.weather.domain.model.TemperatureUnit
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.components.IconAction
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule
import app.sereno.weather.ui.components.SegmentedControl
import app.sereno.weather.ui.components.ToggleRow

/**
 * Settings.
 *
 * Grouped by what the user is trying to change rather than by which subsystem
 * owns it, and built from the same segmented controls and checks as the rest of
 * the app. Developer mode is reached the traditional way — tapping the version
 * five times — which keeps a screen full of diagnostics out of an ordinary
 * person's way without hiding it from the person who needs it.
 */
@Composable
fun SettingsScreen(
    state: AppState,
    onClose: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onTemperatureUnit: (TemperatureUnit) -> Unit,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onPrecipUnit: (PrecipUnit) -> Unit,
    onPressureUnit: (PressureUnit) -> Unit,
    onLanguage: (Lang?) -> Unit,
    onReduceMotion: (Boolean?) -> Unit,
    onRainNotifications: (Boolean) -> Unit,
    onSevereNotifications: (Boolean) -> Unit,
    onDeveloperMode: (Boolean) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val settings = state.settings
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    var versionTaps by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenHeader(
            title = copy.settings,
            actions = { IconAction(Glyph.Close, copy.close, onClose) },
        )

        GapSection()
        SectionLabel(copy.appearance)
        Spacer(Modifier.height(Space.md))
        SegmentedControl(
            options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
            selected = settings.themeMode,
            label = {
                when (it) {
                    ThemeMode.System -> copy.themeSystem
                    ThemeMode.Light -> copy.themeLight
                    ThemeMode.Dark -> copy.themeDark
                }
            },
            onSelect = onTheme,
        )
        Spacer(Modifier.height(Space.sm))
        SText(
            text = copy.t(
                "Il tema si adatta comunque al meteo: le notti e i temporali restano scuri.",
                "The theme still follows the weather: nights and storms stay dark.",
            ),
            style = Sereno.type.caption,
            emphasis = Emphasis.tertiary,
        )

        GapSection()
        SectionLabel(copy.units)
        Spacer(Modifier.height(Space.md))
        LabelledControl(copy.temperature) {
            SegmentedControl(
                options = TemperatureUnit.entries,
                selected = settings.temperatureUnit,
                label = { if (it == TemperatureUnit.Celsius) "°C" else "°F" },
                onSelect = onTemperatureUnit,
            )
        }
        Spacer(Modifier.height(Space.lg))
        LabelledControl(copy.wind) {
            SegmentedControl(
                options = SpeedUnit.entries,
                selected = settings.speedUnit,
                label = { it.symbol },
                onSelect = onSpeedUnit,
            )
        }
        Spacer(Modifier.height(Space.lg))
        LabelledControl(copy.precipitation) {
            SegmentedControl(
                options = PrecipUnit.entries,
                selected = settings.precipUnit,
                label = { it.symbol },
                onSelect = onPrecipUnit,
            )
        }
        Spacer(Modifier.height(Space.lg))
        LabelledControl(copy.pressure) {
            SegmentedControl(
                options = PressureUnit.entries,
                selected = settings.pressureUnit,
                label = { it.symbol },
                onSelect = onPressureUnit,
            )
        }

        GapSection()
        SectionLabel(copy.language)
        Spacer(Modifier.height(Space.md))
        SegmentedControl(
            options = listOf(null, Lang.It, Lang.En),
            selected = settings.language,
            label = {
                when (it) {
                    null -> copy.themeSystem
                    Lang.It -> "Italiano"
                    Lang.En -> "English"
                }
            },
            onSelect = onLanguage,
        )

        GapSection()
        SectionLabel(copy.t("Movimento e notifiche", "Motion and notifications"))
        Spacer(Modifier.height(Space.sm))
        ToggleRow(
            label = copy.reduceMotion,
            detail = copy.t(
                "Disattiva le animazioni ambientali. Segue comunque le impostazioni di sistema.",
                "Turns off ambient animation. System settings are always honoured.",
            ),
            checked = settings.reduceMotion == true,
            onCheckedChange = { onReduceMotion(if (it) true else null) },
        )
        SectionRule()
        ToggleRow(
            label = copy.rainAlerts,
            detail = copy.t("Avvisa quando sta per piovere.", "Tells you when rain is about to start."),
            checked = settings.rainNotifications,
            onCheckedChange = onRainNotifications,
        )
        SectionRule()
        ToggleRow(
            label = copy.severeAlerts,
            detail = copy.t("Temporali, vento forte, gelo.", "Storms, strong wind, ice."),
            checked = settings.severeNotifications,
            onCheckedChange = onSevereNotifications,
        )

        GapSection()
        SectionLabel(copy.privacy)
        Spacer(Modifier.height(Space.md))
        SText(copy.privacyBody, style = Sereno.type.body, emphasis = Emphasis.secondary)

        GapSection()
        SectionLabel(copy.dataSources)
        Spacer(Modifier.height(Space.md))
        listOf(
            "Open-Meteo" to copy.t(
                "Previsioni multi-modello, qualità dell'aria e ricerca località. Licenza CC BY 4.0.",
                "Multi-model forecasts, air quality and place search. CC BY 4.0.",
            ),
            "ECMWF" to copy.t("Modelli IFS e AIFS.", "IFS and AIFS models."),
            "DWD" to copy.t("Modelli ICON e ICON-EU.", "ICON and ICON-EU models."),
            "ItaliaMeteo · ARPAE" to copy.t(
                "ICON-2I, modello ad alta risoluzione per l'Italia.",
                "ICON-2I, the high-resolution model for Italy.",
            ),
            "NOAA" to copy.t("Modello GFS.", "GFS model."),
            "Protezione Civile" to copy.t("Mosaico radar nazionale.", "National radar mosaic."),
        ).forEachIndexed { index, (name, description) ->
            if (index > 0) SectionRule()
            Column(Modifier.fillMaxWidth().padding(vertical = Space.md)) {
                SText(name, style = Sereno.type.bodyStrong)
                Spacer(Modifier.height(2.dp))
                SText(description, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
            }
        }

        GapSection()
        Pressable(
            onClick = {
                versionTaps += 1
                if (versionTaps >= 5 && !settings.developerMode) onDeveloperMode(true)
            },
            modifier = Modifier.fillMaxWidth(),
            pressScale = 1f,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Touch.min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SText("Sereno", style = Sereno.type.body, emphasis = Emphasis.secondary)
                Spacer(Modifier.weight(1f))
                SText(version, style = Sereno.type.data, emphasis = Emphasis.tertiary)
            }
        }

        if (settings.developerMode) {
            SectionRule()
            Pressable(
                onClick = onOpenDebug,
                modifier = Modifier.fillMaxWidth(),
                pressScale = 0.995f,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Touch.min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SText(copy.developer, style = Sereno.type.body)
                    Spacer(Modifier.weight(1f))
                    app.sereno.weather.design.SGlyph(Glyph.ChevronRight, size = 16.dp, emphasis = Emphasis.tertiary)
                }
            }
        }

        Spacer(Modifier.height(Space.railClearance))
    }
}

@Composable
private fun LabelledControl(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SText(label, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
        Spacer(Modifier.height(Space.sm))
        content()
    }
}
