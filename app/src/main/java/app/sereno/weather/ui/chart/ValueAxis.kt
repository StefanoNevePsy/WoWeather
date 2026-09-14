package app.sereno.weather.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/** One horizontal reference level: where it sits, and what it reads. */
internal data class AxisTick(val y: Float, val label: String)

/**
 * The value axis shared by every chart.
 *
 * Two decisions keep it from costing anything visually:
 *
 * - **Labels sit inside the plot**, tucked just above their own gridline at the
 *   left edge, rather than in a gutter. A gutter would have taken 30dp of width
 *   off charts that are only ~370dp wide to begin with, and on a phone that
 *   width is worth more than the tidiness of a column of right-aligned numbers.
 * - **The gridlines are barely there** — a few percent of ink. They are meant to
 *   let you read a value off the curve, not to draw a grid.
 *
 * A tick whose label would be clipped off the top of the plot is drawn below
 * its line instead, so the top of the range is always legible.
 */
internal fun DrawScope.drawValueAxis(
    ticks: List<AxisTick>,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    gridColor: Color,
    left: Float = 0f,
    right: Float = size.width,
    labelGap: Float = 3f,
    labelBelow: Boolean = false,
) {
    ticks.forEach { tick ->
        drawLine(
            color = gridColor,
            start = Offset(left, tick.y),
            end = Offset(right, tick.y),
            strokeWidth = 1f,
        )
        if (tick.label.isBlank()) return@forEach

        val measured = measurer.measure(tick.label, labelStyle)
        val above = tick.y - measured.size.height - labelGap
        val y = if (labelBelow || above < 0f) tick.y + labelGap else above
        drawText(
            textMeasurer = measurer,
            text = tick.label,
            topLeft = Offset(left, y),
            style = labelStyle,
        )
    }
}

/**
 * Three levels across a range: bottom, middle, top.
 *
 * The middle one is often left unlabelled. On charts whose curves sweep the
 * full height — the model comparison, the fortnight trend — a label at
 * mid-height lands underneath the data and becomes unreadable. Where the data
 * occupies a narrow band, as on the hourly ribbon, the middle label is clear
 * and worth having. The gridline is drawn either way.
 */
internal fun axisTicks(
    min: Double,
    max: Double,
    top: Float,
    bottom: Float,
    labelMiddle: Boolean = true,
    label: (Double) -> String,
): List<AxisTick> {
    if (max - min < 1e-6) return emptyList()
    val middle = (min + max) / 2.0
    return listOf(max to true, middle to labelMiddle, min to true).map { (value, labelled) ->
        AxisTick(
            y = Curves.project(value, min, max, top, bottom),
            label = if (labelled) label(value) else "",
        )
    }
}
