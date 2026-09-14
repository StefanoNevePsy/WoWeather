package app.sereno.weather.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapRow
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Skeleton
import app.sereno.weather.design.Space
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.domain.synthesis.ModelWeights
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.chart.LabMetric
import app.sereno.weather.ui.chart.ModelComparisonChart
import app.sereno.weather.ui.chart.ModelStyles
import app.sereno.weather.ui.components.MessageState
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule
import app.sereno.weather.ui.components.SegmentedControl
import kotlin.math.abs

/**
 * Forecast Lab.
 *
 * The screen that shows the working. It is the most technical thing in the app
 * and it was written to be readable by someone who has never heard of ICON or
 * IFS: the chart shows lines that agree or do not, the readout underneath names
 * the models and their numbers in plain language, and the verdict is a phrase
 * rather than a statistic.
 *
 * Nothing here is required to use Sereno. That is the point — "simple when you
 * want it, deep when you need it" only works if the deep part is genuinely
 * optional.
 */
@Composable
fun LabScreen(
    state: AppState,
    formatter: Formatter,
    nowEpoch: Long,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val bundle = state.bundle

    var metric by remember { mutableStateOf(LabMetric.Temperature) }
    var horizon by remember { mutableStateOf(48) }
    var selectedEpoch by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenHeader(
            title = copy.forecastLab,
            subtitle = state.selectedPlace?.name,
        )

        if (bundle == null) {
            Spacer(Modifier.height(Space.blockGap))
            Skeleton(Modifier.fillMaxWidth().height(210.dp))
            Spacer(Modifier.height(Space.railClearance))
            return@Column
        }

        val seriesByModel = remember(bundle) {
            bundle.models.entries.mapNotNull { (apiId, series) ->
                val model = WeatherModel.byApiId(apiId) ?: return@mapNotNull null
                if (!series.available || series.hourly.isEmpty()) null else model to series.hourly
            }.toMap()
        }

        if (seriesByModel.size < 2) {
            MessageState(
                title = copy.t("Un solo modello qui", "Only one model here"),
                body = copy.t(
                    "Per questa località è disponibile un solo modello, quindi non c'è nulla da confrontare.",
                    "Only one model covers this location, so there is nothing to compare.",
                ),
                glyph = Glyph.Info,
            )
            Spacer(Modifier.height(Space.railClearance))
            return@Column
        }

        Spacer(Modifier.height(Space.blockGap))

        SegmentedControl(
            options = LabMetric.entries,
            selected = metric,
            label = { metricLabel(it, copy) },
            onSelect = { metric = it; selectedEpoch = null },
        )

        Spacer(Modifier.height(Space.md))
        SegmentedControl(
            options = listOf(24, 48, 72, 120),
            selected = horizon,
            label = { copy.t("${it}h", "${it}h") },
            onSelect = { horizon = it; selectedEpoch = null },
        )

        Spacer(Modifier.height(Space.xl))
        ModelComparisonChart(
            seriesByModel = seriesByModel,
            metric = metric,
            nowEpoch = nowEpoch,
            hoursShown = horizon,
            formatter = formatter,
            selectedEpoch = selectedEpoch,
            onSelect = { selectedEpoch = it },
        )

        Spacer(Modifier.height(Space.xl))
        Legend(seriesByModel.keys.toList(), copy)

        GapSection()
        AgreementReadout(bundle, seriesByModel, metric, selectedEpoch, nowEpoch, formatter, copy)

        GapSection()
        WeightingExplainer(nowEpoch, selectedEpoch, copy)

        GapSection()
        ModelCatalogue(bundle, copy)

        Spacer(Modifier.height(Space.railClearance))
    }
}

private fun metricLabel(metric: LabMetric, copy: Copy): String = when (metric) {
    LabMetric.Temperature -> copy.t("Temp.", "Temp")
    LabMetric.Precipitation -> copy.t("Pioggia", "Rain")
    LabMetric.Wind -> copy.t("Vento", "Wind")
    LabMetric.Cloud -> copy.t("Nuvole", "Cloud")
}

