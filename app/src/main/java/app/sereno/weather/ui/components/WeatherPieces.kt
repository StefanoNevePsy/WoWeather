package app.sereno.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.Rule
import app.sereno.weather.design.SGlyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.design.Stroke
import app.sereno.weather.design.WeatherGlyph
import app.sereno.weather.domain.model.AlertSeverity
import app.sereno.weather.domain.model.BlendedDay
import app.sereno.weather.domain.model.Nowcast
import app.sereno.weather.domain.model.NowcastKind
import app.sereno.weather.domain.model.WeatherAlert
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.Formatter

/**
 * The nowcast line: the answer to "is it about to rain?".
 *
 * This sits directly under the hero because for daily use it is the single most
 * valuable sentence in the app. It is phrased as a range and never as an exact
 * minute — the underlying data cannot support a claim like "in 47 minutes", and
 * pretending otherwise is how a weather app loses trust in one afternoon.
 */
@Composable
fun NowcastLine(
    nowcast: Nowcast?,
    copy: Copy,
    modifier: Modifier = Modifier,
) {
    if (nowcast == null || nowcast.kind == NowcastKind.Unknown) return
    val data = Sereno.data
    val type = Sereno.type

    val text = when (nowcast.kind) {
        NowcastKind.Dry -> copy.noRainFor(nowcast.horizonHours)
        NowcastKind.StartingSoon -> copy.rainStartingIn(
            nowcast.startMinutesLow ?: 0,
            nowcast.startMinutesHigh ?: 0,
            nowcast.isSnow,
        )
        NowcastKind.Stopping -> copy.rainStoppingIn(
            nowcast.endMinutesLow ?: 0,
            nowcast.endMinutesHigh ?: 0,
        )
        NowcastKind.Ongoing -> if (nowcast.isSnow) copy.snowingNow else copy.rainingNow
        NowcastKind.Intermittent -> copy.intermittentFor(nowcast.horizonHours)
        NowcastKind.Unknown -> return
    }

    val wet = nowcast.kind != NowcastKind.Dry
    val accent = if (wet) data.precip else Sereno.atmosphere.ink

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SGlyph(
            glyph = if (wet) Glyph.Droplet else Glyph.Check,
            size = 16.dp,
            tint = accent,
            emphasis = if (wet) 1f else Emphasis.tertiary,
        )
        Spacer(Modifier.width(Space.md))
        SText(
            text = text,
            style = type.bodyStrong,
            emphasis = if (wet) Emphasis.primary else Emphasis.secondary,
        )
    }
}

/**
 * A weather warning.
 *
 * Severity is carried by a coloured rule down the left edge and by the word
 * itself, not by a filled panel. A block of alarm colour makes an Advisory look
 * like a catastrophe, and the brief was explicit about avoiding alarmism.
 */
@Composable
fun AlertRow(
    alert: WeatherAlert,
    copy: Copy,
    formatter: Formatter,
    modifier: Modifier = Modifier,
) {
    val data = Sereno.data
    val color = when (alert.severity) {
        AlertSeverity.Advisory -> data.caution
        AlertSeverity.Watch -> data.caution
        AlertSeverity.Warning -> data.warning
        AlertSeverity.Severe -> data.severe
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "${copy.severity(alert.severity)}. ${alert.headline}. ${alert.detail}"
            },
    ) {
        Box(
            Modifier
                .width(2.dp)
                .heightIn(min = 44.dp)
                .background(color.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SText(
                    text = copy.severity(alert.severity).uppercase(),
                    style = Sereno.type.label,
                    color = color,
                )
                Spacer(Modifier.width(Space.sm))
                SText(
                    text = alert.headline,
                    style = Sereno.type.label,
                    emphasis = Emphasis.tertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            SText(alert.detail, style = Sereno.type.body, emphasis = Emphasis.secondary)
            if (alert.derived) {
                Spacer(Modifier.height(2.dp))
                SText(
                    text = copy.t(
                        "Derivata dai modelli · non è un'allerta ufficiale",
                        "Derived from the models · not an official warning",
                    ),
                    style = Sereno.type.caption,
                    emphasis = Emphasis.quaternary,
                )
            }
        }
    }
}

/**
 * One day in the forecast list.
 *
 * The temperature bar is the part that earns its place: each day's range is
 * drawn against the *whole period's* range, so a glance down the column shows
 * which days are the warm ones without reading a single number.
 */
@Composable
fun DailyRow(
    day: BlendedDay,
    label: String,
    formatter: Formatter,
    copy: Copy,
    periodMin: Double,
    periodMax: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showConfidence: Boolean = true,
) {
    val type = Sereno.type
    val data = Sereno.data
    val probability = day.precipitationProbabilityMax

    val description = buildString {
        append(label)
        append(", ")
        append(copy.conditionName(app.sereno.weather.domain.model.WeatherCodes.condition(day.weatherCode)))
        append(", ")
        append(copy.high); append(" "); append(formatter.temperature(day.temperatureMax))
        append(", ")
        append(copy.low); append(" "); append(formatter.temperature(day.temperatureMin))
    }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(94.dp)) {
                SText(label, style = type.body, maxLines = 1)
                if (probability != null && probability >= 15) {
                    Spacer(Modifier.height(1.dp))
                    SText(
                        formatter.percent(probability),
                        style = type.dataSmall,
                        color = data.probability,
                        emphasis = 0.9f,
                    )
                }
            }

            WeatherGlyph(
                code = day.weatherCode,
                isDay = true,
                size = 23.dp,
                animated = false,
            )

            Spacer(Modifier.width(Space.md))

            SText(
                formatter.degrees(day.temperatureMin),
                style = type.data,
                emphasis = Emphasis.tertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(26.dp),
            )
            Spacer(Modifier.width(Space.sm))
            TemperatureRangeBar(
                dayMin = day.temperatureMin,
                dayMax = day.temperatureMax,
                periodMin = periodMin,
                periodMax = periodMax,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.sm))
            SText(
                formatter.degrees(day.temperatureMax),
                style = type.data,
                textAlign = TextAlign.End,
                modifier = Modifier.width(26.dp),
            )

            if (showConfidence) {
                Spacer(Modifier.width(Space.md))
                ConfidenceDot(day.confidence)
            }
        }
    }

    if (onClick != null) {
        Pressable(onClick = onClick, modifier = modifier.fillMaxWidth(), pressScale = 0.995f) { content() }
    } else {
        Box(modifier.fillMaxWidth()) { content() }
    }
}

