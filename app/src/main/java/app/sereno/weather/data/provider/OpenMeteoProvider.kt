package app.sereno.weather.data.provider

import app.sereno.weather.data.net.Http
import app.sereno.weather.data.net.HttpException
import app.sereno.weather.domain.model.Coordinates
import app.sereno.weather.domain.model.CurrentConditions
import app.sereno.weather.domain.model.DayPoint
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.ModelAvailability
import app.sereno.weather.domain.model.ModelSeries
import app.sereno.weather.domain.model.WeatherModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Open-Meteo, used as a multi-model gateway rather than as a single forecast.
 *
 * Open-Meteo will serve several NWP models in one response, suffixing every
 * variable with the model id (`temperature_2m_icon_eu`). That is the single
 * fact that makes Sereno's whole premise affordable: seven models cost one
 * request and a few tens of kilobytes, not seven round trips.
 *
 * Two calls go out per refresh:
 *  - the multi-model call, for the hourly and daily series that get blended;
 *  - a plain call, for current conditions and — where Open-Meteo has it — the
 *    15-minute precipitation series that sharpens the nowcast.
 *
 * They are independent, so a failure of the second never costs us the forecast.
 */
class OpenMeteoProvider(private val http: Http) : ForecastProvider {

    override val id = "open-meteo"
    override val displayName = "Open-Meteo"
    override val attribution = "Weather data by Open-Meteo.com (CC BY 4.0)"

    override val supportedModels: List<WeatherModel> = WeatherModel.entries.toList()

