package app.sereno.weather.data.provider

import app.sereno.weather.data.net.Http
import app.sereno.weather.domain.model.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Italy's Dipartimento della Protezione Civile national radar mosaic.
 *
 * The DPC's public API is the authoritative radar source for Italy, and it is
 * what an Italian user will compare Sereno against. It is also outside our
 * control and occasionally unreachable, so this provider is written to fail
 * *informatively*: every failure path returns a [RadarStatus] that names the
 * reason, and the map screen renders that reason rather than an empty grey box.
 *
 * Coverage is checked before the network call. Asking Rome's radar about Lisbon
 * and reporting "unavailable" would be misleading — it is not unavailable, it
 * simply does not cover that place, and those are different messages.
 */
class RadarDpcProvider(private val http: Http) : RadarProvider {

    override val id = "radar-dpc"
    override val displayName = "Radar Protezione Civile"
    override val attribution = "Radar mosaic © Dipartimento della Protezione Civile"

    /** Products the mosaic publishes, in the order the layer picker shows them. */
    enum class Product(val code: String, val label: String, val description: String) {
        Sri("SRI", "Intensità", "Surface Rainfall Intensity — mm/h al suolo"),
        Vmi("VMI", "Riflettività", "Vertically Integrated Maximum reflectivity"),
        Amv("AMV", "Accumuli", "Accumulated rainfall"),
        Ltg("LTG", "Fulmini", "Lightning strikes"),
    }

    override suspend fun status(coordinates: Coordinates): RadarStatus {
        if (!coversItaly(coordinates)) {
            return RadarStatus.OutOfCoverage("Il radar DPC copre il territorio italiano")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE/findLastProductByType?type=${Product.Sri.code}"
                val root = http.json.parseToJsonElement(http.get(url).body).jsonObject
                // The API reports times in epoch milliseconds.
                val millis = root["time"]?.jsonPrimitive?.longOrNull
                    ?: root["lastProductDate"]?.jsonPrimitive?.longOrNull
                if (millis == null) {
                    RadarStatus.Unavailable("Risposta radar non riconosciuta")
                } else {
                    val latest = millis / 1000
                    RadarStatus.Available(
                        frames = listOf(RadarFrame(latest, Product.Sri.code)),
                        latestEpoch = latest,
                    )
                }
            }.getOrElse { error ->
                RadarStatus.Unavailable(error.message ?: "Servizio radar non raggiungibile")
            }
        }
    }

    private fun coversItaly(coordinates: Coordinates): Boolean =
        coordinates.latitude in 35.0..47.6 && coordinates.longitude in 6.0..19.2

    companion object {
        private const val BASE = "https://radar-api.protezionecivile.it/wide/product"
    }
}
