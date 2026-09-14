package app.sereno.weather.domain.synthesis

import app.sereno.weather.domain.model.WeatherModel

/**
 * How much each model is trusted, as a function of how far ahead it is looking.
 *
 * This is the opinionated core of Sereno. The shape of every curve follows from
 * one idea: **resolution wins early, physics wins late.**
 *
 *  - A 2.2 km regional model (ICON-2I) resolves the Apennine convection that a
 *    25 km global model can only smear, so it dominates the first two days —
 *    and then stops existing, because it does not run past 72 h.
 *  - ECMWF's IFS is the best medium-range deterministic model there is, so its
 *    weight climbs as the regional models drop out and peaks from day 5.
 *  - AIFS is the machine-learning sibling of IFS. It verifies close to IFS and
 *    genuinely better on some fields, but it is young, so it sits just below.
 *  - GFS is kept at a low, flat weight throughout. It is here to be *disagreed
 *    with*: a forecast where GFS is the odd one out is a forecast whose spread
 *    the user should see.
 *
 * Curves are control points of (leadHours, weight), linearly interpolated, and
 * hard zero past the model's published horizon. A model that returns nulls for
 * a location outside its domain simply never contributes, so no geographic
 * bounding boxes are needed.
 */
object ModelWeights {

    private val curves: Map<WeatherModel, List<Pair<Int, Double>>> = mapOf(
        WeatherModel.Icon2I to listOf(
            0 to 1.00, 36 to 1.00, 54 to 0.78, 66 to 0.34, 72 to 0.12,
        ),
        WeatherModel.AromeHd to listOf(
            0 to 0.86, 30 to 0.84, 42 to 0.50, 48 to 0.12,
        ),
        WeatherModel.IconEu to listOf(
            0 to 0.56, 48 to 0.56, 84 to 0.46, 108 to 0.30, 120 to 0.16,
        ),
        WeatherModel.IconGlobal to listOf(
            0 to 0.26, 72 to 0.30, 120 to 0.30, 168 to 0.22, 180 to 0.14,
        ),
        WeatherModel.EcmwfIfs to listOf(
            0 to 0.44, 48 to 0.62, 84 to 0.90, 120 to 1.00, 240 to 1.00, 360 to 0.84,
        ),
        WeatherModel.EcmwfAifs to listOf(
            0 to 0.32, 48 to 0.48, 84 to 0.70, 120 to 0.84, 240 to 0.84, 360 to 0.74,
        ),
        WeatherModel.Gfs to listOf(
            0 to 0.24, 120 to 0.26, 240 to 0.24, 384 to 0.16,
        ),
    )

    fun weight(model: WeatherModel, leadHours: Int): Double {
        if (leadHours > model.horizonHours) return 0.0
        val curve = curves[model] ?: return 0.25
        if (leadHours <= curve.first().first) return curve.first().second
        if (leadHours >= curve.last().first) return curve.last().second
        for (i in 0 until curve.size - 1) {
            val (h0, w0) = curve[i]
            val (h1, w1) = curve[i + 1]
            if (leadHours in h0..h1) {
                val f = (leadHours - h0).toDouble() / (h1 - h0).toDouble()
                return w0 + (w1 - w0) * f
            }
        }
        return curve.last().second
    }

    /**
     * Which regime the app is in at a given lead time. Shown to the user in the
     * Forecast Lab so the weighting is explainable rather than magic.
     */
    fun regime(leadHours: Int): Regime = when {
        leadHours <= 2 -> Regime.Nowcast
        leadHours <= 72 -> Regime.HighResolution
        leadHours <= 168 -> Regime.MediumRange
        else -> Regime.Extended
    }

    enum class Regime { Nowcast, HighResolution, MediumRange, Extended }
}