    override suspend fun fetch(
        coordinates: Coordinates,
        models: List<WeatherModel>,
        days: Int,
    ): RawForecast = coroutineScope {
        val multiModel = async { fetchMultiModel(coordinates, models, days) }
        val observational = async { runCatching { fetchObservational(coordinates) }.getOrNull() }

        val forecast = multiModel.await()
        val extra = observational.await()

        forecast.copy(
            current = extra?.current ?: forecast.current,
            minutely = extra?.minutely.orEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // Multi-model hourly + daily
    // -----------------------------------------------------------------------

    private suspend fun fetchMultiModel(
        coordinates: Coordinates,
        models: List<WeatherModel>,
        days: Int,
    ): RawForecast {
        val result = try {
            http.get(buildForecastUrl(coordinates, models, days, RICH_HOURLY))
        } catch (e: HttpException) {
            // Open-Meteo rejects the whole request if one variable is unknown
            // for every requested model. Rather than permanently shipping the
            // lowest common denominator, ask for the rich set and step down
            // only when this particular combination refuses it.
            if (e.code == 400) {
                http.get(buildForecastUrl(coordinates, models, days, CORE_HOURLY))
            } else throw e
        }

        val root = http.json.parseToJsonElement(result.body).jsonObject
        val hourly = root["hourly"]?.jsonObject
        val daily = root["daily"]?.jsonObject
        val hourTimes = hourly.longs("time").orEmpty()
        val dayTimes = daily.longs("time").orEmpty()

        val multi = models.size > 1
        val series = mutableMapOf<WeatherModel, ModelSeries>()
        val availability = mutableListOf<ModelAvailability>()

        models.forEach { model ->
            val suffix = if (multi) "_${model.apiId}" else ""
            val hours = parseHours(hourly, hourTimes, suffix)
            val dayPoints = parseDays(daily, dayTimes, suffix)

            // A model out of its domain comes back as a full-length array of
            // nulls, not as a missing key, so presence is measured in values.
            val realHours = hours.count { it.temperature != null || it.precipitation != null }
            val available = realHours > 0

            series[model] = ModelSeries(
                model = model,
                hourly = if (available) hours.filter { it.temperature != null || it.precipitation != null } else emptyList(),
                daily = if (available) dayPoints.filter { it.temperatureMax != null || it.precipitationSum != null } else emptyList(),
                available = available,
            )
            availability += ModelAvailability(
                model = model,
                available = available,
                hoursReturned = realHours,
                note = if (available) null else "no data at this location",
            )
        }

        return RawForecast(
            series = series,
            availability = availability,
            current = null,
            timezone = root["timezone"]?.jsonPrimitive?.contentOrNullSafe() ?: "UTC",
            utcOffsetSeconds = root["utc_offset_seconds"]?.jsonPrimitive?.intOrNull ?: 0,
            minutely = emptyList(),
            latencyMs = result.latencyMs,
        )
    }

    private fun parseHours(hourly: JsonObject?, times: List<Long>, suffix: String): List<HourPoint> {
        if (hourly == null || times.isEmpty()) return emptyList()
        val temperature = hourly.doubles("temperature_2m$suffix")
        val apparent = hourly.doubles("apparent_temperature$suffix")
        val dewPoint = hourly.doubles("dew_point_2m$suffix")
        val humidity = hourly.doubles("relative_humidity_2m$suffix")
        val precipitation = hourly.doubles("precipitation$suffix")
        val rain = hourly.doubles("rain$suffix")
        val showers = hourly.doubles("showers$suffix")
        val snowfall = hourly.doubles("snowfall$suffix")
        val probability = hourly.doubles("precipitation_probability$suffix")
        val windSpeed = hourly.doubles("wind_speed_10m$suffix")
        val windDirection = hourly.doubles("wind_direction_10m$suffix")
        val windGusts = hourly.doubles("wind_gusts_10m$suffix")
        val cloud = hourly.doubles("cloud_cover$suffix")
        val pressure = hourly.doubles("pressure_msl$suffix")
        val visibility = hourly.doubles("visibility$suffix")
        val uv = hourly.doubles("uv_index$suffix")
        val code = hourly.ints("weather_code$suffix")
        val isDay = hourly.ints("is_day$suffix")

        return times.mapIndexed { i, epoch ->
            HourPoint(
                epochSeconds = epoch,
                temperature = temperature.at(i),
                apparentTemperature = apparent.at(i),
                dewPoint = dewPoint.at(i),
                humidity = humidity.at(i),
                precipitation = precipitation.at(i),
                rain = rain.at(i),
                showers = showers.at(i),
                snowfall = snowfall.at(i),
                precipitationProbability = probability.at(i),
                windSpeed = windSpeed.at(i),
                windGust = windGusts.at(i),
                windDirection = windDirection.at(i),
                cloudCover = cloud.at(i),
                pressure = pressure.at(i),
                visibility = visibility.at(i),
                uvIndex = uv.at(i),
                weatherCode = code.at(i),
                isDay = isDay.at(i)?.let { it == 1 },
            )
        }
    }

    private fun parseDays(daily: JsonObject?, times: List<Long>, suffix: String): List<DayPoint> {
        if (daily == null || times.isEmpty()) return emptyList()
        val max = daily.doubles("temperature_2m_max$suffix")
        val min = daily.doubles("temperature_2m_min$suffix")
        val appMax = daily.doubles("apparent_temperature_max$suffix")
        val appMin = daily.doubles("apparent_temperature_min$suffix")
        val precipSum = daily.doubles("precipitation_sum$suffix")
        val rainSum = daily.doubles("rain_sum$suffix")
        val snowSum = daily.doubles("snowfall_sum$suffix")
        val precipHours = daily.doubles("precipitation_hours$suffix")
        val probMax = daily.doubles("precipitation_probability_max$suffix")
        val windMax = daily.doubles("wind_speed_10m_max$suffix")
        val gustMax = daily.doubles("wind_gusts_10m_max$suffix")
        val windDir = daily.doubles("wind_direction_10m_dominant$suffix")
        val uvMax = daily.doubles("uv_index_max$suffix")
        val code = daily.ints("weather_code$suffix")
        val sunrise = daily.longs("sunrise$suffix")
        val sunset = daily.longs("sunset$suffix")
        val daylight = daily.doubles("daylight_duration$suffix")

        return times.mapIndexed { i, epoch ->
            DayPoint(
                epochSeconds = epoch,
                temperatureMax = max.at(i),
                temperatureMin = min.at(i),
                apparentMax = appMax.at(i),
                apparentMin = appMin.at(i),
                precipitationSum = precipSum.at(i),
                rainSum = rainSum.at(i),
                snowfallSum = snowSum.at(i),
                precipitationHours = precipHours.at(i),
                precipitationProbabilityMax = probMax.at(i),
                windSpeedMax = windMax.at(i),
                windGustMax = gustMax.at(i),
                windDirectionDominant = windDir.at(i),
                uvIndexMax = uvMax.at(i),
                weatherCode = code.at(i),
                sunriseEpoch = sunrise?.getOrNull(i),
                sunsetEpoch = sunset?.getOrNull(i),
                daylightSeconds = daylight.at(i),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Current conditions + 15-minute precipitation
    // -----------------------------------------------------------------------

    private suspend fun fetchObservational(coordinates: Coordinates): RawForecast {
        val url = buildString {
            append(FORECAST_ENDPOINT)
            append("?latitude=").append(fmt(coordinates.latitude))
            append("&longitude=").append(fmt(coordinates.longitude))
            append("&current=").append(CURRENT_FIELDS)
            append("&minutely_15=").append(MINUTELY_FIELDS)
            append("&forecast_minutely_15=48")
            append("&timezone=auto&timeformat=unixtime&wind_speed_unit=kmh&precipitation_unit=mm")
        }
        val result = http.get(url)
        val root = http.json.parseToJsonElement(result.body).jsonObject

        val current = root["current"]?.jsonObject?.let { c ->
            CurrentConditions(
                epochSeconds = c["time"]?.jsonPrimitive?.longOrNull ?: 0L,
                temperature = c.double("temperature_2m"),
                apparentTemperature = c.double("apparent_temperature"),
                humidity = c.double("relative_humidity_2m"),
                precipitation = c.double("precipitation"),
                windSpeed = c.double("wind_speed_10m"),
                windGust = c.double("wind_gusts_10m"),
                windDirection = c.double("wind_direction_10m"),
                cloudCover = c.double("cloud_cover"),
                pressure = c.double("pressure_msl"),
                visibility = null,
                uvIndex = null,
                dewPoint = null,
                weatherCode = c["weather_code"]?.jsonPrimitive?.intOrNull,
                isDay = (c["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
                source = "Open-Meteo best-match",
            )
        }

        val minutely = root["minutely_15"]?.jsonObject?.let { m ->
            val times = m.longs("time").orEmpty()
            val precip = m.doubles("precipitation")
            val snow = m.doubles("snowfall")
            val code = m.ints("weather_code")
            times.mapIndexed { i, epoch ->
                MinutePoint(epoch, precip.at(i), snow.at(i), code.at(i))
            }
        }.orEmpty()

        return RawForecast(
            series = emptyMap(),
            availability = emptyList(),
            current = current,
            timezone = root["timezone"]?.jsonPrimitive?.contentOrNullSafe() ?: "UTC",
            utcOffsetSeconds = root["utc_offset_seconds"]?.jsonPrimitive?.intOrNull ?: 0,
            minutely = minutely,
            latencyMs = result.latencyMs,
        )
    }

    // -----------------------------------------------------------------------

    private fun buildForecastUrl(
        coordinates: Coordinates,
        models: List<WeatherModel>,
        days: Int,
        hourlyFields: String,
    ): String = buildString {
        append(FORECAST_ENDPOINT)
        append("?latitude=").append(fmt(coordinates.latitude))
        append("&longitude=").append(fmt(coordinates.longitude))
        append("&hourly=").append(hourlyFields)
        append("&daily=").append(DAILY_FIELDS)
        append("&models=").append(models.joinToString(",") { it.apiId })
        append("&forecast_days=").append(days.coerceIn(1, 16))
        append("&timezone=auto&timeformat=unixtime&wind_speed_unit=kmh&precipitation_unit=mm")
    }

    private fun fmt(v: Double): String = "%.4f".format(java.util.Locale.US, v)

    companion object {
        private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"

        private const val RICH_HOURLY =
            "temperature_2m,apparent_temperature,dew_point_2m,relative_humidity_2m," +
                "precipitation,rain,showers,snowfall,precipitation_probability,weather_code," +
                "cloud_cover,pressure_msl,visibility,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index,is_day"

        /** The subset every model in the catalogue is known to publish. */
        private const val CORE_HOURLY =
            "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,rain," +
                "snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,is_day"

        private const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
                "apparent_temperature_min,sunrise,sunset,daylight_duration,uv_index_max," +
                "precipitation_sum,rain_sum,snowfall_sum,precipitation_hours," +
                "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
                "wind_direction_10m_dominant"

        private const val CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
                "rain,showers,snowfall,weather_code,cloud_cover,pressure_msl," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m"

        private const val MINUTELY_FIELDS = "precipitation,snowfall,weather_code"
    }
}

// ---------------------------------------------------------------------------
// Dynamic-key JSON helpers
//
// Open-Meteo's variable names depend on which models were requested, so the
// response cannot be described by a fixed @Serializable class. These read the
// tree directly and treat every absence — missing key, JSON null, short array —
// as a null value rather than an error.
// ---------------------------------------------------------------------------

internal fun JsonObject?.array(key: String): JsonArray? =
    this?.get(key)?.takeIf { it is JsonArray }?.jsonArray

internal fun JsonObject?.doubles(key: String): List<Double?>? =
    array(key)?.map { it.jsonPrimitive.doubleOrNull }

internal fun JsonObject?.ints(key: String): List<Int?>? =
    array(key)?.map { it.jsonPrimitive.intOrNull }

internal fun JsonObject?.longs(key: String): List<Long>? =
    array(key)?.mapNotNull { it.jsonPrimitive.longOrNull }

internal fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

internal fun <T> List<T?>?.at(index: Int): T? = this?.getOrNull(index)

internal fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
