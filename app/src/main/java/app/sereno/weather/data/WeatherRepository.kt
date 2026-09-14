package app.sereno.weather.data

import app.sereno.weather.data.cache.ForecastCache
import app.sereno.weather.data.provider.AirQualityProvider
import app.sereno.weather.data.provider.ForecastProvider
import app.sereno.weather.data.provider.RadarProvider
import app.sereno.weather.data.provider.RadarStatus
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.Nowcast
import app.sereno.weather.domain.model.Place
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.domain.nowcast.FineNowcaster
import app.sereno.weather.domain.nowcast.Nowcaster
import app.sereno.weather.domain.severe.SevereEngine
import app.sereno.weather.domain.synthesis.ConfidenceEngine
import app.sereno.weather.domain.synthesis.Synthesizer
import app.sereno.weather.i18n.Copy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What the UI observes while a forecast loads. */
sealed interface ForecastResource {
    data object Loading : ForecastResource

    data class Data(
        val bundle: ForecastBundle,
        /** True while a refresh is still in flight behind this cached copy. */
        val refreshing: Boolean,
    ) : ForecastResource

    data class Failed(
        val lastKnown: ForecastBundle?,
        val cause: Throwable,
    ) : ForecastResource
}

/**
 * Composes providers into the forecast the app shows.
 *
 * The repository is the only place that knows more than one provider exists.
 * Everything above it consumes a [ForecastBundle] and cannot tell whether it
 * came from one model or seven, from the network or from disk.
 */
class WeatherRepository(
    private val forecastProvider: ForecastProvider,
    private val airQualityProvider: AirQualityProvider,
    private val radarProvider: RadarProvider,
    private val cache: ForecastCache,
    private val nowEpoch: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    /**
     * Emits the cached forecast immediately (if any), then the refreshed one.
     *
     * This ordering is the whole offline story: the screen paints real data on
     * the first frame and quietly improves, instead of showing a spinner over
     * a forecast we already have.
     */
    fun stream(
        place: Place,
        copy: Copy,
        forceRefresh: Boolean = false,
    ): Flow<ForecastResource> = flow {
        val cached = cache.read(place.id)

        if (cached != null) {
            emit(ForecastResource.Data(cached, refreshing = true))
            if (!forceRefresh && isFresh(cached)) {
                emit(ForecastResource.Data(cached, refreshing = false))
                return@flow
            }
        } else {
            emit(ForecastResource.Loading)
        }

        try {
            val fresh = fetch(place, copy)
            cache.write(fresh)
            emit(ForecastResource.Data(fresh, refreshing = false))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            emit(ForecastResource.Failed(cached, error))
        }
    }

    suspend fun fetch(place: Place, copy: Copy, days: Int = 16): ForecastBundle = coroutineScope {
        // Air quality is a nice-to-have: it must never be able to fail a refresh,
        // so it runs alongside and its failure resolves to null.
        val airDeferred = async { runCatching { airQualityProvider.fetch(place.coordinates) }.getOrNull() }

        val raw = forecastProvider.fetch(place.coordinates, WeatherModel.requested, days)
        val now = nowEpoch()

        val synthesis = Synthesizer.synthesize(raw.series, now)

        val nowcast = buildNowcast(raw, synthesis, now)

        val alerts = SevereEngine.derive(synthesis.hours, now, copy, raw.utcOffsetSeconds)

        ForecastBundle(
            place = place,
            fetchedAtEpoch = now,
            timezone = raw.timezone,
            utcOffsetSeconds = raw.utcOffsetSeconds,
            current = raw.current,
            hours = synthesis.hours,
            days = synthesis.days,
            models = raw.series.mapKeys { it.key.apiId },
            availability = raw.availability,
            airQuality = airDeferred.await(),
            alerts = alerts,
            nowcast = nowcast,
            latencyMs = raw.latencyMs,
            fromCache = false,
        )
    }

    private fun buildNowcast(
        raw: app.sereno.weather.data.provider.RawForecast,
        synthesis: Synthesizer.Result,
        now: Long,
    ): Nowcast {
        val nearTerm = synthesis.hours.filter { it.epochSeconds in now..(now + 4 * 3600) }
        val confidence = ConfidenceEngine.aggregate(nearTerm.map { it.confidence })

        // Prefer the 15-minute series where the provider has it for this
        // location; fall back to interpolated hourly everywhere else.
        if (raw.minutely.isNotEmpty()) {
            val samples = raw.minutely.map { it.epochSeconds to (it.precipitation ?: 0.0) }
            val snowy = raw.minutely.any { (it.snowfall ?: 0.0) > 0.02 }
            FineNowcaster.compute(samples, now, confidence, snowy)?.let { return it }
        }
        return Nowcaster.compute(synthesis.hours, now)
    }

    suspend fun radarStatus(place: Place): RadarStatus = radarProvider.status(place.coordinates)

    suspend fun cachedOnly(place: Place): ForecastBundle? = cache.read(place.id)

    suspend fun clearCache() = cache.clear()

    suspend fun cacheSizeBytes(): Long = cache.totalSizeBytes()

    /** Forecasts age out after 15 minutes; models do not update faster than that. */
    private fun isFresh(bundle: ForecastBundle): Boolean =
        nowEpoch() - bundle.fetchedAtEpoch < FRESHNESS_SECONDS

    companion object {
        const val FRESHNESS_SECONDS = 15 * 60L
    }
}
