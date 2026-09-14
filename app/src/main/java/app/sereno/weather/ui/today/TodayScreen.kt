package app.sereno.weather.ui.today

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapBlock
import app.sereno.weather.design.GapRow
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.SGlyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.design.WeatherGlyph
import app.sereno.weather.domain.model.AirQuality
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.domain.story.Narrator
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.chart.HourlyRibbon
import app.sereno.weather.ui.components.AlertRow
import app.sereno.weather.ui.components.ConditionRow
import app.sereno.weather.ui.components.ConfidenceMeter
import app.sereno.weather.ui.components.DailyRow
import app.sereno.weather.ui.components.IconAction
import app.sereno.weather.ui.components.NowcastLine
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule
import app.sereno.weather.ui.components.StatusDot
import app.sereno.weather.ui.components.TodaySkeleton
import app.sereno.weather.ui.components.UvMark
import app.sereno.weather.ui.components.WindArrow

/**
 * The Today screen.
 *
 * It is arranged as a piece of editorial layout rather than a dashboard: one
 * dominant element (the temperature), one sentence that answers the question
 * people actually open a weather app to ask (the nowcast), one human sentence
 * about the day, then progressively more detail for anyone who wants it.
 *
 * There is not a single card on this screen. Structure comes from labels,
 * hairlines and vertical rhythm, which is what lets it stay dense without
 * feeling boxed in.
 */
@Composable
fun TodayScreen(
    state: AppState,
    formatter: Formatter,
    nowEpoch: Long,
    scrollState: ScrollState,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val bundle = state.bundle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))

        ScreenHeader(
            title = state.selectedPlace?.name ?: copy.t("Sereno", "Sereno"),
            subtitle = state.selectedPlace?.subtitle,
            onTitleClick = onOpenPlaces,
            leading = if (state.selectedPlace?.isCurrentLocation == true) {
                { SGlyph(Glyph.Pin, size = 14.dp, emphasis = Emphasis.tertiary) }
            } else null,
            actions = {
                IconAction(Glyph.Gear, copy.settings, onOpenSettings)
            },
        )

        if (bundle == null) {
            Spacer(Modifier.height(Space.blockGap))
            TodaySkeleton()
            Spacer(Modifier.height(Space.railClearance))
            return@Column
        }

        HeroBlock(bundle, formatter, copy, nowEpoch)

        GapBlock()
        NowcastLine(bundle.nowcast, copy)

        val narrative = remember(bundle) {
            Narrator.dayNarrative(
                hours = bundle.hours.filter {
                    it.epochSeconds in nowEpoch..(nowEpoch + 20 * 3600)
                },
                utcOffsetSeconds = bundle.utcOffsetSeconds,
                copy = copy,
            )
        }
        if (narrative != null) {
            GapBlock()
            SText(narrative, style = Sereno.type.narrative, emphasis = Emphasis.secondary)
        }

        if (bundle.alerts.isNotEmpty()) {
            GapSection()
            SectionLabel(copy.t("Allerte", "Alerts"))
            Spacer(Modifier.height(Space.lg))
            bundle.alerts.take(3).forEachIndexed { index, alert ->
                if (index > 0) {
                    Spacer(Modifier.height(Space.lg))
                    SectionRule()
                    Spacer(Modifier.height(Space.lg))
                }
                AlertRow(alert, copy, formatter)
            }
        }

        GapSection()
        HourlySection(bundle, formatter, copy, nowEpoch)

        GapSection()
        ConfidenceSection(bundle, copy, nowEpoch)

        GapSection()
        ConditionsSection(bundle, formatter, copy, nowEpoch)

        GapSection()
        DaysPreview(bundle, formatter, copy, nowEpoch, onOpenDay)

        GapSection()
        Attribution(bundle, formatter, copy, nowEpoch)

        Spacer(Modifier.height(Space.railClearance))
    }
}

/**
 * The hero.
 *
 * The temperature is set in the display optical cut at 104sp with the degree
 * sign at less than half size — a real typographic detail that stops the symbol
 * from shouting as loudly as the number, and the main reason this block reads
 * as typeset rather than as a large label.
 */
