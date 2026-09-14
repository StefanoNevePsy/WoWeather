package app.sereno.weather.ui.daydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapRow
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.design.WeatherGlyph
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.domain.story.Narrator
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.chart.HourlyRibbon
import app.sereno.weather.ui.components.ConditionRow
import app.sereno.weather.ui.components.ConfidenceMeter
import app.sereno.weather.ui.components.IconAction
import app.sereno.weather.ui.components.MessageState
import app.sereno.weather.ui.components.SectionRule
import app.sereno.weather.ui.components.UvMark
import app.sereno.weather.ui.components.WindArrow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One day, in full.
 *
 * Reached by tapping a row in the daily list. It reuses the hourly ribbon
 * rather than introducing a second chart language, scoped to that day's own
 * 24 hours — which is also why the ribbon was written to take an arbitrary
 * window rather than always starting from now.
 */
@Composable
fun DayDetailScreen(
    bundle: ForecastBundle?,
    dayEpoch: Long,
    formatter: Formatter,
    copy: Copy,
    nowEpoch: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val day = bundle?.days?.firstOrNull { it.epochSeconds == dayEpoch }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SText(
                    text = formatter.dayLabel(dayEpoch, nowEpoch),
                    style = Sereno.type.title,
                )
                SText(
                    text = formatter.dayMonth(dayEpoch),
                    style = Sereno.type.caption,
                    emphasis = Emphasis.tertiary,
                )
            }
            IconAction(Glyph.Close, copy.close, onClose)
        }

        if (bundle == null || day == null) {
            MessageState(
                title = copy.errorTitle,
                body = copy.errorBody,
                glyph = Glyph.Info,
            )
            return@Column
        }

        val dayHours = remember(bundle, dayEpoch) {
            bundle.hours.filter { it.epochSeconds in dayEpoch until (dayEpoch + 24 * 3600) }
        }

        Spacer(Modifier.height(Space.blockGap))

        // Hero: the day's range, with its condition.
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SText(
                    text = formatter.temperature(day.temperatureMax),
                    style = Sereno.type.displaySmall,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                SText(
                    text = "${copy.low} ${formatter.temperature(day.temperatureMin)}",
                    style = Sereno.type.data,
                    emphasis = Emphasis.tertiary,
                )
                Spacer(Modifier.height(Space.sm))
                SText(
                    text = copy.conditionName(
                        WeatherCodes.condition(day.weatherCode),
                        WeatherCodes.intensity(day.weatherCode),
                    ),
                    style = Sereno.type.headline,
                    emphasis = Emphasis.secondary,
                )
            }
            WeatherGlyph(
                code = day.weatherCode,
                isDay = true,
                size = 52.dp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        val narrative = remember(dayHours) {
            Narrator.dayNarrative(dayHours, bundle.utcOffsetSeconds, copy)
        }
        if (narrative != null) {
            GapRow()
            SText(narrative, style = Sereno.type.narrative, emphasis = Emphasis.secondary)
        }

        if (dayHours.size >= 2) {
            GapSection()
            HourlyDetail(dayHours, bundle, formatter, copy, day.sunriseEpoch, day.sunsetEpoch)
        }

        if (day.sunriseEpoch != null && day.sunsetEpoch != null) {
            GapSection()
            SectionLabel(copy.t("Luce", "Daylight"))
            Spacer(Modifier.height(Space.lg))
            DaylightArc(
                sunrise = day.sunriseEpoch,
                sunset = day.sunsetEpoch,
                nowEpoch = nowEpoch,
                formatter = formatter,
                copy = copy,
            )
        }

        GapSection()
        SectionLabel(copy.t("Dettagli", "Details"))
        Spacer(Modifier.height(Space.sm))

        ConditionRow(
            copy.precipitation,
            formatter.precipitation(day.precipitationSum),
            detail = day.precipitationProbabilityMax?.let { "${formatter.percent(it)} ${copy.probability.lowercase()}" },
        )
        SectionRule()
        ConditionRow(
            copy.wind,
            formatter.speed(day.windSpeedMax),
            detail = formatter.windDirection(day.windDirectionDominant),
            mark = { WindArrow(day.windDirectionDominant) },
        )
        SectionRule()
        ConditionRow(copy.gusts, formatter.speed(day.windGustMax))
        SectionRule()
        ConditionRow(
            copy.uvIndex,
            formatter.uv(day.uvIndexMax),
            detail = formatter.uvDescription(day.uvIndexMax),
            mark = { UvMark(day.uvIndexMax) },
        )
        if ((day.snowfallSum ?: 0.0) > 0.1) {
            SectionRule()
            ConditionRow(copy.snow, "${formatter.precipitation(day.snowfallSum, withUnit = false)} cm")
        }
        SectionRule()
        ConditionRow(
            copy.humidity,
            formatter.percent(dayHours.mapNotNull { it.humidity }.averageOrNull()),
        )
        SectionRule()
        ConditionRow(
            copy.cloudCover,
            formatter.percent(dayHours.mapNotNull { it.cloudCover }.averageOrNull()),
        )

        GapSection()
        ConfidenceMeter(
            confidence = day.confidence,
            copy = copy,
            explanation = copy.t(
                "Calcolata sull'accordo tra ${day.contributors.size} modelli per questa giornata.",
                "Computed from the agreement of ${day.contributors.size} models for this day.",
            ),
        )

        Spacer(Modifier.height(Space.railClearance))
    }
}

