package app.sereno.weather.domain.synthesis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Weighted(val value: Double, val weight: Double)

internal fun List<Weighted>.weightedMean(): Double? {
    val totalWeight = sumOf { it.weight }
    if (totalWeight <= 0.0) return null
    return sumOf { it.value * it.weight } / totalWeight
}

/**
 * Population standard deviation about the weighted mean.
 *
 * Using the weighted mean rather than the plain mean matters here: the spread
 * we report should be the spread around the number we actually showed, not
 * around a number nobody saw.
 */
internal fun List<Weighted>.weightedStdDev(): Double {
    if (size < 2) return 0.0
    val mean = weightedMean() ?: return 0.0
    val totalWeight = sumOf { it.weight }
    if (totalWeight <= 0.0) return 0.0
    val variance = sumOf { it.weight * (it.value - mean) * (it.value - mean) } / totalWeight
    return sqrt(variance.coerceAtLeast(0.0))
}

/** Full range, which is what the spread band in the charts draws. */
internal fun List<Weighted>.range(): ClosedFloatingPointRange<Double>? {
    if (isEmpty()) return null
    val min = minOf { it.value }
    val max = maxOf { it.value }
    return min..max
}

/**
 * Circular (vector) mean for bearings.
 *
 * Averaging 350° and 10° arithmetically gives 180°, i.e. precisely the opposite
 * wind. Directions must always go through the unit circle.
 */
internal fun List<Weighted>.circularMean(): Double? {
    val totalWeight = sumOf { it.weight }
    if (totalWeight <= 0.0) return null
    var x = 0.0
    var y = 0.0
    forEach {
        val rad = Math.toRadians(it.value)
        x += cos(rad) * it.weight
        y += sin(rad) * it.weight
    }
    if (abs(x) < 1e-9 && abs(y) < 1e-9) return null
    val deg = Math.toDegrees(atan2(y, x))
    return (deg + 360.0) % 360.0
}

/** Maps a measured spread onto 0..1, where 1 = models agree. */
internal fun agreementFrom(spread: Double, perfectBelow: Double, uselessAbove: Double): Float {
    if (spread <= perfectBelow) return 1f
    if (spread >= uselessAbove) return 0f
    return (1.0 - (spread - perfectBelow) / (uselessAbove - perfectBelow)).toFloat()
}

internal fun linearMap(x: Double, x0: Double, x1: Double, y0: Double, y1: Double): Double {
    if (x1 == x0) return y0
    val f = ((x - x0) / (x1 - x0)).coerceIn(0.0, 1.0)
    return y0 + (y1 - y0) * f
}
