package app.sereno.weather.domain.model

import kotlinx.serialization.Serializable

/**
 * One model's raw output for one hour. Every field is nullable on purpose:
 * models differ in what they publish, and Sereno never fabricates a value it
 * was not given. A null here travels all the way to the UI as "—".
 */
@Serializable
data class HourPoint(
    val epochSeconds: Long,
    val temperature: Double? = null,
    val apparentTemperature: Double? = null,
    val dewPoint: Double? = null,
    val humidity: Double? = null,
    val precipitation: Double? = null,
    val rain: Double? = null,
    val showers: Double? = null,
    val snowfall: Double? = null,
    val precipitationProbability: Double? = null,
    val windSpeed: Double? = null,
    val windGust: Double? = null,
    val windDirection: Double? = null,
    val cloudCover: Double? = null,
    val pressure: Double? = null,
    val visibility: Double? = null,
    val uvIndex: Double? = null,
    val weatherCode: Int? = null,
    val isDay: Boolean? = null,
    val cape: Double? = null,
    val freezingLevel: Double? = null,
)

@Serializable
data class DayPoint(
    val epochSeconds: Long,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val precipitationSum: Double? = null,
    val rainSum: Double? = null,
    val snowfallSum: Double? = null,
    val precipitationHours: Double? = null,
    val precipitationProbabilityMax: Double? = null,
    val windSpeedMax: Double? = null,
    val windGustMax: Double? = null,
    val windDirectionDominant: Double? = null,
    val uvIndexMax: Double? = null,
    val weatherCode: Int? = null,
    val sunriseEpoch: Long? = null,
    val sunsetEpoch: Long? = null,
    val daylightSeconds: Double? = null,
)

/** Everything one model returned for one location. */
@Serializable
data class ModelSeries(
    val model: WeatherModel,
    val hourly: List<HourPoint> = emptyList(),
    val daily: List<DayPoint> = emptyList(),
    /** When this model run was initialised, if the provider told us. */
    val runInitEpoch: Long? = null,
    val available: Boolean = true,
) {
    fun hourAt(epoch: Long): HourPoint? = hourly.firstOrNull { it.epochSeconds == epoch }
}

// ---------------------------------------------------------------------------
// Confidence
// ---------------------------------------------------------------------------

@Serializable
enum class ConfidenceBand { VeryHigh, High, Moderate, Low, VeryLow }

/**
 * Why a confidence score came out the way it did.
 *
 * These are surfaced verbatim in the UI, because "72%" on its own teaches the
 * user nothing. Each driver carries the quantity that produced it so the debug
 * screen can show the arithmetic.
 */
@Serializable
enum class ConfidenceFactor { TemperatureSpread, PrecipitationAgreement, AmountSpread, WindSpread, ModelCount, LeadTime }

@Serializable
data class ConfidenceDriver(
    val factor: ConfidenceFactor,
    /** 0..1, where 1 means this factor is fully supportive of the forecast. */
    val score: Float,
    /** The raw measurement, e.g. a standard deviation in °C. */
    val value: Double,
    /** How much this factor moved the final score. */
    val weight: Float,
)

@Serializable
data class Confidence(
    val score: Float,
    val band: ConfidenceBand,
    val modelCount: Int,
    val drivers: List<ConfidenceDriver> = emptyList(),
) {
    val percent: Int get() = (score * 100).toInt().coerceIn(0, 100)

    companion object {
        fun bandFor(score: Float): ConfidenceBand = when {
            score >= 0.86f -> ConfidenceBand.VeryHigh
            score >= 0.70f -> ConfidenceBand.High
            score >= 0.52f -> ConfidenceBand.Moderate
            score >= 0.34f -> ConfidenceBand.Low
            else -> ConfidenceBand.VeryLow
        }

        val unknown = Confidence(0f, ConfidenceBand.VeryLow, 0)
    }
}

/** How a blended value was arrived at. Drives the "is this real?" disclosure. */
@Serializable
enum class Provenance {
    /** Weighted blend of two or more models. */
    Blended,

    /** Only one model offered this field. */
    SingleModel,

    /** Computed by Sereno from other fields (always labelled in the UI). */
    Derived,

    /** No model offered this field. */
    Missing,
}

@Serializable
data class Sourced<T>(
    val value: T?,
    val provenance: Provenance,
    val contributors: List<WeatherModel> = emptyList(),
) {
    val isReal: Boolean get() = value != null && provenance != Provenance.Missing
}

// ---------------------------------------------------------------------------
// Blended output
// ---------------------------------------------------------------------------

/**
 * The forecast Sereno actually shows: one hour, blended across models, with the
 * spread that produced it kept alongside so the UI can show uncertainty rather
 * than hide it.
 */
