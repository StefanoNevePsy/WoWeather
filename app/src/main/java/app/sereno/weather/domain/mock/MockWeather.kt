package app.sereno.weather.domain.mock

import app.sereno.weather.design.Mood
import app.sereno.weather.domain.model.Coordinates
import app.sereno.weather.domain.model.CurrentConditions
import app.sereno.weather.domain.model.DayPoint
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.ModelAvailability
import app.sereno.weather.domain.model.ModelSeries
import app.sereno.weather.domain.model.Place
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.domain.nowcast.Nowcaster
import app.sereno.weather.domain.severe.SevereEngine
import app.sereno.weather.domain.synthesis.Synthesizer
import app.sereno.weather.i18n.Copy
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic weather for design work.
 *
 * Every atmospheric state in the app has to be *looked at* to be judged, and
 * waiting for a real thunderstorm over Correggio to check the storm palette is
 * not a workflow. These states run through the exact same synthesis, nowcast
 * and severe-weather pipeline as live data, so what you see is what the real
 * code will render — not a hand-drawn approximation of it.
 */
object MockWeather {

    enum class State(val key: String, val label: String, val mood: Mood) {
        ClearDay("clear_day", "Clear day", Mood.ClearDay),
        ClearNight("clear_night", "Clear night", Mood.ClearNight),
        PartlyCloudy("partly", "Partly cloudy", Mood.PartlyDay),
        Overcast("overcast", "Overcast", Mood.Overcast),
        Fog("fog", "Fog", Mood.Fog),
        Rain("rain", "Rain", Mood.Rain),
        Storm("storm", "Severe storm", Mood.Thunder),
        Snow("snow", "Snow", Mood.Snow);

        companion object {
            fun fromKey(key: String?): State? = entries.firstOrNull { it.key == key }
        }
    }

    /** Europe/Rome, which is what the mock place uses. */
    private const val OFFSET_SECONDS = 7200L

    fun bundle(state: State, place: Place, nowEpoch: Long, copy: Copy): ForecastBundle {
        val hourZero = (nowEpoch / 3600) * 3600
        // Daily aggregation, day labels and sunrise/sunset all key off day
        // boundaries, so these have to be real local midnights rather than
        // "now, a day later" — otherwise the mock reports sunrise at 17:00.
        val localMidnight = ((hourZero + OFFSET_SECONDS) / 86_400) * 86_400 - OFFSET_SECONDS
        // Deterministic per state: the same mock always looks the same, which
        // matters when comparing a design change against a screenshot.
        val random = Random(state.ordinal * 7919)

        val models = listOf(
            WeatherModel.Icon2I, WeatherModel.IconEu, WeatherModel.EcmwfIfs,
            WeatherModel.EcmwfAifs, WeatherModel.Gfs,
        )

        val series = models.associateWith { model ->
            val bias = when (model) {
                WeatherModel.Icon2I -> 0.4
                WeatherModel.EcmwfIfs -> -0.2
                WeatherModel.EcmwfAifs -> 0.1
                WeatherModel.Gfs -> -0.7
                else -> 0.0
            }
            val horizon = minOf(model.horizonHours, 16 * 24)
            val hours = (0 until horizon).map { h ->
                hourPoint(state, hourZero + h * 3600L, h, bias, random)
            }
            ModelSeries(
                model = model,
                hourly = hours,
                daily = (0 until 16).mapNotNull { d ->
                    if (d * 24 >= horizon) null else dayPoint(state, localMidnight, d, hours, bias)
                },
                available = true,
            )
        }

        val synthesis = Synthesizer.synthesize(series, nowEpoch)
        val nowcast = Nowcaster.compute(synthesis.hours, nowEpoch)
        val alerts = SevereEngine.derive(synthesis.hours, nowEpoch, copy, 7200)

        val first = synthesis.hours.firstOrNull()
        return ForecastBundle(
            place = place,
            fetchedAtEpoch = nowEpoch,
            timezone = "Europe/Rome",
            utcOffsetSeconds = 7200,
            current = CurrentConditions(
                epochSeconds = nowEpoch,
                temperature = first?.temperature,
                apparentTemperature = first?.apparentTemperature,
                humidity = first?.humidity,
                precipitation = first?.precipitation,
                windSpeed = first?.windSpeed,
                windGust = first?.windGust,
                windDirection = first?.windDirection,
                cloudCover = first?.cloudCover,
                pressure = first?.pressure,
                visibility = first?.visibility,
                uvIndex = first?.uvIndex,
                dewPoint = first?.dewPoint,
                weatherCode = first?.weatherCode,
                isDay = first?.isDay ?: true,
                source = "Mock · ${state.label}",
            ),
            hours = synthesis.hours,
            days = synthesis.days,
            models = series.mapKeys { it.key.apiId },
            availability = models.map { ModelAvailability(it, true, 120, null, "mock") },
            airQuality = null,
            alerts = alerts,
            nowcast = nowcast,
            latencyMs = 0,
            fromCache = false,
        )
    }