/** The min-to-max segment, positioned within the whole period's range. */
@Composable
fun TemperatureRangeBar(
    dayMin: Double?,
    dayMax: Double?,
    periodMin: Double,
    periodMax: Double,
    modifier: Modifier = Modifier,
) {
    val atmosphere = Sereno.atmosphere
    val data = Sereno.data
    if (dayMin == null || dayMax == null) {
        Box(modifier.height(3.dp))
        return
    }
    val span = (periodMax - periodMin).coerceAtLeast(1.0)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clearAndSetSemantics { },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = atmosphere.ink(Emphasis.hairline),
            size = size,
            cornerRadius = radius,
        )
        val startFraction = ((dayMin - periodMin) / span).toFloat().coerceIn(0f, 1f)
        val endFraction = ((dayMax - periodMin) / span).toFloat().coerceIn(0f, 1f)
        val left = startFraction * size.width
        // Keep a visible minimum so a day with no diurnal range is still drawn.
        val width = ((endFraction - startFraction) * size.width).coerceAtLeast(size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(data.forTemperature(dayMin), data.forTemperature(dayMax)),
                startX = left,
                endX = left + width,
            ),
            topLeft = Offset(left.coerceAtMost(size.width - width), 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}

/**
 * A conditions row with an optional inline mark.
 *
 * The marks (a wind arrow, a UV scale, an air-quality dot) are what keep this
 * from being a plain table: each row carries one small piece of visual data
 * that the number alone does not convey.
 */
@Composable
fun ConditionRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    mark: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { contentDescription = "$label: $value${detail?.let { ", $it" } ?: ""}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SText(label, style = Sereno.type.body, emphasis = Emphasis.secondary)
        Spacer(Modifier.weight(1f))
        if (detail != null) {
            SText(detail, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
            Spacer(Modifier.width(Space.md))
        }
        if (mark != null) {
            mark()
            Spacer(Modifier.width(Space.md))
        }
        SText(value, style = Sereno.type.data)
    }
}

/** A wind arrow pointing the way the wind is going. */
@Composable
fun WindArrow(directionDegrees: Double?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val atmosphere = Sereno.atmosphere
    if (directionDegrees == null) return
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        // Meteorological convention: the value is the direction the wind comes
        // *from*, so the arrow is drawn pointing the opposite way.
        val radians = Math.toRadians(directionDegrees + 180.0).toFloat()
        val radius = this.size.minDimension / 2f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val tip = Offset(
            centre.x + kotlin.math.sin(radians) * radius,
            centre.y - kotlin.math.cos(radians) * radius,
        )
        val tail = Offset(
            centre.x - kotlin.math.sin(radians) * radius,
            centre.y + kotlin.math.cos(radians) * radius,
        )
        drawLine(
            color = atmosphere.ink(Emphasis.secondary),
            start = tail, end = tip,
            strokeWidth = 1.4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val headAngleLeft = radians + 2.6f
        val headAngleRight = radians - 2.6f
        val headLength = radius * 0.62f
        listOf(headAngleLeft, headAngleRight).forEach { angle ->
            drawLine(
                color = atmosphere.ink(Emphasis.secondary),
                start = tip,
                end = Offset(
                    tip.x + kotlin.math.sin(angle) * headLength,
                    tip.y - kotlin.math.cos(angle) * headLength,
                ),
                strokeWidth = 1.4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** A small UV scale mark: a track with the current value marked on it. */
@Composable
fun UvMark(uv: Double?, modifier: Modifier = Modifier) {
    val atmosphere = Sereno.atmosphere
    val data = Sereno.data
    if (uv == null) return
    Canvas(
        modifier
            .width(38.dp)
            .height(3.dp)
            .clearAndSetSemantics { },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(atmosphere.ink(Emphasis.hairline), size = size, cornerRadius = radius)
        // The scale tops out at 11 because that is where the official index
        // stops being "extreme" and starts being unbounded.
        val fraction = (uv / 11.0).toFloat().coerceIn(0f, 1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(data.positive, data.caution, data.uv, data.severe)),
            size = Size(size.width * fraction, size.height),
            cornerRadius = radius,
        )
    }
}

/** A filled dot, used for air quality bands. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 7.dp) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) { drawCircle(color) }
}

@Composable
fun SectionRule(modifier: Modifier = Modifier) = Rule(modifier, Emphasis.hairline)

@Composable
fun HairlineSpacerHeight() = Spacer(Modifier.height(Stroke.hairline))