@Composable
private fun HeroBlock(
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
) {
    val type = Sereno.type
    val current = bundle.current
    val hour = bundle.hours.firstOrNull { it.epochSeconds >= nowEpoch - 3600 }

    val temperature = current?.temperature ?: hour?.temperature
    val code = current?.weatherCode ?: hour?.weatherCode
    val isDay = current?.isDay ?: hour?.isDay ?: true
    val today = bundle.days.firstOrNull()

    Spacer(Modifier.height(Space.sectionGap))

    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            // The degree sign is set at full size in the display cut rather than
            // as a superscript. Inter's ring already sits at cap height, so
            // shrinking and shifting it only ever lands it slightly wrong; at
            // full size it is simply correct.
            SText(
                text = formatter.temperature(temperature),
                style = type.display,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.sm))
            SText(
                text = copy.conditionName(
                    WeatherCodes.condition(code),
                    WeatherCodes.intensity(code),
                ),
                style = type.headline,
                emphasis = Emphasis.secondary,
            )
        }
        Spacer(Modifier.width(Space.lg))
        WeatherGlyph(
            code = code,
            isDay = isDay,
            size = 68.dp,
            animated = true,
            modifier = Modifier.padding(top = 10.dp),
            contentDescription = copy.conditionName(WeatherCodes.condition(code)),
        )
    }

    if (today != null) {
        GapRow()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SText(
                "${copy.high} ${formatter.temperature(today.temperatureMax)}",
                style = type.data,
                emphasis = Emphasis.secondary,
            )
            SText("   ·   ", style = type.data, emphasis = Emphasis.quaternary)
            SText(
                "${copy.low} ${formatter.temperature(today.temperatureMin)}",
                style = type.data,
                emphasis = Emphasis.secondary,
            )
        }
    }
}

/**
 * The hourly section.
 *
 * The section label doubles as the scrub readout: while a finger is on the
 * ribbon the heading becomes the values under it, which avoids adding a
 * tooltip that would cover the very chart it describes.
 */
@Composable
private fun HourlySection(
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
) {
    var scrubbed by remember { mutableStateOf<BlendedHour?>(null) }
    val today = bundle.days.firstOrNull()

    val heading = scrubbed?.let { hour ->
        buildString {
            append(formatter.time(hour.epochSeconds))
            append("  ·  ")
            append(formatter.temperature(hour.temperature))
            val mm = hour.precipitation ?: 0.0
            if (mm >= BlendedHour.WET_THRESHOLD_MM) {
                append("  ·  ")
                append(formatter.precipitation(mm))
            }
            hour.precipitationProbability?.let {
                if (it >= 10) {
                    append("  ·  ")
                    append(formatter.percent(it))
                }
            }
        }
    } ?: copy.t("Prossime 24 ore", "Next 24 hours")

    SectionLabel(heading)
    Spacer(Modifier.height(Space.md))
    HourlyRibbon(
        hours = bundle.hours,
        nowEpoch = nowEpoch,
        formatter = formatter,
        sunriseEpoch = today?.sunriseEpoch,
        sunsetEpoch = today?.sunsetEpoch,
        onSelectionChange = { scrubbed = it },
    )
}

@Composable
private fun ConfidenceSection(bundle: ForecastBundle, copy: Copy, nowEpoch: Long) {
    val explanation = remember(bundle) { Narrator.confidenceExplanation(bundle, copy) }
    val confidence = remember(bundle, nowEpoch) {
        app.sereno.weather.domain.synthesis.ConfidenceEngine.aggregate(
            bundle.hours.filter { it.epochSeconds in nowEpoch..(nowEpoch + 24 * 3600) }
                .map { it.confidence },
        )
    }
    ConfidenceMeter(confidence = confidence, copy = copy, explanation = explanation)
}

/**
 * The conditions block.
 *
 * A specification list, not a grid of tiles. Each row carries one small inline
 * mark — a wind arrow, a UV scale, an air-quality dot — so the column has
 * texture and a scan down it conveys more than the numbers alone.
 */
