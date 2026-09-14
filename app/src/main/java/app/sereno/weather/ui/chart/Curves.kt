package app.sereno.weather.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Curve construction shared by every chart in the app.
 *
 * All of Sereno's series are smoothed the same way, which matters more than it
 * sounds: if the hourly ribbon and the model-comparison chart curve differently,
 * the same data looks like two different forecasts.
 */
object Curves {

    /**
     * A smooth path through every point, via Catmull-Rom converted to cubic
     * Béziers.
     *
     * Catmull-Rom is the right choice here because it *interpolates* — the curve
     * passes exactly through each data point. A plain Bézier smoothing that only
     * approximates the points would quietly redraw a 3 mm hour as 2 mm, which in
     * a weather app is not a cosmetic difference.
     *
     * [tension] 0 gives the standard uniform spline; higher values flatten it.
     * 0.12 takes the edge off the overshoot that Catmull-Rom produces at sharp
     * reversals without making the curve look mechanical.
     */
    fun smooth(points: List<Offset>, tension: Float = 0.12f): Path {
        val path = Path()
        appendSmooth(path, points, tension, startWithMove = true)
        return path
    }

    /**
     * Appends a smoothed run of points to an existing path.
     *
     * Kept separate from [smooth] so a band can run out along one series and
     * back along another inside a *single* contour. Building it from two paths
     * and `addPath` does not work: `addPath` begins a new subpath, which leaves
     * the fill open and produces a stray wedge across the chart.
     */
    private fun appendSmooth(path: Path, points: List<Offset>, tension: Float, startWithMove: Boolean) {
        if (points.isEmpty()) return
        if (startWithMove) path.moveTo(points[0].x, points[0].y) else path.lineTo(points[0].x, points[0].y)
        if (points.size == 1) return

        val scale = (1f - tension) / 6f
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

            path.cubicTo(
                p1.x + (p2.x - p0.x) * scale,
                p1.y + (p2.y - p0.y) * scale,
                p2.x - (p3.x - p1.x) * scale,
                p2.y - (p3.y - p1.y) * scale,
                p2.x,
                p2.y,
            )
        }
    }

    /** The same curve, closed down to [baseline] so it can be filled. */
    fun smoothArea(points: List<Offset>, baseline: Float, tension: Float = 0.12f): Path {
        if (points.isEmpty()) return Path()
        val path = smooth(points, tension)
        path.lineTo(points.last().x, baseline)
        path.lineTo(points.first().x, baseline)
        path.close()
        return path
    }

    /**
     * A band between two series, for uncertainty envelopes.
     *
     * The upper edge runs forwards and the lower edge runs back, so the result
     * is one closed contour that fills cleanly without a seam.
     */
    fun band(upper: List<Offset>, lower: List<Offset>, tension: Float = 0.12f): Path {
        if (upper.isEmpty() || lower.isEmpty()) return Path()
        val path = Path()
        appendSmooth(path, upper, tension, startWithMove = true)
        appendSmooth(path, lower.reversed(), tension, startWithMove = false)
        path.close()
        return path
    }

    /** Maps a value onto a pixel position, guarding against a zero-height range. */
    fun project(value: Double, min: Double, max: Double, top: Float, bottom: Float): Float {
        if (max - min < 1e-6) return (top + bottom) / 2f
        val fraction = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
        return bottom - fraction * (bottom - top)
    }

    /**
     * Picks a rounded axis range that contains the data.
     *
     * Charts that snap their axis to the exact data minimum make every day look
     * equally variable; rounding to a sensible step and keeping a little
     * headroom preserves the difference between a flat day and a dramatic one.
     */
    fun niceRange(values: List<Double>, step: Double = 2.0, padding: Double = 1.0): ClosedFloatingPointRange<Double> {
        if (values.isEmpty()) return 0.0..1.0
        val rawMin = values.min() - padding
        val rawMax = values.max() + padding
        val min = kotlin.math.floor(rawMin / step) * step
        val max = kotlin.math.ceil(rawMax / step) * step
        return if (max - min < step) min..(min + step) else min..max
    }
}
