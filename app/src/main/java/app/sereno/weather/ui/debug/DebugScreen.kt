package app.sereno.weather.ui.debug

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sereno.weather.data.ForecastResource
import app.sereno.weather.data.provider.RadarStatus
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.domain.mock.MockWeather
import app.sereno.weather.domain.model.ConfidenceFactor
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.components.IconAction
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule
import app.sereno.weather.ui.components.SegmentedControl
import app.sereno.weather.ui.components.TextButton

/**
 * Developer tools.
 *
 * Two jobs. First, to make the forecast *auditable*: which models answered, how
 * old the run is, how long the call took, and the raw numbers that produced the
 * confidence score — so a suspicious-looking forecast can be checked rather than
 * argued about.
 *
 * Second, the mock states. Every atmospheric design in the app can be summoned
 * on demand instead of waiting for the weather to cooperate, and because the
 * mocks run through the real synthesis pipeline, what appears is what the live
 * code will draw.
 */
@Composable
fun DebugScreen(
    state: AppState,
    formatter: Formatter,
    nowEpoch: Long,
    onClose: () -> Unit,
    onMockState: (String?) -> Unit,
    onClearCache: () -> Unit,
    onRefresh: () -> Unit,
    cacheSize: suspend () -> Long,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val bundle = state.bundle
    val scrollState = rememberScrollState()

    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bundle) { cacheBytes = runCatching { cacheSize() }.getOrNull() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenHeader(
            title = copy.developer,
            subtitle = "Sereno diagnostics",
            actions = { IconAction(Glyph.Close, copy.close, onClose) },
        )

        GapSection()
        SectionLabel("Mock weather")
        Spacer(Modifier.height(Space.md))
        SText(
            text = copy.t(
                "Sostituisce la previsione reale con uno stato sintetico, passando comunque dal motore di sintesi.",
                "Replaces the live forecast with a synthetic state, still routed through the synthesis engine.",
            ),
            style = Sereno.type.caption,
            emphasis = Emphasis.tertiary,
        )
        Spacer(Modifier.height(Space.md))

        MockWeather.State.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { mock ->
                    val active = state.settings.mockState == mock.key
                    Row(Modifier.weight(1f).padding(end = Space.sm)) {
                        app.sereno.weather.ui.components.ToggleRow(
                            label = mock.label,
                            checked = active,
                            onCheckedChange = { checked -> onMockState(if (checked) mock.key else null) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.settings.mockState != null) {
            Spacer(Modifier.height(Space.md))
            TextButton(copy.t("Torna al meteo reale", "Back to live weather"), onClick = { onMockState(null) })
        }

        GapSection()
        SectionLabel("Request")
        Spacer(Modifier.height(Space.sm))
        DebugRow("Forecast provider", "Open-Meteo")
        SectionRule()
        DebugRow("Status", statusLabel(state.forecast))
        SectionRule()
        DebugRow("Latency", bundle?.let { "${it.latencyMs} ms" } ?: "—")
        SectionRule()
        DebugRow("From cache", bundle?.fromCache?.toString() ?: "—")
        SectionRule()
        DebugRow("Forecast age", bundle?.let { "${it.ageMinutes(nowEpoch)} min" } ?: "—")
        SectionRule()
        DebugRow("Fetched at", bundle?.let { formatter.time(it.fetchedAtEpoch) } ?: "—")
        SectionRule()
        DebugRow("Cache on disk", cacheBytes?.let { "${it / 1024} KB" } ?: "—")

        GapSection()
        SectionLabel("Location")
        Spacer(Modifier.height(Space.sm))
        val place = state.selectedPlace
        DebugRow("Place", place?.name ?: "—")
        SectionRule()
        DebugRow(
            "Coordinates",
            place?.let {
                "%.4f, %.4f".format(it.coordinates.latitude, it.coordinates.longitude)
            } ?: "—",
        )
        SectionRule()
        DebugRow("Timezone", bundle?.timezone ?: "—")
        SectionRule()
        DebugRow("UTC offset", bundle?.let { "${it.utcOffsetSeconds / 3600}h" } ?: "—")
        SectionRule()
        DebugRow("Permission", state.locationState.name)

        GapSection()
        SectionLabel("Models")
        Spacer(Modifier.height(Space.sm))
        if (bundle == null) {
            SText("—", style = Sereno.type.body, emphasis = Emphasis.tertiary)
        } else {
            bundle.availability.forEachIndexed { index, availability ->
                if (index > 0) SectionRule()
                DebugRow(
                    label = availability.model.displayName,
                    value = if (availability.available) {
                        "${availability.hoursReturned} h"
                    } else {
                        availability.note ?: "unavailable"
                    },
                    dim = !availability.available,
                )
            }
        }

        GapSection()
        SectionLabel("Radar")
        Spacer(Modifier.height(Space.sm))
        DebugRow("DPC", radarLabel(state.radar, formatter))

        GapSection()
        SectionLabel("Confidence · next hour")
        Spacer(Modifier.height(Space.sm))
        val nextHour = bundle?.hours?.firstOrNull { it.epochSeconds >= nowEpoch }
        if (nextHour == null) {
            SText("—", style = Sereno.type.body, emphasis = Emphasis.tertiary)
        } else {
            DebugRow("Score", "${nextHour.confidence.percent}% · ${nextHour.confidence.band}")
            SectionRule()
            DebugRow("Models", nextHour.confidence.modelCount.toString())
            nextHour.confidence.drivers.forEach { driver ->
                SectionRule()
                DebugRow(
                    label = copy.confidenceFactor(driver.factor),
                    value = "${"%.2f".format(driver.score)} · ${unitFor(driver.factor, driver.value)}",
                )
            }
        }

        GapSection()
        Row(Modifier.fillMaxWidth()) {
            TextButton(copy.refresh, onRefresh, Modifier.weight(1f))
            TextButton("Clear cache", { onClearCache(); cacheBytes = 0 }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(Space.railClearance))
    }
}

@Composable
private fun DebugRow(label: String, value: String, dim: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SText(label, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
        Spacer(Modifier.weight(1f))
        SText(
            text = value,
            style = Sereno.type.dataSmall,
            emphasis = if (dim) Emphasis.quaternary else Emphasis.primary,
            textAlign = TextAlign.End,
        )
    }
}

private fun statusLabel(resource: ForecastResource): String = when (resource) {
    is ForecastResource.Data -> if (resource.refreshing) "refreshing" else "ok"
    is ForecastResource.Failed -> "failed: ${resource.cause::class.simpleName}"
    ForecastResource.Loading -> "loading"
}

private fun radarLabel(status: RadarStatus?, formatter: Formatter): String = when (status) {
    null -> "—"
    is RadarStatus.Available -> "ok · ${formatter.time(status.latestEpoch)}"
    is RadarStatus.OutOfCoverage -> "out of coverage"
    is RadarStatus.Unavailable -> "unavailable"
}

/** Adds the unit each confidence driver is actually measured in. */
private fun unitFor(factor: ConfidenceFactor, value: Double): String = when (factor) {
    ConfidenceFactor.TemperatureSpread -> "σ %.2f °C".format(value)
    ConfidenceFactor.PrecipitationAgreement -> "%.0f%% agree".format(value * 100)
    ConfidenceFactor.AmountSpread -> "cv %.2f".format(value)
    ConfidenceFactor.WindSpread -> "σ %.1f km/h".format(value)
    ConfidenceFactor.ModelCount -> "%.0f models".format(value)
    ConfidenceFactor.LeadTime -> "+%.0f h".format(value)
}
