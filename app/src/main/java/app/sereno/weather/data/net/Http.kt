package app.sereno.weather.data.net

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/** Round-trip metadata kept for the debug screen. */
data class HttpResult(
    val body: String,
    val latencyMs: Long,
    val fromNetwork: Boolean,
    val url: String,
)

class HttpException(val code: Int, val url: String, message: String) : IOException(message)

/**
 * The single HTTP client for the whole app.
 *
 * Timeouts are short on purpose. A weather app that spins for thirty seconds on
 * a train is worse than one that fails in six and shows the cached forecast, so
 * every call is expected to either answer quickly or lose to the cache.
 */
class Http(cacheDir: File) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 8 MB of HTTP cache means a cold start on a flaky connection still
        // paints real data while the refresh is in flight.
        .cache(Cache(File(cacheDir, "http"), 8L * 1024 * 1024))
        .build()

    suspend fun get(url: String): HttpResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpException(response.code, url, "HTTP ${response.code} for $url")
            }
            HttpResult(
                body = body,
                latencyMs = elapsed,
                fromNetwork = response.networkResponse != null,
                url = url,
            )
        }
    }

    /** Raw bytes, for map tiles. */
    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url, "HTTP ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    companion object {
        // Open-Meteo asks non-commercial clients to identify themselves.
        const val USER_AGENT = "Sereno/1.0 (open-source weather app; Android)"
    }
}