    private fun hourPoint(
        state: State,
        epoch: Long,
        index: Int,
        bias: Double,
        random: Random,
    ): HourPoint {
        // Local solar hour drives the diurnal cycle; everything else modulates it.
        val localHour = (((epoch + 7200) / 3600) % 24).toInt()
        val diurnal = sin((localHour - 9) / 24.0 * 2 * PI)
        val night = localHour < 6 || localHour >= 20

        val base = when (state) {
            State.ClearDay -> 24.0 + diurnal * 6
            State.ClearNight -> 12.0 + diurnal * 3
            State.PartlyCloudy -> 19.0 + diurnal * 5
            State.Overcast -> 14.0 + diurnal * 2.5
            State.Fog -> 8.0 + diurnal * 1.5
            State.Rain -> 13.0 + diurnal * 2
            State.Storm -> 26.0 + diurnal * 4
            State.Snow -> -1.0 + diurnal * 2
        }
        // Two slow waves standing in for synoptic evolution. Without them every
        // day of the mock is identical, the fortnight trend chart is a flat
        // rectangle, and the daily list cannot be judged at all.
        val synoptic = sin(index / 47.0) * 3.6 + sin(index / 113.0) * 2.4
        val jitter = (random.nextDouble() - 0.5) * 0.8
        val temperature = base + bias + jitter + synoptic

        // Precipitation waxes and wanes over days too, so consecutive days in
        // the mock are not carbon copies.
        val wetSpell = (0.35 + 0.9 * ((sin(index / 61.0) + 1.0) / 2.0))
        val precipitation = when (state) {
            State.Rain -> max(0.0, (1.4 + sin(index / 3.0) * 1.6 + bias * 0.8) * wetSpell)
            // Storms in the opening hours, so the hero and the alerts agree
            // with each other when this state is selected.
            State.Storm -> if (index % 11 <= 3) max(0.0, 7.0 + sin(index.toDouble()) * 5 + bias * 3) else 0.0
            State.Snow -> max(0.0, (0.9 + sin(index / 4.0) * 0.7) * wetSpell)
            State.Overcast -> if (index % 9 == 0) 0.12 else 0.0
            else -> 0.0
        }

        val cloud = when (state) {
            State.ClearDay, State.ClearNight -> 4.0 + random.nextDouble() * 9
            State.PartlyCloudy -> 42.0 + sin(index / 5.0) * 18 + sin(index / 53.0) * 26
            State.Overcast -> 94.0
            State.Fog -> 99.0
            State.Rain -> 90.0
            State.Storm -> 82.0 + sin(index.toDouble()) * 12
            State.Snow -> 96.0
        }

        val wind = when (state) {
            State.Storm -> 34.0 + sin(index / 2.0) * 16
            State.Rain -> 17.0 + sin(index / 4.0) * 6
            State.Snow -> 12.0
            State.Fog -> 3.0
            else -> 9.0 + sin(index / 6.0) * 4
        } + bias

        val code = when (state) {
            State.ClearDay, State.ClearNight -> 0
            State.PartlyCloudy -> 2
            State.Overcast -> 3
            State.Fog -> 45
            State.Rain -> if (precipitation > 2.5) 63 else 61
            State.Storm -> if (precipitation > 5) 96 else if (precipitation > 0) 95 else 3
            State.Snow -> 73
        }

        return HourPoint(
            epochSeconds = epoch,
            temperature = temperature,
            apparentTemperature = temperature - if (wind > 20) 2.5 else 0.6,
            dewPoint = temperature - 4,
            humidity = when (state) {
                State.Fog -> 98.0
                State.Rain, State.Snow -> 88.0
                State.ClearDay -> 44.0
                else -> 66.0
            },
            precipitation = precipitation,
            snowfall = if (state == State.Snow) precipitation * 0.7 else 0.0,
            precipitationProbability = null,
            windSpeed = wind,
            windGust = wind * 1.8,
            windDirection = (220.0 + index * 3) % 360,
            cloudCover = cloud.coerceIn(0.0, 100.0),
            pressure = if (state == State.Storm) 995.0 else 1016.0,
            visibility = if (state == State.Fog) 180.0 else 22000.0,
            uvIndex = if (night) 0.0 else when (state) {
                State.ClearDay -> 7.5
                State.PartlyCloudy -> 4.5
                else -> 1.5
            },
            weatherCode = code,
            isDay = !night,
        )
    }

    private fun dayPoint(
        state: State,
        localMidnight: Long,
        dayIndex: Int,
        hours: List<HourPoint>,
        bias: Double,
    ): DayPoint {
        val dayStart = localMidnight + dayIndex * 24 * 3600L
        val dayHours = hours.filter { it.epochSeconds in dayStart until (dayStart + 24 * 3600) }
        if (dayHours.isEmpty()) {
            return DayPoint(epochSeconds = dayStart)
        }
        return DayPoint(
            epochSeconds = dayStart,
            temperatureMax = dayHours.mapNotNull { it.temperature }.maxOrNull(),
            temperatureMin = dayHours.mapNotNull { it.temperature }.minOrNull(),
            precipitationSum = dayHours.sumOf { it.precipitation ?: 0.0 },
            snowfallSum = dayHours.sumOf { it.snowfall ?: 0.0 },
            windSpeedMax = dayHours.mapNotNull { it.windSpeed }.maxOrNull(),
            windGustMax = dayHours.mapNotNull { it.windGust }.maxOrNull(),
            windDirectionDominant = 225.0 + bias,
            uvIndexMax = dayHours.mapNotNull { it.uvIndex }.maxOrNull(),
            weatherCode = dayHours.mapNotNull { it.weatherCode }.maxOrNull(),
            // Local 06:40 and 19:50, expressed against local midnight.
            sunriseEpoch = dayStart + 6 * 3600 + 40 * 60,
            sunsetEpoch = dayStart + 19 * 3600 + 50 * 60,
        )
    }

    val correggio = Place(
        id = "mock-correggio",
        name = "Correggio",
        admin = "Reggio Emilia",
        country = "Italia",
        countryCode = "IT",
        coordinates = Coordinates(44.7706, 10.7803),
        timezone = "Europe/Rome",
    )
}
