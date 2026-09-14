package app.sereno.weather.data.provider

import app.sereno.weather.data.net.Http
import app.sereno.weather.domain.model.AirQuality
import app.sereno.weather.domain.model.Coordinates
import app.sereno.weather.domain.model.Place
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Place search.
 *
 * Results are re-ordered before display: Open-Meteo returns them by population,
 * which puts Rome, New York ahead of Rome, Italy for an Italian user typing
 * "Roma". Ranking exact name matches first, then the user's own country, fixes
 * the overwhelmingly common case without hiding anything.
 */
class OpenMeteoGeocodingProvider(
    private val http: Http,
    private val preferredCountry: () -> String?,
) : GeocodingProvider {

    override val id = "open-meteo-geocoding"
    override val displayName = "Open-Meteo Geocoding"
    override val attribution = "Geocoding by Open-Meteo.com"

    override suspend fun search(query: String, languageTag: String, limit: Int): List<Place> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val url = buildString {
            append("https://geocoding-api.open-meteo.com/v1/search")
            append("?name=").append(java.net.URLEncoder.encode(trimmed, "UTF-8"))
            append("&count=").append(limit.coerceIn(1, 20))
            append("&language=").append(languageTag.take(2))
            append("&format=json")
        }

        val root = http.json.parseToJsonElement(http.get(url).body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()

        val places = results.mapNotNull { element ->
            val obj = element.jsonObject
            val latitude = obj.double("latitude") ?: return@mapNotNull null
            val longitude = obj.double("longitude") ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            Place(
                id = obj["id"]?.jsonPrimitive?.longOrNull?.toString() ?: "$latitude,$longitude",
                name = name,
                admin = obj["admin1"]?.jsonPrimitive?.contentOrNullSafe(),
                country = obj["country"]?.jsonPrimitive?.contentOrNullSafe(),
                countryCode = obj["country_code"]?.jsonPrimitive?.contentOrNullSafe(),
                coordinates = Coordinates(latitude, longitude),
                timezone = obj["timezone"]?.jsonPrimitive?.contentOrNullSafe(),
            )
        }

        val home = preferredCountry()
        val needle = trimmed.lowercase()
        return places.sortedWith(
            compareByDescending<Place> { it.name.lowercase() == needle }
                .thenByDescending { home != null && it.countryCode.equals(home, ignoreCase = true) }
                .thenByDescending { it.name.lowercase().startsWith(needle) },
        )
    }
}

class OpenMeteoAirQualityProvider(private val http: Http) : AirQualityProvider {

    override val id = "open-meteo-air-quality"
    override val displayName = "Open-Meteo Air Quality"
    override val attribution = "Air quality by Open-Meteo.com / CAMS"

    override suspend fun fetch(coordinates: Coordinates): AirQuality? {
        val url = buildString {
            append("https://air-quality-api.open-meteo.com/v1/air-quality")
            append("?latitude=").append("%.4f".format(java.util.Locale.US, coordinates.latitude))
            append("&longitude=").append("%.4f".format(java.util.Locale.US, coordinates.longitude))
            append("&current=european_aqi,pm10,pm2_5,ozone,nitrogen_dioxide,sulphur_dioxide")
            append("&timezone=auto&timeformat=unixtime")
        }
        val root = http.json.parseToJsonElement(http.get(url).body).jsonObject
        val current = root["current"]?.jsonObject ?: return null
        return AirQuality(
            epochSeconds = current["time"]?.jsonPrimitive?.longOrNull ?: 0L,
            europeanAqi = current["european_aqi"]?.jsonPrimitive?.intOrNull,
            pm25 = current.double("pm2_5"),
            pm10 = current.double("pm10"),
            ozone = current.double("ozone"),
            nitrogenDioxide = current.double("nitrogen_dioxide"),
            sulphurDioxide = current.double("sulphur_dioxide"),
        )
    }
}