@Composable
private fun HourlyDetail(
    dayHours: List<BlendedHour>,
    bundle: ForecastBundle,
    formatter: Formatter,
    copy: Copy,
    sunrise: Long?,
    sunset: Long?,
) {
    var scrubbed by remember { mutableStateOf<BlendedHour?>(null) }
    val heading = scrubbed?.let {
        "${formatter.time(it.epochSeconds)}  ·  ${formatter.temperature(it.temperature)}"
    } ?: copy.t("Ora per ora", "Hour by hour")

    SectionLabel(heading)
    Spacer(Modifier.height(Space.md))
    HourlyRibbon(
        hours = dayHours,
        nowEpoch = dayHours.first().epochSeconds,
        formatter = formatter,
        sunriseEpoch = sunrise,
        sunsetEpoch = sunset,
        onSelectionChange = { scrubbed = it },
    )
}

/**
 * The daylight arc.
 *
 * A half-ellipse from sunrise to sunset with the sun's current position on it.
 * It conveys the length of the day and how far through it you are in one shape,
 * which two timestamps in a table do not.
 */
@Composable
private fun DaylightArc(
    sunrise: Long,
    sunset: Long,
    nowEpoch: Long,
    formatter: Formatter,
    copy: Copy,
    modifier: Modifier = Modifier,
) {
    val atmosphere = Sereno.atmosphere
    val fraction = ((nowEpoch - sunrise).toDouble() / (sunset - sunrise).toDouble())
        .coerceIn(0.0, 1.0).toFloat()
    val duringDay = nowEpoch in sunrise..sunset

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clearAndSetSemantics { },
        ) {
            val inset = 10.dp.toPx()
            val baseline = size.height - 14.dp.toPx()
            val arcRect = Rect(
                left = inset,
                top = baseline - (size.width - inset * 2) * 0.30f,
                right = size.width - inset,
                bottom = baseline + (size.width - inset * 2) * 0.30f,
            )

            val arc = Path().apply { arcTo(arcRect, 180f, 180f, true) }
            drawPath(
                arc,
                color = atmosphere.ink(Emphasis.hairline),
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                ),
            )

            drawLine(
                color = atmosphere.ink(Emphasis.hairline),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1f,
            )

            if (duringDay) {
                // Parametric point on the same ellipse the arc was drawn from.
                val angle = PI.toFloat() * (1f + fraction)
                val cx = arcRect.center.x + cos(angle) * (arcRect.width / 2f)
                val cy = arcRect.center.y + sin(angle) * (arcRect.height / 2f)
                drawCircle(atmosphere.accent.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = Offset(cx, cy))
                drawCircle(atmosphere.accent, radius = 4.dp.toPx(), center = Offset(cx, cy))
            }

            listOf(arcRect.left, arcRect.right).forEach { x ->
                drawLine(
                    color = atmosphere.ink(Emphasis.quaternary),
                    start = Offset(x, baseline - 4.dp.toPx()),
                    end = Offset(x, baseline + 4.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth()) {
            Column {
                SText(copy.sunrise.uppercase(), style = Sereno.type.label, emphasis = Emphasis.tertiary)
                SText(formatter.time(sunrise), style = Sereno.type.data)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                SText(copy.sunset.uppercase(), style = Sereno.type.label, emphasis = Emphasis.tertiary)
                SText(formatter.time(sunset), style = Sereno.type.data)
            }
        }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
