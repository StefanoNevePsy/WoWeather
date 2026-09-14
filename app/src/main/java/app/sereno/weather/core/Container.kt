package app.sereno.weather.core

import android.content.Context
import app.sereno.weather.data.WeatherRepository
import app.sereno.weather.data.cache.ForecastCache
import app.sereno.weather.data.location.LocationSource
import app.sereno.weather.data.net.Http
import app.sereno.weather.data.places.PlacesStore
import app.sereno.weather.data.prefs.SettingsStore
import app.sereno.weather.data.provider.OpenMeteoAirQualityProvider
import app.sereno.weather.data.provider.OpenMeteoGeocodingProvider
import app.sereno.weather.data.provider.OpenMeteoProvider
import app.sereno.weather.data.provider.RadarDpcProvider

/**
 * Dependency wiring, done by hand.
 *
 * Sereno has one graph, built once, with no scopes and no runtime resolution,
 * so a DI framework would buy nothing here but an annotation processor and a
 * slower build. Everything is lazy, so nothing touches disk or the network
 * until something actually asks for it.
 */
class Container(private val context: Context) {

    val http: Http by lazy { Http(context.cacheDir) }

    val settings: SettingsStore by lazy { SettingsStore(context) }
    val places: PlacesStore by lazy { PlacesStore(context) }
    val location: LocationSource by lazy { LocationSource(context) }

    val forecastProvider: OpenMeteoProvider by lazy { OpenMeteoProvider(http) }
    val airQualityProvider: OpenMeteoAirQualityProvider by lazy { OpenMeteoAirQualityProvider(http) }
    val radarProvider: RadarDpcProvider by lazy { RadarDpcProvider(http) }

    val geocodingProvider: OpenMeteoGeocodingProvider by lazy {
        OpenMeteoGeocodingProvider(http) {
            // Bias search towards the user's own country so "Roma" finds Rome,
            // Italy before Rome, New York.
            context.resources.configuration.locales[0]?.country
        }
    }

    val cache: ForecastCache by lazy { ForecastCache(context) }

    val repository: WeatherRepository by lazy {
        WeatherRepository(
            forecastProvider = forecastProvider,
            airQualityProvider = airQualityProvider,
            radarProvider = radarProvider,
            cache = cache,
        )
    }

    /** Every source the app talks to, for the data-sources and debug screens. */
    val providerInfos by lazy {
        listOf(forecastProvider, geocodingProvider, airQualityProvider, radarProvider)
    }
}
