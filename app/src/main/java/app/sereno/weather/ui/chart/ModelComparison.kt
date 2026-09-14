package app.sereno.weather.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Sereno
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.ui.Formatter

/** What the Forecast Lab is plotting. */
enum class LabMetric {
    Temperature, Precipitation, Wind, Cloud;

    fun extract(point: HourPoint): Double? = when (this) {
        Temperature -> point.temperature
        Precipitation -> point.precipitation
        Wind -> point.windSpeed
        Cloud -> point.cloudCover
    }
}

/**
 * Per-model visual identity.
 *
 * Colours come from the Okabe–Ito qualitative palette, which was designed to
 * stay distinguishable under the common forms of colour blindness. They are
 * also paired with distinct dash patterns, because relying on hue alone would
 * make the chart unreadable for some users regardless of how good the palette
 * is — and because on a small phone chart, line texture reads faster than
 * colour anyway.
 */
object ModelStyles {

    fun color(model: WeatherModel, dark: Boolean): Color = when (model) {
        WeatherModel.Icon2I -> if (dark) Color(0xFFF57A33) else Color(0xFFD55E00)
        WeatherModel.AromeHd -> if (dark) Color(0xFFE491BE) else Color(0xFFCC79A7)
        WeatherModel.IconEu -> if (dark) Color(0xFF7CC8F2) else Color(0xFF3E9BD1)
        WeatherModel.IconGlobal -> if (dark) Color(0xFF4A9BD8) else Color(0xFF0072B2)
        WeatherModel.EcmwfIfs -> if (dark) Color(0xFF3FBE98) else Color(0xFF009E73)
        WeatherModel.EcmwfAifs -> if (dark) Color(0xFFF2B846) else Color(0xFFD79200)
        // GFS is intentionally neutral: it is the reference to disagree with.
        WeatherModel.Gfs -> if (dark) Color(0xFF9AA3AD) else Color(0xFF7C858F)
    }

    /** Null means a solid line. */
    fun dash(model: WeatherModel): FloatArray? = when (model) {
        WeatherModel.Icon2I, WeatherModel.AromeHd -> null
        WeatherModel.IconEu -> floatArrayOf(9f, 4f)
        WeatherModel.IconGlobal -> floatArrayOf(5f, 4f)
        WeatherModel.EcmwfIfs -> null
        WeatherModel.EcmwfAifs -> floatArrayOf(12f, 4f, 2f, 4f)
        WeatherModel.Gfs -> floatArrayOf(2f, 4f)
    }

    fun strokeWidth(model: WeatherModel): Float = when (model) {
        WeatherModel.EcmwfIfs, WeatherModel.Icon2I -> 2.3f
        else -> 1.8f
    }
}

/**
 * The model comparison chart.
 *
 * Every available model is drawn as its own line over a shared envelope showing
 * the full spread. The envelope is the point of the whole screen: where it is
 * narrow the forecast is settled, and where it fans out the user can see
 * exactly which model is the outlier and by how much.
 */
