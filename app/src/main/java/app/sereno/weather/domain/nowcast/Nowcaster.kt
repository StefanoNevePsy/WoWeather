package app.sereno.weather.domain.nowcast

import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.Confidence
import app.sereno.weather.domain.model.Nowcast
import app.sereno.weather.domain.model.NowcastKind
import app.sereno.weather.domain.synthesis.ConfidenceEngine
import kotlin.math.roundToInt

/**
 * Answers the only weather question most people ask: *is it about to rain?*
 *
 * The hard part is honesty. The inputs are hourly, so the true resolution of
 * any answer is one hour — but "rain sometime in the 17:00 hour" is useless,
 * while "rain at 17:23" is a lie. Sereno's compromise is to interpolate
 * *within* the hour using the shape of the surrounding hours, and then to widen
 * the result into a range whose width grows with how uncertain the models are.
 * A confident, sharply-rising signal yields "in 35–45 min"; a marginal, hedged
 * one yields "in 30–75 min". The range is the honest part.
 */
object Nowcaster {

    private const val HORIZON_HOURS = 3
    private const val MIN_RATE = BlendedHour.WET_THRESHOLD_MM

    fun compute(hours: List<BlendedHour>, nowEpoch: Long): Nowcast {
        val window = hours
            .filter { it.epochSeconds >= nowEpoch - 3600 && it.epochSeconds <= nowEpoch + HORIZON_HOURS * 3600 + 3600 }
            .sortedBy { it.epochSeconds }

        if (window.size < 2) return Nowcast(NowcastKind.Unknown, horizonHours = HORIZON_HOURS)

        val confidence = ConfidenceEngine.aggregate(window.take(HORIZON_HOURS + 1).map { it.confidence })

        // Sample the precipitation rate every 5 minutes across the horizon.
        val samples = (0..HORIZON_HOURS * 12).map { step ->
            val minute = step * 5
            val epoch = nowEpoch + minute * 60L
            minute to rateAt(window, epoch)
        }

        val snow = window.take(2).any { (it.snowfall ?: 0.0) > 0.05 }
        val wetNow = samples.first().second >= MIN_RATE

        val transitions = mutableListOf<Pair<Int, Boolean>>()
        var previousWet = wetNow
        samples.forEach { (minute, rate) ->
            val wet = rate >= MIN_RATE
            if (wet != previousWet) {
                transitions += minute to wet
                previousWet = wet
            }
        }

        // Uncertainty of the *timing*, expressed as minutes of slack either
        // side. Low confidence widens the window; so does a slow, flat onset.
        val slack = timingSlack(confidence)

        return when {
            wetNow -> {
                val stop = transitions.firstOrNull { !it.second }?.first
                val spellCount = transitions.count { it.second }
                when {
                    stop == null -> Nowcast(
                        kind = NowcastKind.Ongoing,
                        peakIntensityMmH = samples.maxOf { it.second },
                        isSnow = snow, confidence = confidence, horizonHours = HORIZON_HOURS,
                    )
                    spellCount >= 1 -> Nowcast(
                        kind = NowcastKind.Intermittent,
                        endMinutesLow = (stop - slack).coerceAtLeast(5),
                        endMinutesHigh = stop + slack,
                        peakIntensityMmH = samples.maxOf { it.second },
                        isSnow = snow, confidence = confidence, horizonHours = HORIZON_HOURS,
                    )
                    else -> Nowcast(
                        kind = NowcastKind.Stopping,
                        endMinutesLow = (stop - slack).coerceAtLeast(5),
                        endMinutesHigh = stop + slack,
                        peakIntensityMmH = samples.maxOf { it.second },
                        isSnow = snow, confidence = confidence, horizonHours = HORIZON_HOURS,
                    )
                }
            }

            else -> {
                val start = transitions.firstOrNull { it.second }?.first
                if (start == null) {
                    Nowcast(
                        kind = NowcastKind.Dry,
                        confidence = confidence,
                        horizonHours = HORIZON_HOURS,
                    )
                } else {
                    val stop = transitions.firstOrNull { it.first > start && !it.second }?.first
                    val spells = transitions.count { it.second }
                    if (spells >= 2) {
                        Nowcast(
                            kind = NowcastKind.Intermittent,
                            startMinutesLow = (start - slack).coerceAtLeast(5),
                            startMinutesHigh = start + slack,
                            peakIntensityMmH = samples.maxOf { it.second },
                            isSnow = snow, confidence = confidence, horizonHours = HORIZON_HOURS,
                        )
                    } else {
                        Nowcast(
                            kind = NowcastKind.StartingSoon,
                            startMinutesLow = roundTo5((start - slack).coerceAtLeast(5)),
                            startMinutesHigh = roundTo5(start + slack),
                            endMinutesLow = stop?.let { roundTo5(it) },
                            endMinutesHigh = stop?.let { roundTo5(it + slack) },
                            peakIntensityMmH = samples.maxOf { it.second },
                            isSnow = snow, confidence = confidence, horizonHours = HORIZON_HOURS,
                        )
                    }
                }
            }
        }
    }

