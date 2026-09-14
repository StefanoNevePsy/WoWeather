package app.sereno.weather.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@Serializable
data class Coordinates(val latitude: Double, val longitude: Double) {
    /** Rounded to ~1 km. Used as a cache key so tiny GPS jitter is not a cache miss. */
    fun cacheKey(): String =
        "${(latitude * 100).roundToInt()}_${(longitude * 100).roundToInt()}"

    fun distanceKmTo(other: Coordinates): Double {
        val dLat = (other.latitude - latitude) * 111.32
        val dLon = (other.longitude - longitude) * 111.32 * cos(Math.toRadians(latitude))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }
}

@Serializable
data class Place(
    val id: String,
    val name: String,
    /** Province / region, e.g. "Reggio Emilia". Shown under the name in search. */
    val admin: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val coordinates: Coordinates,
    val timezone: String? = null,
    /** True for the single entry that tracks the device's location. */
    val isCurrentLocation: Boolean = false,
) {
    val subtitle: String?
        get() = listOfNotNull(admin, country).distinct().joinToString(", ").ifBlank { null }

    companion object {
        const val CURRENT_LOCATION_ID = "__current__"

        fun forCurrentLocation(coordinates: Coordinates, name: String) = Place(
            id = CURRENT_LOCATION_ID,
            name = name,
            coordinates = coordinates,
            isCurrentLocation = true,
        )
    }
}

/**
 * The numerical weather prediction models Sereno can draw on.
 *
 * [apiId] is Open-Meteo's model identifier; Open-Meteo can serve several models
 * in one request and suffixes each returned series with it, which is what makes
 * the whole multi-model design affordable on mobile data.
 *
 * [nativeResolutionKm] and [horizonHours] are the two properties that actually
 * drive blending: a 2.2 km regional model is worth far more than a 25 km global
 * one tomorrow morning, and worth nothing at all at day 6 because it does not
 * run that far.
 */
@Serializable
enum class WeatherModel(
    val apiId: String,
    val displayName: String,
    val shortName: String,
    val centre: String,
    val nativeResolutionKm: Double,
    val horizonHours: Int,
    val isRegional: Boolean,
    val providesPrecipProbability: Boolean,
) {
    Icon2I(
        apiId = "italia_meteo_arpae_icon_2i",
        displayName = "ICON-2I",
        shortName = "2I",
        centre = "ItaliaMeteo · ARPAE",
        nativeResolutionKm = 2.2,
        horizonHours = 72,
        isRegional = true,
        providesPrecipProbability = false,
    ),
    AromeHd(
        apiId = "meteofrance_arome_france_hd",
        displayName = "AROME HD",
        shortName = "AR",
        centre = "Météo-France",
        nativeResolutionKm = 1.5,
        horizonHours = 48,
        isRegional = true,
        providesPrecipProbability = false,
    ),
    IconEu(
        apiId = "icon_eu",
        displayName = "ICON-EU",
        shortName = "EU",
        centre = "DWD",
        nativeResolutionKm = 7.0,
        horizonHours = 120,
        isRegional = true,
        providesPrecipProbability = false,
    ),
    IconGlobal(
        apiId = "icon_global",
        displayName = "ICON",
        shortName = "IC",
        centre = "DWD",
        nativeResolutionKm = 11.0,
        horizonHours = 180,
        isRegional = false,
        providesPrecipProbability = false,
    ),
    EcmwfIfs(
        apiId = "ecmwf_ifs025",
        displayName = "ECMWF IFS",
        shortName = "IFS",
        centre = "ECMWF",
        nativeResolutionKm = 25.0,
        horizonHours = 360,
        isRegional = false,
        providesPrecipProbability = false,
    ),
    EcmwfAifs(
        apiId = "ecmwf_aifs025_single",
        displayName = "ECMWF AIFS",
        shortName = "AIFS",
        centre = "ECMWF · machine learning",
        nativeResolutionKm = 25.0,
        horizonHours = 360,
        isRegional = false,
        providesPrecipProbability = false,
    ),
    Gfs(
        apiId = "gfs_seamless",
        displayName = "GFS",
        shortName = "GFS",
        centre = "NOAA",
        nativeResolutionKm = 13.0,
        horizonHours = 384,
        isRegional = false,
        providesPrecipProbability = true,
    );

    companion object {
        /** The set requested on every forecast call. */
        val requested: List<WeatherModel> = entries.toList()

        fun byApiId(id: String): WeatherModel? = entries.firstOrNull { it.apiId == id }
    }
}

// ---------------------------------------------------------------------------
// Units
// ---------------------------------------------------------------------------

@Serializable
enum class TemperatureUnit(val symbol: String) {
    Celsius("°"), Fahrenheit("°");

    fun fromCelsius(c: Double): Double = if (this == Celsius) c else c * 9.0 / 5.0 + 32.0
    /** Degrees in this unit that correspond to one Celsius degree. */
    val deltaPerCelsius: Double get() = if (this == Celsius) 1.0 else 1.8
}

@Serializable
enum class SpeedUnit(val symbol: String) {
    KmH("km/h"), Ms("m/s"), Mph("mph"), Knots("kn");

    fun fromKmh(v: Double): Double = when (this) {
        KmH -> v
        Ms -> v / 3.6
        Mph -> v / 1.609344
        Knots -> v / 1.852
    }
}

@Serializable
enum class PrecipUnit(val symbol: String) {
    Mm("mm"), Inch("in");

    fun fromMm(v: Double): Double = if (this == Mm) v else v / 25.4
}

@Serializable
enum class PressureUnit(val symbol: String) {
    HPa("hPa"), InHg("inHg"), MmHg("mmHg");

    fun fromHpa(v: Double): Double = when (this) {
        HPa -> v
        InHg -> v * 0.02952998
        MmHg -> v * 0.7500617
    }
}

/** Compass sector for a wind bearing, in the 8-point form people actually read. */
fun bearingToSector(degrees: Double): Int {
    val d = ((degrees % 360) + 360) % 360
    return (((d + 22.5) / 45).toInt()) % 8
}

fun angleDifference(a: Double, b: Double): Double {
    val diff = abs(((a - b) % 360 + 540) % 360 - 180)
    return diff
}