@Composable
fun ModelComparisonChart(
    seriesByModel: Map<WeatherModel, List<HourPoint>>,
    metric: LabMetric,
    nowEpoch: Long,
    hoursShown: Int,
    formatter: Formatter,
    modifier: Modifier = Modifier,
    selectedEpoch: Long? = null,
    onSelect: (Long?) -> Unit = {},
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val type = Sereno.type
    val measurer = rememberTextMeasurer()
    val inspecting = LocalInspectionMode.current

    val timeline = remember(seriesByModel, nowEpoch, hoursShown) {
        (0 until hoursShown).map { nowEpoch + it * 3600L }
            .map { (it / 3600) * 3600 }
    }

    val indexed = remember(seriesByModel) {
        seriesByModel.mapValues { (_, points) -> points.associateBy { it.epochSeconds } }
    }

    val allValues = remember(indexed, metric, timeline) {
        indexed.values.flatMap { byTime ->
            timeline.mapNotNull { epoch -> byTime[epoch]?.let { metric.extract(it) } }
        }
    }
    if (allValues.isEmpty()) return

    val range = remember(allValues, metric) {
        when (metric) {
            LabMetric.Temperature -> Curves.niceRange(allValues, step = 2.0, padding = 1.0)
            LabMetric.Cloud -> 0.0..100.0
            LabMetric.Precipitation -> 0.0..maxOf(1.0, allValues.max() * 1.15)
            LabMetric.Wind -> 0.0..maxOf(10.0, allValues.max() * 1.15)
        }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(metric, seriesByModel) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared || inspecting || motion.reduceMotion) 1f else 0f,
        animationSpec = motion.draw(),
        label = "labDraw",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT.dp)
            .clearAndSetSemantics { }
            .pointerInput(timeline) {
                detectTapGestures { offset ->
                    onSelect(epochAt(offset.x, size.width.toFloat(), timeline))
                }
            }
            .pointerInput(timeline) {
                detectDragGestures(
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) },
                ) { change, _ ->
                    onSelect(epochAt(change.position.x, size.width.toFloat(), timeline))
                }
            },
    ) {
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val step = size.width / timeline.size
        fun centreX(index: Int) = step * (index + 0.5f)
        fun y(value: Double) = Curves.project(value, range.start, range.endInclusive, top, bottom)

        // Gridlines: three, quiet, unlabelled except at the extremes.
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val lineY = bottom - (bottom - top) * fraction
            drawLine(
                color = atmosphere.ink(Emphasis.whisper + 0.02f),
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1f,
            )
        }

        // The envelope.
        val upper = mutableListOf<Offset>()
        val lower = mutableListOf<Offset>()
        timeline.forEachIndexed { index, epoch ->
            val values = indexed.values.mapNotNull { byTime -> byTime[epoch]?.let { metric.extract(it) } }
            if (values.isEmpty()) return@forEachIndexed
            upper += Offset(centreX(index), y(values.max()))
            lower += Offset(centreX(index), y(values.min()))
        }
        if (upper.size >= 2) {
            drawPath(
                Curves.band(upper, lower),
                color = atmosphere.ink(0.07f * progress),
            )
        }

        // One line per model.
        indexed.forEach { (model, byTime) ->
            val points = timeline.mapIndexedNotNull { index, epoch ->
                byTime[epoch]?.let { metric.extract(it) }?.let { Offset(centreX(index), y(it)) }
            }
            if (points.size < 2) return@forEach
            val dash = ModelStyles.dash(model)
            drawPath(
                path = Curves.smooth(points),
                color = ModelStyles.color(model, atmosphere.dark).copy(alpha = progress),
                style = Stroke(
                    width = ModelStyles.strokeWidth(model).dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
                ),
            )
        }

        // Selection.
        if (selectedEpoch != null) {
            val index = timeline.indexOf(selectedEpoch)
            if (index >= 0) {
                val x = centreX(index)
                drawLine(
                    color = atmosphere.ink(Emphasis.tertiary),
                    start = Offset(x, top - 6.dp.toPx()),
                    end = Offset(x, bottom),
                    strokeWidth = 1.2.dp.toPx(),
                )
                indexed.forEach { (model, byTime) ->
                    val value = byTime[selectedEpoch]?.let { metric.extract(it) } ?: return@forEach
                    drawCircle(
                        color = atmosphere.skyMid,
                        radius = 4.6.dp.toPx(),
                        center = Offset(x, y(value)),
                    )
                    drawCircle(
                        color = ModelStyles.color(model, atmosphere.dark),
                        radius = 2.9.dp.toPx(),
                        center = Offset(x, y(value)),
                    )
                }
            }
        }

        // Axis: hour labels every six hours, plus a day boundary tick.
        val tickStyle = type.tick.copy(color = atmosphere.ink(Emphasis.quaternary))
        timeline.forEachIndexed { index, epoch ->
            val hour = formatter.hourOfDay(epoch)
            if (hour % 6 != 0) return@forEachIndexed
            val label = if (hour == 0) formatter.weekdayShort(epoch) else formatter.hourShort(epoch)
            val measured = measurer.measure(label, tickStyle)
            drawText(
                textMeasurer = measurer,
                text = label,
                topLeft = Offset(
                    (centreX(index) - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    size.height - 15.dp.toPx(),
                ),
                style = tickStyle,
            )
            if (hour == 0) {
                drawLine(
                    color = atmosphere.ink(Emphasis.hairline),
                    start = Offset(centreX(index) - step / 2f, top),
                    end = Offset(centreX(index) - step / 2f, bottom),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

private const val CHART_HEIGHT = 210f

private fun epochAt(x: Float, width: Float, timeline: List<Long>): Long? {
    if (timeline.isEmpty() || width <= 0f) return null
    val step = width / timeline.size
    val index = (x / step).toInt().coerceIn(0, timeline.lastIndex)
    return timeline[index]
}