@Serializable
data class BlendedHour(
    val epochSeconds: Long,
    val temperature: Double?,
    val temperatureSpread: Double,
    val apparentTemperature: Double?,
    val precipitation: Double?,
    val precipitationSpread: Double,
    /** Blended where models publish it, otherwise model agreement (see [probabilityProvenance]). */
    val precipitationProbability: Double?,
    val probabilityProvenance: Provenance,
    val snowfall: Double?,
    val windSpeed: Double?,
    val windSpeedSpread: Double,
    val windGust: Double?,
    val windDirection: Double?,
    val cloudCover: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val visibility: Double?,
    val uvIndex: Double?,
    val dewPoint: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val confidence: Confidence,
    val contributors: List<WeatherModel>,
) {
    val isWet: Boolean get() = (precipitation ?: 0.0) >= WET_THRESHOLD_MM

    companion object {
        /** Below this, an hour is dry for every purpose in the app. */
        const val WET_THRESHOLD_MM = 0.1
    }
}

@Serializable
data class BlendedDay(
    val epochSeconds: Long,
    val temperatureMax: Double?,
    val temperatureMin: Double?,
    val temperatureMaxSpread: Double,
    val precipitationSum: Double?,
    val precipitationSumSpread: Double,
    val precipitationProbabilityMax: Double?,
    val probabilityProvenance: Provenance,
    val snowfallSum: Double?,
    val windSpeedMax: Double?,
    val windGustMax: Double?,
    val windDirectionDominant: Double?,
    val uvIndexMax: Double?,
    val weatherCode: Int?,
    val sunriseEpoch: Long?,
    val sunsetEpoch: Long?,
    val confidence: Confidence,
    val contributors: List<WeatherModel>,
)

@Serializable
data class CurrentConditions(
    val epochSeconds: Long,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val precipitation: Double?,
    val windSpeed: Double?,
    val windGust: Double?,
    val windDirection: Double?,
    val cloudCover: Double?,
    val pressure: Double?,
    val visibility: Double?,
    val uvIndex: Double?,
    val dewPoint: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    /** Which observation-ish source this came from. */
    val source: String,
)

// ---------------------------------------------------------------------------
// Air quality, alerts
// ---------------------------------------------------------------------------

@Serializable
data class AirQuality(
    val epochSeconds: Long,
    val europeanAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val nitrogenDioxide: Double?,
    val sulphurDioxide: Double?,
    val pollenGrass: Double? = null,
    val pollenBirch: Double? = null,
)

@Serializable
enum class AlertSeverity { Advisory, Watch, Warning, Severe }

@Serializable
enum class AlertKind { Thunderstorm, Hail, Wind, Rain, Snow, Ice, Heat, Cold, Fog, Other }

@Serializable
data class WeatherAlert(
    val id: String,
    val kind: AlertKind,
    val severity: AlertSeverity,
    val headline: String,
    val detail: String,
    val startEpoch: Long,
    val endEpoch: Long,
    /** "Sereno" for locally-derived alerts, or the issuing authority. */
    val issuer: String,
    val derived: Boolean,
)

// ---------------------------------------------------------------------------
// Nowcast
// ---------------------------------------------------------------------------

@Serializable
enum class NowcastKind { Dry, StartingSoon, Ongoing, Stopping, Intermittent, Unknown }

/**
 * The answer to "is it about to rain?".
 *
 * [startMinutes]/[endMinutes] are deliberately a *range*, because the underlying
 * hourly data cannot support a single-minute claim. The UI renders them as
 * "in 38–55 min" and never as "in 47 min".
 */
@Serializable
data class Nowcast(
    val kind: NowcastKind,
    val startMinutesLow: Int? = null,
    val startMinutesHigh: Int? = null,
    val endMinutesLow: Int? = null,
    val endMinutesHigh: Int? = null,
    val peakIntensityMmH: Double? = null,
    val isSnow: Boolean = false,
    val confidence: Confidence = Confidence.unknown,
    /** Horizon actually examined, in hours. */
    val horizonHours: Int = 3,
)

// ---------------------------------------------------------------------------
// The whole thing
// ---------------------------------------------------------------------------

@Serializable
data class ModelAvailability(
    val model: WeatherModel,
    val available: Boolean,
    val hoursReturned: Int,
    val runInitEpoch: Long? = null,
    val note: String? = null,
)

@Serializable
data class ForecastBundle(
    val place: Place,
    val fetchedAtEpoch: Long,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val current: CurrentConditions?,
    val hours: List<BlendedHour>,
    val days: List<BlendedDay>,
    val models: Map<String, ModelSeries>,
    val availability: List<ModelAvailability>,
    val airQuality: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    val nowcast: Nowcast? = null,
    /** Round-trip time of the forecast call, for the debug screen. */
    val latencyMs: Long = 0,
    val fromCache: Boolean = false,
) {
    fun seriesFor(model: WeatherModel): ModelSeries? = models[model.apiId]

    val availableModels: List<WeatherModel>
        get() = availability.filter { it.available }.map { it.model }

    fun ageMinutes(nowEpoch: Long): Long = (nowEpoch - fetchedAtEpoch) / 60
}
