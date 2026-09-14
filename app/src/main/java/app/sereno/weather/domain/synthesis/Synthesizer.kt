package app.sereno.weather.domain.synthesis

import app.sereno.weather.domain.model.BlendedDay
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.Confidence
import app.sereno.weather.domain.model.DayPoint
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.ModelSeries
import app.sereno.weather.domain.model.Provenance
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.domain.model.WeatherModel
import kotlin.math.roundToInt

/**
 * Collapses several models into the single forecast Sereno shows, while keeping
 * the disagreement that produced it.
 *
 * The blend is a lead-time-weighted mean (see [ModelWeights]) applied per field,
 * per hour, skipping nulls — so a model that does not publish gusts, or that has
 * no data for this location, simply does not vote on that field rather than
 * dragging it toward zero.
 */
object Synthesizer {

    data class Result(
        val hours: List<BlendedHour>,
        val days: List<BlendedDay>,
    )

    fun synthesize(
        series: Map<WeatherModel, ModelSeries>,
        nowEpoch: Long,
    ): Result {
        val usable = series.filterValues { it.available && it.hourly.isNotEmpty() }
        if (usable.isEmpty()) return Result(emptyList(), emptyList())

        val hours = blendHours(usable, nowEpoch)
        val days = blendDays(series.filterValues { it.available && it.daily.isNotEmpty() }, nowEpoch, hours)
        return Result(hours, days)
    }

    // -----------------------------------------------------------------------
    // Hourly
    // -----------------------------------------------------------------------

    private fun blendHours(series: Map<WeatherModel, ModelSeries>, nowEpoch: Long): List<BlendedHour> {
        // Index every model by timestamp once: the naive version of this is
        // O(models x hours x hours) and is genuinely slow enough to drop frames
        // on the first composition.
        val indexed: Map<WeatherModel, Map<Long, HourPoint>> =
            series.mapValues { (_, s) -> s.hourly.associateBy { it.epochSeconds } }

        val timeline = indexed.values.flatMap { it.keys }.distinct().sorted()

        return timeline.map { epoch ->
            val leadHours = ((epoch - nowEpoch) / 3600.0).roundToInt().coerceAtLeast(0)

            val contributors = mutableListOf<WeatherModel>()
            val weightOf = mutableMapOf<WeatherModel, Double>()
            val points = mutableMapOf<WeatherModel, HourPoint>()

            indexed.forEach { (model, byTime) ->
                val point = byTime[epoch] ?: return@forEach
                val w = ModelWeights.weight(model, leadHours)
                if (w <= 0.0) return@forEach
                // A row of all-nulls is a model that does not cover this place.
                if (point.temperature == null && point.precipitation == null && point.windSpeed == null) return@forEach
                weightOf[model] = w
                points[model] = point
                contributors += model
            }

            if (contributors.isEmpty()) {
                return@map emptyHour(epoch)
            }

            fun samples(selector: (HourPoint) -> Double?): List<Weighted> =
                points.mapNotNull { (model, p) ->
                    selector(p)?.let { Weighted(it, weightOf.getValue(model)) }
                }

            val temps = samples { it.temperature }
            val precips = samples { it.precipitation }
            val winds = samples { it.windSpeed }

            val precipitation = precips.weightedMean()
            val snowfall = samples { it.snowfall }.weightedMean()
            val cloud = samples { it.cloudCover }.weightedMean()

            val (probability, probProvenance) = blendProbability(points, weightOf, precips)

            val modalCode = modalWeatherCode(points, weightOf)
            val code = WeatherCodes.reconcile(modalCode, precipitation, snowfall, cloud)

            val isDay = points.values.mapNotNull { it.isDay }.let { flags ->
                if (flags.isEmpty()) true else flags.count { it } * 2 >= flags.size
            }

            BlendedHour(
                epochSeconds = epoch,
                temperature = temps.weightedMean(),
                temperatureSpread = temps.weightedStdDev(),
                apparentTemperature = samples { it.apparentTemperature }.weightedMean(),
                precipitation = precipitation,
                precipitationSpread = precips.weightedStdDev(),
                precipitationProbability = probability,
                probabilityProvenance = probProvenance,
                snowfall = snowfall,
                windSpeed = winds.weightedMean(),
                windSpeedSpread = winds.weightedStdDev(),
                windGust = samples { it.windGust }.weightedMean(),
                windDirection = samples { it.windDirection }.circularMean(),
                cloudCover = cloud,
                humidity = samples { it.humidity }.weightedMean(),
                pressure = samples { it.pressure }.weightedMean(),
                visibility = samples { it.visibility }.weightedMean(),
                uvIndex = samples { it.uvIndex }.weightedMean(),
                dewPoint = samples { it.dewPoint }.weightedMean(),
                weatherCode = code,
                isDay = isDay,
                confidence = ConfidenceEngine.compute(temps, precips, winds, leadHours, contributors.size),
                contributors = contributors.sortedBy { it.ordinal },
            )
        }
    }