@Composable
private fun ConditionsSection(
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
) {
    val current = bundle.current
    val hour = bundle.hours.firstOrNull { it.epochSeconds >= nowEpoch - 3600 }
    val air = bundle.airQuality

    SectionLabel(copy.t("Condizioni", "Conditions"))
    Spacer(Modifier.height(Space.sm))

    val feels = current?.apparentTemperature ?: hour?.apparentTemperature
    ConditionRow(copy.feelsLike, formatter.temperature(feels))
    SectionRule()

    val wind = current?.windSpeed ?: hour?.windSpeed
    val gust = current?.windGust ?: hour?.windGust
    val direction = current?.windDirection ?: hour?.windDirection
    ConditionRow(
        label = copy.wind,
        value = formatter.speed(wind),
        detail = buildString {
            append(formatter.windDirection(direction))
            if (gust != null && wind != null && gust > wind * 1.3) {
                append("  ·  ${copy.gusts} ${formatter.speed(gust)}")
            }
        },
        mark = { WindArrow(direction) },
    )
    SectionRule()

    ConditionRow(copy.humidity, formatter.percent(current?.humidity ?: hour?.humidity))
    SectionRule()

    val uv = hour?.uvIndex
    ConditionRow(
        label = copy.uvIndex,
        value = formatter.uv(uv),
        detail = formatter.uvDescription(uv),
        mark = { UvMark(uv) },
    )
    SectionRule()

    ConditionRow(copy.pressure, formatter.pressure(current?.pressure ?: hour?.pressure))
    SectionRule()

    ConditionRow(copy.visibility, formatter.visibility(hour?.visibility))
    SectionRule()

    ConditionRow(
        label = copy.dewPoint,
        value = formatter.temperature(hour?.dewPoint),
    )

    if (air?.europeanAqi != null) {
        SectionRule()
        ConditionRow(
            label = copy.airQuality,
            value = air.europeanAqi.toString(),
            detail = formatter.airQualityDescription(air.europeanAqi),
            mark = { StatusDot(aqiColor(air.europeanAqi)) },
        )
    }
}

@Composable
private fun aqiColor(aqi: Int?): androidx.compose.ui.graphics.Color {
    val data = Sereno.data
    return when {
        aqi == null -> data.cloud
        aqi <= 20 -> data.positive
        aqi <= 40 -> data.positive
        aqi <= 60 -> data.caution
        aqi <= 80 -> data.warning
        else -> data.severe
    }
}

@Composable
private fun DaysPreview(
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
    onOpenDay: (Long) -> Unit,
) {
    val days = bundle.days.take(7)
    if (days.isEmpty()) return

    val periodMin = days.mapNotNull { it.temperatureMin }.minOrNull() ?: 0.0
    val periodMax = days.mapNotNull { it.temperatureMax }.maxOrNull() ?: 1.0

    SectionLabel(copy.t("Prossimi giorni", "Next days"))
    Spacer(Modifier.height(Space.sm))

    days.forEachIndexed { index, day ->
        if (index > 0) SectionRule()
        DailyRow(
            day = day,
            label = formatter.dayLabel(day.epochSeconds, nowEpoch),
            formatter = formatter,
            copy = copy,
            periodMin = periodMin,
            periodMax = periodMax,
            onClick = { onOpenDay(day.epochSeconds) },
        )
    }
}

/**
 * Provenance, at the foot of the page.
 *
 * Which models were actually used, and how old the data is. It is small and
 * quiet, but it is never hidden — the whole premise of the app is that the user
 * gets to see where the forecast came from.
 */
@Composable
private fun Attribution(
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
) {
    val models = bundle.availableModels
    Column(Modifier.fillMaxWidth()) {
        SectionRule()
        Spacer(Modifier.height(Space.lg))
        SText(
            text = if (models.isEmpty()) "Open-Meteo" else models.joinToString(" · ") { it.displayName },
            style = Sereno.type.caption,
            emphasis = Emphasis.quaternary,
        )
        Spacer(Modifier.height(4.dp))
        SText(
            text = buildString {
                append(copy.updatedAt)
                append(" ")
                append(formatter.relativeAge(bundle.ageMinutes(nowEpoch)))
                if (bundle.fromCache) append(" · ${copy.t("da cache", "cached")}")
            },
            style = Sereno.type.caption,
            emphasis = Emphasis.quaternary,
        )
        Spacer(Modifier.height(Space.xs))
        SText(
            text = "Open-Meteo · ECMWF · DWD · NOAA · ItaliaMeteo ARPAE",
            style = Sereno.type.caption,
            emphasis = Emphasis.quaternary,
        )
    }
}
