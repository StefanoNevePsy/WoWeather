package app.sereno.weather.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.drawWeatherGlyph
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.ui.Formatter
import kotlin.math.roundToInt

/**
 * The hourly ribbon: Sereno's signature chart.
 *
 * It deliberately is not a row of icons with temperatures under them. Weather
 * over a day is a *continuous* thing, and four quantities — temperature,
 * precipitation amount, precipitation likelihood and daylight — only make sense
 * read against each other. So they share one frame:
 *
 *  - temperature is the curve, on its own auto-ranged scale;
 *  - precipitation hangs below it as bars;
 *  - likelihood is the soft area behind those bars;
 *  - night is a wash across the full height;
 *  - sunrise and sunset are ticks on the time axis.
 *
 * Twenty-four hours are fitted to the viewport rather than scrolled. That is a
 * deliberate trade: it gives up the ability to see further ahead in exchange for
 * a drag gesture that means exactly one thing — scrub — with no ambiguity
 * against panning, and for a shape the eye can take in whole.
 */
@Composable
fun HourlyRibbon(
    hours: List<BlendedHour>,
    nowEpoch: Long,
    formatter: Formatter,
    modifier: Modifier = Modifier,
    sunriseEpoch: Long? = null,
    sunsetEpoch: Long? = null,
    onSelectionChange: (BlendedHour?) -> Unit = {},
) {
    val atmosphere = Sereno.atmosphere
    val data = Sereno.data
    val motion = Sereno.motion
    val type = Sereno.type
    val measurer = rememberTextMeasurer()
    val inspecting = LocalInspectionMode.current

    val window = remember(hours, nowEpoch) {
        hours.filter { it.epochSeconds >= nowEpoch - 3600 }.take(24)
    }

    var selectedIndex by remember(window) { mutableIntStateOf(-1) }
    var chartWidth by remember { mutableStateOf(0f) }

    // Progressive draw-in. The curve writes itself once, on arrival; it never
    // replays on recomposition, which would turn a nice touch into a tic.
    var drawnOnce by remember { mutableStateOf(false) }
    LaunchedEffect(window) { drawnOnce = true }
    val progress by animateFloatAsState(
        targetValue = if (drawnOnce || inspecting || motion.reduceMotion) 1f else 0f,
        animationSpec = motion.draw(),
        label = "ribbonDraw",
    )

    LaunchedEffect(selectedIndex) {
        onSelectionChange(window.getOrNull(selectedIndex))
    }

    if (window.size < 2) {
        Box(modifier.fillMaxWidth().height(RIBBON_HEIGHT.dp))
        return
    }

    val temperatures = window.mapNotNull { it.temperature }
    val range = remember(window) { Curves.niceRange(temperatures, step = 2.0, padding = 1.2) }
    val maxPrecip = remember(window) {
        // Always allow at least 2 mm of scale so a drizzle does not render as a
        // downpour just because it is the wettest hour on screen.
        maxOf(2.0, window.maxOf { it.precipitation ?: 0.0 })
    }

    // A dry day has nothing to put in the precipitation band, and an empty
    // band leaves a hole under the curve. The chart simply gets shorter.
    val dry = remember(window) {
        window.none { (it.precipitation ?: 0.0) >= BlendedHour.WET_THRESHOLD_MM }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (dry) DRY_HEIGHT.dp else RIBBON_HEIGHT.dp)
            .pointerInput(window) {
                detectTapGestures { offset ->
                    selectedIndex = indexFor(offset.x, size.width.toFloat(), window.size)
                }
            }
            .pointerInput(window) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { selectedIndex = -1 },
                    onDragCancel = { selectedIndex = -1 },
                    onDragStart = { offset ->
                        selectedIndex = indexFor(offset.x, size.width.toFloat(), window.size)
                    },
                ) { change, _ ->
                    selectedIndex = indexFor(change.position.x, size.width.toFloat(), window.size)
                }
            },
    ) {
        chartWidth = size.width
        val step = size.width / window.size
        fun centreX(index: Int) = step * (index + 0.5f)

        val tempTop = TEMP_TOP.dp.toPx()
        val tempBottom = (if (dry) DRY_TEMP_BOTTOM else TEMP_BOTTOM).dp.toPx()
        val precipTop = PRECIP_TOP.dp.toPx()
        val precipBottom = (if (dry) DRY_BASELINE else PRECIP_BOTTOM).dp.toPx()
        val axisY = (if (dry) DRY_AXIS_Y else AXIS_Y).dp.toPx()

        // 1. Night. One wash per run of dark hours rather than a per-hour
        //    stripe, faded at both ends so dusk reads as a transition instead
        //    of a hard-edged selection box.
        drawNightBands(window, step, size.width, precipBottom, atmosphere.ink(0.05f))

        // 2. Likelihood of precipitation, behind the amounts.
        val probabilityPoints = window.mapIndexed { index, hour ->
            Offset(centreX(index), Curves.project(hour.precipitationProbability ?: 0.0, 0.0, 100.0, precipTop, precipBottom))
        }
        drawPath(
            Curves.smoothArea(probabilityPoints, precipBottom),
            brush = Brush.verticalGradient(
                0f to data.probability.copy(alpha = 0.20f),
                1f to data.probability.copy(alpha = 0.02f),
                startY = precipTop,
                endY = precipBottom,
            ),
        )

        // 3. Precipitation amount.
        @Suppress("NAME_SHADOWING")
        val barWidth = (step * 0.36f).coerceAtMost(9.dp.toPx())
        window.forEachIndexed { index, hour ->
            val mm = hour.precipitation ?: 0.0
            if (mm < BlendedHour.WET_THRESHOLD_MM) return@forEachIndexed
            val top = Curves.project(mm, 0.0, maxPrecip, precipTop, precipBottom)
            val height = (precipBottom - top) * progress
            if (height <= 0.5f) return@forEachIndexed
            val isSnow = (hour.snowfall ?: 0.0) > 0.05
            drawRoundRect(
                color = if (isSnow) data.cold.copy(alpha = 0.75f) else data.precip.copy(alpha = 0.82f),
                topLeft = Offset(centreX(index) - barWidth / 2f, precipBottom - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2.6f),
            )
        }

        // 4. Baseline under the precipitation band.
        drawLine(
            color = atmosphere.ink(Emphasis.hairline),
            start = Offset(0f, precipBottom),
            end = Offset(size.width, precipBottom),
            strokeWidth = 1f,
        )

        // 5. Temperature.
        val tempPoints = window.mapIndexedNotNull { index, hour ->
            hour.temperature?.let {
                Offset(centreX(index), Curves.project(it, range.start, range.endInclusive, tempTop, tempBottom))
            }
        }
        if (tempPoints.size >= 2) {
            val curve = Curves.smooth(tempPoints)

            // A whisper of fill: enough to tie the curve to the band below it,
            // not enough to become an area chart.
            drawPath(
                Curves.smoothArea(tempPoints, tempBottom + 6.dp.toPx()),
                brush = Brush.verticalGradient(
                    0f to atmosphere.ink(0.09f),
                    1f to Color.Transparent,
                    startY = tempTop,
                    endY = tempBottom + 6.dp.toPx(),
                ),
            )

            drawPath(
                path = partialPath(curve, progress),
                brush = Brush.horizontalGradient(
                    window.mapNotNull { it.temperature }.map { data.forTemperature(it) },
                ),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 6. Condition marks every six hours: enough to orient, never a row of
        //    icons competing with the curve.
        window.forEachIndexed { index, hour ->
            if (index % 6 != 2) return@forEachIndexed
            drawWeatherGlyph(
                code = hour.weatherCode,
                isDay = hour.isDay,
                center = Offset(centreX(index), GLYPH_Y.dp.toPx()),
                sizePx = 17.dp.toPx(),
                ink = atmosphere.ink(Emphasis.secondary * progress),
                accent = atmosphere.accent.copy(alpha = progress),
            )
        }

        // 7. Sunrise and sunset as ticks on the axis.
        listOfNotNull(sunriseEpoch, sunsetEpoch).forEach { epoch ->
            val x = positionForEpoch(epoch, window, step) ?: return@forEach
            drawLine(
                color = atmosphere.accent.copy(alpha = 0.5f),
                start = Offset(x, precipBottom - 4.dp.toPx()),
                end = Offset(x, precipBottom + 4.dp.toPx()),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 8. Now.
        positionForEpoch(nowEpoch, window, step)?.let { x ->
            drawLine(
                color = atmosphere.ink(Emphasis.quaternary),
                start = Offset(x, tempTop - 10.dp.toPx()),
                end = Offset(x, precipBottom),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)),
            )
        }

        // 9. Hour labels.
        val tickStyle = type.tick.copy(color = atmosphere.ink(Emphasis.tertiary))
        window.forEachIndexed { index, hour ->
            if (index % 3 != 0) return@forEachIndexed
            val label = formatter.hourShort(hour.epochSeconds)
            val measured = measurer.measure(label, tickStyle)
            drawText(
                textMeasurer = measurer,
                text = label,
                topLeft = Offset(centreX(index) - measured.size.width / 2f, axisY),
                style = tickStyle,
            )
        }

        // 10. The scrub readout, on top of everything.
        val selected = window.getOrNull(selectedIndex)
        if (selected != null) {
            val x = centreX(selectedIndex)
            drawLine(
                color = atmosphere.ink(Emphasis.tertiary),
                start = Offset(x, tempTop - 14.dp.toPx()),
                end = Offset(x, precipBottom),
                strokeWidth = 1.2.dp.toPx(),
            )
            selected.temperature?.let { value ->
                val y = Curves.project(value, range.start, range.endInclusive, tempTop, tempBottom)
                drawCircle(atmosphere.skyMid, radius = 5.2.dp.toPx(), center = Offset(x, y))
                drawCircle(data.forTemperature(value), radius = 3.4.dp.toPx(), center = Offset(x, y))

                val label = formatter.temperature(value)
                val labelStyle = type.dataSmall.copy(color = atmosphere.ink)
                val measured = measurer.measure(label, labelStyle)
                val labelX = (x - measured.size.width / 2f)
                    .coerceIn(2.dp.toPx(), size.width - measured.size.width - 2.dp.toPx())
                drawText(
                    textMeasurer = measurer,
                    text = label,
                    topLeft = Offset(labelX, y - measured.size.height - 11.dp.toPx()),
                    style = labelStyle,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------

private const val RIBBON_HEIGHT = 208f
private const val GLYPH_Y = 20f
private const val TEMP_TOP = 44f
private const val TEMP_BOTTOM = 116f
private const val PRECIP_TOP = 132f
private const val PRECIP_BOTTOM = 176f
private const val AXIS_Y = 186f

// The shorter layout used when nothing falls in the whole window.
private const val DRY_HEIGHT = 158f
private const val DRY_TEMP_BOTTOM = 120f
private const val DRY_BASELINE = 128f
private const val DRY_AXIS_Y = 138f

private fun indexFor(x: Float, width: Float, count: Int): Int {
    if (width <= 0f || count == 0) return -1
    val step = width / count
    return (x / step).toInt().coerceIn(0, count - 1)
}

private fun positionForEpoch(epoch: Long, window: List<BlendedHour>, step: Float): Float? {
    val first = window.firstOrNull()?.epochSeconds ?: return null
    val hoursIn = (epoch - first) / 3600.0
    if (hoursIn < 0 || hoursIn > window.size) return null
    return (step * (hoursIn + 0.5f)).toFloat()
}

/**
 * The wash that marks night hours.
 *
 * Contiguous dark hours are merged into one band, and each band fades in and
 * out horizontally across roughly an hour at each end. Hard edges made this
 * read as a highlighted selection rather than as nightfall.
 */
private fun DrawScope.drawNightBands(
    window: List<BlendedHour>,
    step: Float,
    width: Float,
    bottom: Float,
    color: Color,
) {
    var runStart = -1
    window.forEachIndexed { index, hour ->
        val night = !hour.isDay
        if (night && runStart < 0) runStart = index
        val ends = !night || index == window.lastIndex
        if (ends && runStart >= 0) {
            val endIndex = if (night) index + 1 else index
            val left = step * runStart
            val bandWidth = step * (endIndex - runStart)
            if (bandWidth > 0f) {
                val fade = (step * 1.1f / bandWidth).coerceAtMost(0.35f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        fade to color,
                        (1f - fade) to color,
                        1f to Color.Transparent,
                        startX = left,
                        endX = left + bandWidth,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(bandWidth, bottom),
                )
            }
            runStart = -1
        }
    }
}

/**
 * The first [fraction] of a path, for the draw-in animation.
 *
 * Measured rather than interpolated, so the curve reveals at a constant speed
 * along its own length instead of racing through the flat parts.
 */
private fun partialPath(path: Path, fraction: Float): Path {
    if (fraction >= 1f) return path
    if (fraction <= 0f) return Path()
    val measure = PathMeasure().apply { setPath(path, false) }
    val destination = Path()
    measure.getSegment(0f, measure.length * fraction, destination, true)
    return destination
}