    /**
     * Precipitation probability is the one field Sereno is allowed to derive.
     *
     * Most deterministic models do not publish a probability at all. Rather than
     * leave the most useful number on the screen blank, we compute the weighted
     * share of models that produce meaningful rain — a genuine measure of model
     * agreement — and tag it [Provenance.Derived] so the UI can say where it
     * came from. It is never presented as if a model had issued it.
     */
    private fun blendProbability(
        points: Map<WeatherModel, HourPoint>,
        weightOf: Map<WeatherModel, Double>,
        precips: List<Weighted>,
    ): Pair<Double?, Provenance> {
        val published = points.mapNotNull { (model, p) ->
            p.precipitationProbability?.let { Weighted(it, weightOf.getValue(model)) }
        }
        if (published.isNotEmpty()) {
            val value = published.weightedMean()
            return value to if (published.size > 1) Provenance.Blended else Provenance.SingleModel
        }
        if (precips.isEmpty()) return null to Provenance.Missing

        val totalWeight = precips.sumOf { it.weight }
        if (totalWeight <= 0.0) return null to Provenance.Missing

        // Each model contributes a soft vote: nothing below 0.05 mm, full vote
        // by 0.5 mm. A hard wet/dry threshold produces a jittery 0/100% number.
        val votes = precips.sumOf { it.weight * wetness(it.value) }
        val probability = (votes / totalWeight) * 100.0
        return (probability / 5.0).roundToInt() * 5.0 to Provenance.Derived
    }

    private fun wetness(mm: Double): Double = linearMap(mm, 0.05, 0.5, 0.0, 1.0)

    /**
     * The weather code with the most weight behind it, tie-broken towards the
     * more significant weather so that a 50/50 sun-versus-storm hour shows the
     * storm.
     */
    private fun modalWeatherCode(
        points: Map<WeatherModel, HourPoint>,
        weightOf: Map<WeatherModel, Double>,
    ): Int? {
        val tally = mutableMapOf<Int, Double>()
        points.forEach { (model, p) ->
            val code = p.weatherCode ?: return@forEach
            tally[code] = (tally[code] ?: 0.0) + weightOf.getValue(model)
        }
        if (tally.isEmpty()) return null
        val best = tally.values.max()
        return tally.filterValues { it >= best - 1e-9 }.keys.maxByOrNull { WeatherCodes.rank(it) }
    }

    private fun emptyHour(epoch: Long) = BlendedHour(
        epochSeconds = epoch,
        temperature = null, temperatureSpread = 0.0, apparentTemperature = null,
        precipitation = null, precipitationSpread = 0.0,
        precipitationProbability = null, probabilityProvenance = Provenance.Missing,
        snowfall = null, windSpeed = null, windSpeedSpread = 0.0, windGust = null,
        windDirection = null, cloudCover = null, humidity = null, pressure = null,
        visibility = null, uvIndex = null, dewPoint = null, weatherCode = null,
        isDay = true, confidence = Confidence.unknown, contributors = emptyList(),
    )

    // -----------------------------------------------------------------------
    // Daily
    // -----------------------------------------------------------------------