/** The legend, showing each model's real line style rather than a colour swatch. */
@Composable
private fun Legend(models: List<WeatherModel>, copy: Copy) {
    val atmosphere = Sereno.atmosphere
    Column(Modifier.fillMaxWidth()) {
        models.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEach { model ->
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(
                            Modifier
                                .width(18.dp)
                                .height(10.dp)
                                .clearAndSetSemantics { },
                        ) {
                            drawLine(
                                color = ModelStyles.color(model, atmosphere.dark),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = ModelStyles.strokeWidth(model).dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = ModelStyles.dash(model)?.let { PathEffect.dashPathEffect(it) },
                            )
                        }
                        Spacer(Modifier.width(Space.sm))
                        SText(
                            model.displayName,
                            style = Sereno.type.dataSmall,
                            emphasis = Emphasis.secondary,
                            maxLines = 1,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The readout that translates the chart into a sentence.
 *
 * This is the part that makes the Lab usable by a non-meteorologist: the models
 * are listed with their numbers, and then a plain verdict says whether that
 * amounts to agreement.
 */
@Composable
private fun AgreementReadout(
    bundle: ForecastBundle,
    seriesByModel: Map<WeatherModel, List<HourPoint>>,
    metric: LabMetric,
    selectedEpoch: Long?,
    nowEpoch: Long,
    formatter: Formatter,
    copy: Copy,
) {
    val atmosphere = Sereno.atmosphere
    // With nothing selected, show the most *interesting* hour rather than the
    // first: the one where the models disagree most within the next day.
    val epoch = selectedEpoch ?: remember(seriesByModel, metric) {
        mostDivergentHour(seriesByModel, metric, nowEpoch)
    } ?: return

    val values = seriesByModel.mapNotNull { (model, points) ->
        points.firstOrNull { it.epochSeconds == epoch }?.let { point ->
            metric.extract(point)?.let { model to it }
        }
    }.sortedByDescending { it.second }
    if (values.isEmpty()) return

    val spread = values.maxOf { it.second } - values.minOf { it.second }
    val verdict = verdictFor(metric, spread, copy)

    SectionLabel("${formatter.weekdayShort(epoch)} ${formatter.time(epoch)}")
    Spacer(Modifier.height(Space.md))

    values.forEach { (model, value) ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(8.dp).clearAndSetSemantics { }) {
                drawCircle(ModelStyles.color(model, atmosphere.dark))
            }
            Spacer(Modifier.width(Space.md))
            SText(model.displayName, style = Sereno.type.body, emphasis = Emphasis.secondary)
            Spacer(Modifier.weight(1f))
            SText(formatValue(metric, value, formatter), style = Sereno.type.data)
        }
    }

    Spacer(Modifier.height(Space.md))
    SectionRule()
    Spacer(Modifier.height(Space.md))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SText(
            text = "→",
            style = Sereno.type.body,
            emphasis = Emphasis.quaternary,
        )
        Spacer(Modifier.width(Space.sm))
        SText(verdict.first, style = Sereno.type.bodyStrong, color = verdict.second)
    }
    GapRow()
    SText(
        text = if (selectedEpoch == null) {
            copy.t(
                "Questa è l'ora in cui i modelli divergono di più nelle prossime 24 ore. Tocca il grafico per un'altra ora.",
                "This is the hour where the models diverge most in the next 24 hours. Tap the chart for another hour.",
            )
        } else {
            copy.t("Tocca il grafico per un'altra ora.", "Tap the chart for another hour.")
        },
        style = Sereno.type.caption,
        emphasis = Emphasis.tertiary,
    )
}

/**
 * Explains the weighting in force right now.
 *
 * Weighting is the app's most consequential opinion, so it is shown rather than
 * hidden: which regime applies at this lead time, and which model therefore
 * carries the most weight.
 */
@Composable
private fun WeightingExplainer(nowEpoch: Long, selectedEpoch: Long?, copy: Copy) {
    val leadHours = (((selectedEpoch ?: nowEpoch) - nowEpoch) / 3600).toInt().coerceAtLeast(0)
    val regime = ModelWeights.regime(leadHours)

    val weights = WeatherModel.entries
        .map { it to ModelWeights.weight(it, leadHours) }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }

    SectionLabel(copy.t("Come viene pesata", "How it is weighted"))
    Spacer(Modifier.height(Space.md))

    SText(
        text = when (regime) {
            ModelWeights.Regime.Nowcast -> copy.t(
                "Nelle prossime ore conta la risoluzione: il modello regionale ad alta risoluzione ha il peso maggiore.",
                "In the next few hours resolution wins: the high-resolution regional model carries the most weight.",
            )
            ModelWeights.Regime.HighResolution -> copy.t(
                "Fino a 72 ore Sereno dà più peso a ICON-2I e agli altri modelli regionali, che risolvono i fenomeni locali.",
                "Out to 72 hours Sereno leans on ICON-2I and the other regional models, which resolve local features.",
            )
            ModelWeights.Regime.MediumRange -> copy.t(
                "Da 3 a 7 giorni il peso passa a ECMWF, il miglior modello globale nel medio termine.",
                "From day 3 to day 7 the weight shifts to ECMWF, the best global model in the medium range.",
            )
            ModelWeights.Regime.Extended -> copy.t(
                "Oltre la settimana restano solo i modelli globali e l'affidabilità cala progressivamente.",
                "Beyond a week only the global models remain, and confidence falls steadily.",
            )
        },
        style = Sereno.type.body,
        emphasis = Emphasis.secondary,
    )

    Spacer(Modifier.height(Space.lg))
    weights.forEach { (model, weight) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SText(model.displayName, style = Sereno.type.caption, emphasis = Emphasis.secondary)
            Spacer(Modifier.weight(1f))
            WeightBar(weight.toFloat(), Modifier.width(96.dp))
            Spacer(Modifier.width(Space.md))
            SText(
                "%.0f%%".format(weight * 100),
                style = Sereno.type.dataSmall,
                emphasis = Emphasis.tertiary,
            )
        }
    }
}