    /**
     * Precipitation rate at an arbitrary instant.
     *
     * Hourly precipitation is an *accumulation over the preceding hour*, so a
     * step function would place a shower at the wrong end of its hour. Linear
     * interpolation between hour centres puts the signal where the physics
     * suggests it actually is.
     */
    private fun rateAt(hours: List<BlendedHour>, epoch: Long): Double {
        val centred = hours.map { (it.epochSeconds - 1800) to (it.precipitation ?: 0.0) }
        val before = centred.lastOrNull { it.first <= epoch }
        val after = centred.firstOrNull { it.first > epoch }
        return when {
            before == null && after == null -> 0.0
            before == null -> after!!.second
            after == null -> before.second
            else -> {
                val span = (after.first - before.first).toDouble()
                if (span <= 0.0) before.second
                else {
                    val f = (epoch - before.first).toDouble() / span
                    before.second + (after.second - before.second) * f
                }
            }
        }
    }

    /** Half-width of the reported timing window, in minutes. */
    private fun timingSlack(confidence: Confidence): Int = when {
        confidence.score >= 0.85f -> 5
        confidence.score >= 0.70f -> 10
        confidence.score >= 0.55f -> 15
        confidence.score >= 0.40f -> 25
        else -> 30
    }

    private fun roundTo5(minutes: Int): Int = ((minutes / 5.0).roundToInt() * 5).coerceAtLeast(5)
}

/**
 * The higher-resolution path.
 *
 * Where Open-Meteo publishes a 15-minute precipitation series — it does for much
 * of central Europe — the timing of an onset is genuinely knowable to within a
 * few minutes rather than inferred from hourly totals. This produces the same
 * [Nowcast] shape so the UI never has to know which path was taken; only the
 * reported range narrows.
 */
object FineNowcaster {

    private const val MIN_RATE_MM_PER_QUARTER = 0.05

    fun compute(
        samples: List<Pair<Long, Double>>,
        nowEpoch: Long,
        confidence: app.sereno.weather.domain.model.Confidence,
        snow: Boolean,
        horizonHours: Int = 3,
    ): app.sereno.weather.domain.model.Nowcast? {
        val window = samples
            .filter { it.first in nowEpoch..(nowEpoch + horizonHours * 3600L) }
            .sortedBy { it.first }
        if (window.size < 4) return null

        val wetNow = window.first().second >= MIN_RATE_MM_PER_QUARTER
        val transitions = mutableListOf<Pair<Int, Boolean>>()
        var previous = wetNow
        window.forEach { (epoch, mm) ->
            val wet = mm >= MIN_RATE_MM_PER_QUARTER
            if (wet != previous) {
                transitions += ((epoch - nowEpoch) / 60).toInt() to wet
                previous = wet
            }
        }

        // Quarter-hourly data is honest to roughly a quarter hour; claiming
        // better than +/- 5 minutes from it would be false precision.
        val slack = if (confidence.score >= 0.7f) 5 else 10
        val peak = window.maxOf { it.second } * 4.0

        return when {
            wetNow -> {
                val stop = transitions.firstOrNull { !it.second }?.first
                if (stop == null) {
                    app.sereno.weather.domain.model.Nowcast(
                        kind = app.sereno.weather.domain.model.NowcastKind.Ongoing,
                        peakIntensityMmH = peak, isSnow = snow,
                        confidence = confidence, horizonHours = horizonHours,
                    )
                } else {
                    app.sereno.weather.domain.model.Nowcast(
                        kind = app.sereno.weather.domain.model.NowcastKind.Stopping,
                        endMinutesLow = (stop - slack).coerceAtLeast(5),
                        endMinutesHigh = stop + slack,
                        peakIntensityMmH = peak, isSnow = snow,
                        confidence = confidence, horizonHours = horizonHours,
                    )
                }
            }
            else -> {
                val start = transitions.firstOrNull { it.second }?.first
                    ?: return app.sereno.weather.domain.model.Nowcast(
                        kind = app.sereno.weather.domain.model.NowcastKind.Dry,
                        confidence = confidence, horizonHours = horizonHours,
                    )
                val spells = transitions.count { it.second }
                val stop = transitions.firstOrNull { it.first > start && !it.second }?.first
                app.sereno.weather.domain.model.Nowcast(
                    kind = if (spells >= 2) app.sereno.weather.domain.model.NowcastKind.Intermittent
                    else app.sereno.weather.domain.model.NowcastKind.StartingSoon,
                    startMinutesLow = (start - slack).coerceAtLeast(5),
                    startMinutesHigh = start + slack,
                    endMinutesLow = stop,
                    endMinutesHigh = stop?.plus(slack),
                    peakIntensityMmH = peak, isSnow = snow,
                    confidence = confidence, horizonHours = horizonHours,
                )
            }
        }
    }
}
