package app.sereno.weather.data.provider

import app.sereno.weather.domain.model.AirQuality
import app.sereno.weather.domain.model.Coordinates
import app.sereno.weather.domain.model.CurrentConditions
import app.sereno.weather.domain.model.ModelAvailability
import app.sereno.weather.domain.model.ModelSeries
import app.sereno.weather.domain.model.Place
import app.sereno.weather.domain.model.WeatherAlert
import app.sereno.weather.domain.model.WeatherModel

/**
 * Everything Sereno gets from the network sits behind one of these.
 *
 * The split is by *capability*, not by vendor, which is the point: a future
 * Meteomatics or Meteoblue integration implements [ForecastProvider] and joins
 * the blend without anything upstream of it changing. The repository composes
 * providers; it never names one.
 */
interface WeatherProviderInfo {
    /** Shown in the debug screen and the data-sources list. */
    val id: String
    val displayName: String
    val attribution: String
}

data class RawForecast(
    val series: Map<WeatherModel, ModelSeries>,
    val availability: List<ModelAvailability>,
    val current: CurrentConditions?,
    val timezone: String,
    val utcOffsetSeconds: Int,
    /** 15-minute precipitation, when the provider has it for this location. */
    val minutely: List<MinutePoint>,
    val latencyMs: Long,
)

data class MinutePoint(
    val epochSeconds: Long,
    val precipitation: Double?,
    val snowfall: Double?,
    val weatherCode: Int?,
)

interface ForecastProvider : WeatherProviderInfo {
    val supportedModels: List<WeatherModel>
    suspend fun fetch(coordinates: Coordinates, models: List<WeatherModel>, days: Int): RawForecast
}

interface GeocodingProvider : WeatherProviderInfo {
    suspend fun search(query: String, languageTag: String, limit: Int = 10): List<Place>
}

interface AirQualityProvider : WeatherProviderInfo {
    suspend fun fetch(coordinates: Coordinates): AirQuality?
}

interface AlertProvider : WeatherProviderInfo {
    /** Official alerts. Returning an empty list means "none", not "unavailable". */
    suspend fun fetch(coordinates: Coordinates, countryCode: String?): List<WeatherAlert>
}

/**
 * A radar imagery source.
 *
 * Kept separate from [ForecastProvider] because radar is observational rather
 * than predictive, and because its availability is regional: a source that can
 * only cover Italy still has to slot in cleanly next to one that covers Europe.
 */
interface RadarProvider : WeatherProviderInfo {
    suspend fun status(coordinates: Coordinates): RadarStatus
}

sealed interface RadarStatus {
    data class Available(
        val frames: List<RadarFrame>,
        val latestEpoch: Long,
    ) : RadarStatus

    data class OutOfCoverage(val reason: String) : RadarStatus
    data class Unavailable(val reason: String) : RadarStatus
}

data class RadarFrame(
    val epochSeconds: Long,
    val productType: String,
)