@Composable
private fun WeightBar(weight: Float, modifier: Modifier = Modifier) {
    val atmosphere = Sereno.atmosphere
    Canvas(modifier.height(3.dp).clearAndSetSemantics { }) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(atmosphere.ink(Emphasis.hairline), size = size, cornerRadius = radius)
        drawRoundRect(
            color = atmosphere.ink(Emphasis.secondary),
            size = androidx.compose.ui.geometry.Size(size.width * weight.coerceIn(0f, 1f), size.height),
            cornerRadius = radius,
        )
    }
}

/** Every model in the catalogue, with what it is and whether it reaches here. */
@Composable
private fun ModelCatalogue(bundle: ForecastBundle, copy: Copy) {
    SectionLabel(copy.t("I modelli", "The models"))
    Spacer(Modifier.height(Space.sm))

    WeatherModel.entries.forEachIndexed { index, model ->
        if (index > 0) SectionRule()
        val availability = bundle.availability.firstOrNull { it.model == model }
        val available = availability?.available == true

        Column(Modifier.fillMaxWidth().padding(vertical = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SText(
                    model.displayName,
                    style = Sereno.type.bodyStrong,
                    emphasis = if (available) Emphasis.primary else Emphasis.tertiary,
                )
                Spacer(Modifier.weight(1f))
                SText(
                    text = if (available) {
                        "${model.nativeResolutionKm.trimZero()} km · ${model.horizonHours}h"
                    } else {
                        copy.unavailableHere
                    },
                    style = Sereno.type.dataSmall,
                    emphasis = Emphasis.tertiary,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(2.dp))
            SText(
                text = "${model.centre} · ${if (model.isRegional) copy.regionalModel else copy.globalModel}",
                style = Sereno.type.caption,
                emphasis = Emphasis.quaternary,
            )
        }
    }
}

// ---------------------------------------------------------------------------

private fun Double.trimZero(): String =
    if (this == toInt().toDouble()) toInt().toString() else "%.1f".format(this)

private fun formatValue(metric: LabMetric, value: Double, formatter: Formatter): String = when (metric) {
    LabMetric.Temperature -> formatter.temperature(value)
    LabMetric.Precipitation -> formatter.precipitation(value)
    LabMetric.Wind -> formatter.speed(value)
    LabMetric.Cloud -> formatter.percent(value)
}

/**
 * Thresholds for what counts as agreement, per metric.
 *
 * They are in each metric's own units because a 2 °C spread is unremarkable
 * while a 2 mm spread on an hourly total is the difference between damp and
 * soaked.
 */
@Composable
private fun verdictFor(
    metric: LabMetric,
    spread: Double,
    copy: Copy,
): Pair<String, androidx.compose.ui.graphics.Color> {
    val data = Sereno.data
    val (strong, partial) = when (metric) {
        LabMetric.Temperature -> 1.5 to 3.5
        LabMetric.Precipitation -> 0.6 to 2.5
        LabMetric.Wind -> 6.0 to 15.0
        LabMetric.Cloud -> 18.0 to 42.0
    }
    return when {
        spread <= strong -> copy.strongAgreement to data.positive
        spread <= partial -> copy.partialAgreement to data.caution
        else -> copy.disagreement to data.warning
    }
}

private fun mostDivergentHour(
    seriesByModel: Map<WeatherModel, List<HourPoint>>,
    metric: LabMetric,
    nowEpoch: Long,
): Long? {
    val horizon = nowEpoch + 24 * 3600
    val byEpoch = mutableMapOf<Long, MutableList<Double>>()
    seriesByModel.values.forEach { points ->
        points.forEach { point ->
            if (point.epochSeconds in nowEpoch..horizon) {
                metric.extract(point)?.let { byEpoch.getOrPut(point.epochSeconds) { mutableListOf() } += it }
            }
        }
    }
    return byEpoch.entries
        .filter { it.value.size >= 2 }
        .maxByOrNull { abs(it.value.max() - it.value.min()) }
        ?.key
}
