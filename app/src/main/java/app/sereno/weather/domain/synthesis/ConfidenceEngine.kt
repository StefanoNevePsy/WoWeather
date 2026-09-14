package app.sereno.weather.domain.synthesis

import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.Confidence
import app.sereno.weather.domain.model.ConfidenceDriver
import app.sereno.weather.domain.model.ConfidenceFactor

/**
 * Turns model disagreement into a number a person can act on.
 *
 * The design rule that shaped this: **confidence must be able to be low.** A
 * score that sits at 85% whatever the models are doing is decoration. So the
 * dominant factor is not how tight the temperatures are — temperatures almost
 * always agree — but whether the models agree on *whether it rains at all*,
 * which is the disagreement that actually ruins someone's afternoon.
 *
 * The lead-time decay is applied as a multiplier rather than as another
 * weighted factor, because a unanimous day-9 forecast is still a day-9
 * forecast: agreement cannot buy back predictability that does not exist.
 */
object ConfidenceEngine {

    private const val W_TEMPERATURE = 0.24f
    private const val W_PRECIP_AGREEMENT = 0.32f
    private const val W_AMOUNT_SPREAD = 0.14f
    private const val W_WIND = 0.13f
    private const val W_MODEL_COUNT = 0.17f

    internal fun compute(
        temperatures: List<Weighted>,
        precipitations: List<Weighted>,
        winds: List<Weighted>,
        leadHours: Int,
        modelCount: Int,
    ): Confidence {
        if (modelCount == 0) return Confidence.unknown

        val drivers = mutableListOf<ConfidenceDriver>()

        // --- temperature -----------------------------------------------------
        val tempSpread = temperatures.weightedStdDev()
        val tempScore = agreementFrom(tempSpread, perfectBelow = 0.7, uselessAbove = 3.6)
        drivers += ConfidenceDriver(ConfidenceFactor.TemperatureSpread, tempScore, tempSpread, W_TEMPERATURE)

        // --- does it rain at all? -------------------------------------------
        val totalPrecipWeight = precipitations.sumOf { it.weight }
        val wetWeight = precipitations.filter { it.value >= BlendedHour.WET_THRESHOLD_MM }.sumOf { it.weight }
        val agreement = if (totalPrecipWeight <= 0.0) 1.0 else {
            maxOf(wetWeight, totalPrecipWeight - wetWeight) / totalPrecipWeight
        }
        // Unanimity is 1.0 and a dead split is 0.5, so rescale to a real 0..1.
        val precipScore = (((agreement - 0.5) * 2.0).coerceIn(0.0, 1.0)).toFloat()
        drivers += ConfidenceDriver(ConfidenceFactor.PrecipitationAgreement, precipScore, agreement, W_PRECIP_AGREEMENT)

        // --- if it rains, how much? -----------------------------------------
        val mean = precipitations.weightedMean() ?: 0.0
        val amountScore: Float
        val amountValue: Double
        if (mean < BlendedHour.WET_THRESHOLD_MM || precipitations.size < 2) {
            // Nothing to disagree about when everyone says dry.
            amountScore = 1f
            amountValue = 0.0
        } else {
            val cv = precipitations.weightedStdDev() / mean
            amountValue = cv
            amountScore = agreementFrom(cv, perfectBelow = 0.35, uselessAbove = 1.4)
        }
        drivers += ConfidenceDriver(ConfidenceFactor.AmountSpread, amountScore, amountValue, W_AMOUNT_SPREAD)

        // --- wind ------------------------------------------------------------
        val windSpread = winds.weightedStdDev()
        val windScore = agreementFrom(windSpread, perfectBelow = 3.0, uselessAbove = 16.0)
        drivers += ConfidenceDriver(ConfidenceFactor.WindSpread, windScore, windSpread, W_WIND)

        // --- how much evidence is there? -------------------------------------
        val countScore = when (modelCount) {
            0 -> 0f
            1 -> 0.24f
            2 -> 0.55f
            3 -> 0.78f
            4 -> 0.91f
            else -> 1f
        }
        drivers += ConfidenceDriver(ConfidenceFactor.ModelCount, countScore, modelCount.toDouble(), W_MODEL_COUNT)

        val agreementScore =
            tempScore * W_TEMPERATURE +
                precipScore * W_PRECIP_AGREEMENT +
                amountScore * W_AMOUNT_SPREAD +
                windScore * W_WIND +
                countScore * W_MODEL_COUNT

        val lead = leadFactor(leadHours)
        drivers += ConfidenceDriver(ConfidenceFactor.LeadTime, lead, leadHours.toDouble(), 1f)

        val score = (agreementScore * lead).coerceIn(0f, 1f)
        return Confidence(
            score = score,
            band = Confidence.bandFor(score),
            modelCount = modelCount,
            drivers = drivers,
        )
    }

    /**
     * Predictability ceiling as a function of lead time.
     *
     * Anchored on how deterministic NWP skill actually decays: still excellent
     * inside 24 h, clearly degraded past day 5, and by day 12–15 only good for
     * the general shape of a pattern.
     */
    fun leadFactor(leadHours: Int): Float {
        val points = listOf(
            0 to 1.00, 24 to 0.98, 48 to 0.95, 72 to 0.92,
            120 to 0.83, 168 to 0.72, 216 to 0.63, 264 to 0.55, 336 to 0.46, 384 to 0.42,
        )
        val h = leadHours.coerceAtLeast(0)
        if (h >= points.last().first) return points.last().second.toFloat()
        for (i in 0 until points.size - 1) {
            val (h0, v0) = points[i]
            val (h1, v1) = points[i + 1]
            if (h in h0..h1) {
                val f = (h - h0).toDouble() / (h1 - h0).toDouble()
                return (v0 + (v1 - v0) * f).toFloat()
            }
        }
        return 1f
    }

    /** Combines per-hour confidences into a confidence for a whole day. */
    fun aggregate(parts: List<Confidence>): Confidence {
        val usable = parts.filter { it.modelCount > 0 }
        if (usable.isEmpty()) return Confidence.unknown
        // The mean, pulled towards the worst hour: a day containing one very
        // uncertain afternoon is not a confident day.
        val mean = usable.map { it.score }.average().toFloat()
        val worst = usable.minOf { it.score }
        val score = (mean * 0.72f + worst * 0.28f).coerceIn(0f, 1f)
        return Confidence(
            score = score,
            band = Confidence.bandFor(score),
            modelCount = usable.maxOf { it.modelCount },
            drivers = usable.maxByOrNull { it.drivers.size }?.drivers.orEmpty(),
        )
    }
}
