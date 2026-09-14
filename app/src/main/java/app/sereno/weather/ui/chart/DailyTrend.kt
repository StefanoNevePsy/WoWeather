package app.sereno.weather.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Sereno
import app.sereno.weather.domain.model.BlendedDay
import app.sereno.weather.ui.Formatter

/**
 * The fourteen-day temperature trend.
 *
 * Two curves — daily maximum and daily minimum — with the diurnal range filled
 * between them. What makes it worth having rather than just reading the list is
 * the third dimension: the fill *fades out* as confidence drops, so the far end
 * of the chart is visibly less solid than the near end. The forecast does not
 * pretend to know day 14 as well as it knows tomorrow, and the drawing says so
 * before any number does.
 */
@Composable
fun DailyTrendChart(
    days: List<BlendedDay>,
    formatter: Formatter,
    modifier: Modifier = Modifier,
    selectedEpoch: Long? = null,
    onSelect: (BlendedDay) -> Unit = {},
) {
    val atmosphere = Sereno.atmosphere
    val data = Sereno.data
    val motion = Sereno.motion
    val type = Sereno.type
    val measurer = rememberTextMeasurer()
    val inspecting = LocalInspectionMode.current

    if (days.size < 2) return

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(days) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared || inspecting || motion.reduceMotion) 1f else 0f,
        animationSpec = motion.draw(),
        label = "trendDraw",
    )

    val maxima = days.mapNotNull { it.temperatureMax }
    val minima = days.mapNotNull { it.temperatureMin }
    if (maxima.isEmpty() || minima.isEmpty()) return
    val range = remember(days) { Curves.niceRange(maxima + minima, step = 5.0, padding = 1.0) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT.dp)
            .clearAndSetSemantics { },
    ) {
        val top = 14.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val step = size.width / days.size
        fun centreX(index: Int) = step * (index + 0.5f)

        val maxPoints = days.mapIndexedNotNull { index, day ->
            day.temperatureMax?.let {
                Offset(centreX(index), Curves.project(it, range.start, range.endInclusive, top, bottom))
            }
        }
        val minPoints = days.mapIndexedNotNull { index, day ->
            day.temperatureMin?.let {
                Offset(centreX(index), Curves.project(it, range.start, range.endInclusive, top, bottom))
            }
        }
        if (maxPoints.size < 2 || minPoints.size < 2) return@Canvas

        // The diurnal band, drawn per day so each segment can carry its own
        // confidence as opacity.
        days.forEachIndexed { index, day ->
            val upper = maxPoints.getOrNull(index) ?: return@forEachIndexed
            val lower = minPoints.getOrNull(index) ?: return@forEachIndexed
            val alpha = (0.06f + day.confidence.score * 0.16f) * progress
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        data.warm.copy(alpha = alpha),
                        data.cool.copy(alpha = alpha * 0.8f),
                    ),
                    startY = upper.y,
                    endY = lower.y,
                ),
                topLeft = Offset(centreX(index) - step / 2f, upper.y),
                size = androidx.compose.ui.geometry.Size(step, (lower.y - upper.y).coerceAtLeast(1f)),
            )
        }

        // The two curves. The minimum is dashed, so the pair is distinguishable
        // without relying on colour alone.
        drawPath(
            Curves.smooth(maxPoints),
            brush = Brush.horizontalGradient(maxima.map { data.forTemperature(it) }),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            Curves.smooth(minPoints),
            brush = Brush.horizontalGradient(minima.map { data.forTemperature(it) }),
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
            ),
        )

        // Selection marker.
        if (selectedEpoch != null) {
            val index = days.indexOfFirst { it.epochSeconds == selectedEpoch }
            if (index >= 0) {
                drawLine(
                    color = atmosphere.ink(Emphasis.quaternary),
                    start = Offset(centreX(index), top - 8.dp.toPx()),
                    end = Offset(centreX(index), bottom + 4.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }

        // Day initials along the bottom.
        val tickStyle = type.tick.copy(color = atmosphere.ink(Emphasis.quaternary))
        days.forEachIndexed { index, day ->
            if (days.size > 10 && index % 2 != 0) return@forEachIndexed
            val label = formatter.weekdayShort(day.epochSeconds).take(2)
            val measured = measurer.measure(label, tickStyle)
            drawText(
                textMeasurer = measurer,
                text = label,
                topLeft = Offset(centreX(index) - measured.size.width / 2f, size.height - 16.dp.toPx()),
                style = tickStyle,
            )
        }
    }
}

private const val CHART_HEIGHT = 148f