    private fun blendDays(
        series: Map<WeatherModel, ModelSeries>,
        nowEpoch: Long,
        blendedHours: List<BlendedHour>,
    ): List<BlendedDay> {
        if (series.isEmpty()) return emptyList()

        val indexed: Map<WeatherModel, Map<Long, DayPoint>> =
            series.mapValues { (_, s) -> s.daily.associateBy { it.epochSeconds } }
        val timeline = indexed.values.flatMap { it.keys }.distinct().sorted()

        val hoursByDay = blendedHours.groupBy { dayStartOf(it.epochSeconds, timeline) }

        return timeline.map { dayEpoch ->
            // Weight a day by its midpoint, not its start: a day-7 forecast
            // should not be weighted as if it were day 6.
            val leadHours = ((dayEpoch + 43_200L - nowEpoch) / 3600.0).roundToInt().coerceAtLeast(0)

            val weightOf = mutableMapOf<WeatherModel, Double>()
            val points = mutableMapOf<WeatherModel, DayPoint>()
            indexed.forEach { (model, byDay) ->
                val point = byDay[dayEpoch] ?: return@forEach
                val w = ModelWeights.weight(model, leadHours)
                if (w <= 0.0) return@forEach
                if (point.temperatureMax == null && point.precipitationSum == null) return@forEach
                weightOf[model] = w
                points[model] = point
            }
            if (points.isEmpty()) return@map emptyDay(dayEpoch)

            fun samples(selector: (DayPoint) -> Double?): List<Weighted> =
                points.mapNotNull { (model, p) ->
                    selector(p)?.let { Weighted(it, weightOf.getValue(model)) }
                }

            val maxes = samples { it.temperatureMax }
            val sums = samples { it.precipitationSum }
            val gusts = samples { it.windGustMax }

            val published = points.mapNotNull { (model, p) ->
                p.precipitationProbabilityMax?.let { Weighted(it, weightOf.getValue(model)) }
            }
            val probability: Double?
            val probProvenance: Provenance
            if (published.isNotEmpty()) {
                probability = published.weightedMean()
                probProvenance = if (published.size > 1) Provenance.Blended else Provenance.SingleModel
            } else {
                val dayHours = hoursByDay[dayEpoch].orEmpty()
                probability = dayHours.mapNotNull { it.precipitationProbability }.maxOrNull()
                probProvenance = if (probability != null) Provenance.Derived else Provenance.Missing
            }

            val precipSum = sums.weightedMean()
            val snowSum = samples { it.snowfallSum }.weightedMean()
            val modal = modalDayCode(points, weightOf)

            val dayHours = hoursByDay[dayEpoch].orEmpty()
            val confidence = if (dayHours.isNotEmpty()) {
                ConfidenceEngine.aggregate(dayHours.map { it.confidence })
            } else {
                ConfidenceEngine.compute(maxes, sums, samples { it.windSpeedMax }, leadHours, points.size)
            }

            BlendedDay(
                epochSeconds = dayEpoch,
                temperatureMax = maxes.weightedMean(),
                temperatureMin = samples { it.temperatureMin }.weightedMean(),
                temperatureMaxSpread = maxes.weightedStdDev(),
                precipitationSum = precipSum,
                precipitationSumSpread = sums.weightedStdDev(),
                precipitationProbabilityMax = probability,
                probabilityProvenance = probProvenance,
                snowfallSum = snowSum,
                windSpeedMax = samples { it.windSpeedMax }.weightedMean(),
                windGustMax = gusts.weightedMean(),
                windDirectionDominant = samples { it.windDirectionDominant }.circularMean(),
                uvIndexMax = samples { it.uvIndexMax }.weightedMean(),
                weatherCode = WeatherCodes.reconcile(modal, precipSum, snowSum, null),
                // Astronomy is geometry, not a forecast: take it from whichever
                // model supplied it rather than averaging timestamps.
                sunriseEpoch = points.values.firstNotNullOfOrNull { it.sunriseEpoch },
                sunsetEpoch = points.values.firstNotNullOfOrNull { it.sunsetEpoch },
                confidence = confidence,
                contributors = points.keys.sortedBy { it.ordinal },
            )
        }
    }

    private fun modalDayCode(points: Map<WeatherModel, DayPoint>, weightOf: Map<WeatherModel, Double>): Int? {
        val tally = mutableMapOf<Int, Double>()
        points.forEach { (model, p) ->
            val code = p.weatherCode ?: return@forEach
            tally[code] = (tally[code] ?: 0.0) + weightOf.getValue(model)
        }
        if (tally.isEmpty()) return null
        val best = tally.values.max()
        return tally.filterValues { it >= best - 1e-9 }.keys.maxByOrNull { WeatherCodes.rank(it) }
    }

    private fun dayStartOf(epoch: Long, dayStarts: List<Long>): Long =
        dayStarts.lastOrNull { it <= epoch } ?: dayStarts.firstOrNull() ?: epoch

    private fun emptyDay(epoch: Long) = BlendedDay(
        epochSeconds = epoch,
        temperatureMax = null, temperatureMin = null, temperatureMaxSpread = 0.0,
        precipitationSum = null, precipitationSumSpread = 0.0,
        precipitationProbabilityMax = null, probabilityProvenance = Provenance.Missing,
        snowfallSum = null, windSpeedMax = null, windGustMax = null,
        windDirectionDominant = null, uvIndexMax = null, weatherCode = null,
        sunriseEpoch = null, sunsetEpoch = null,
        confidence = Confidence.unknown, contributors = emptyList(),
    )
}
