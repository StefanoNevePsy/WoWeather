package app.sereno.weather.data.cache

import android.content.Context
import app.sereno.weather.domain.model.ForecastBundle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Last-known forecast per place, on disk.
 *
 * This is what makes Sereno usable on the underground. The rule it enforces is
 * that a cached forecast is *always* shown — stale data with an honest
 * "updated 3 hours ago" beats a spinner — and the age is surfaced rather than
 * hidden so the user can judge it.
 */
class ForecastCache(context: Context) {

    private val directory = File(context.filesDir, "forecast-cache").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(placeId: String): ForecastBundle? = withContext(Dispatchers.IO) {
        val file = fileFor(placeId)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(ForecastBundle.serializer(), file.readText()) }
            .getOrElse {
                // A cache entry written by an older schema is worthless, not fatal.
                file.delete()
                null
            }
            ?.copy(fromCache = true)
    }

    suspend fun write(bundle: ForecastBundle) = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(bundle.place.id)
            // Write-then-rename: a refresh killed midway must not leave a
            // half-written file that poisons the next cold start.
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(ForecastBundle.serializer(), bundle.copy(fromCache = false)))
            temporary.renameTo(file)
        }
        Unit
    }

    suspend fun evict(placeId: String) = withContext(Dispatchers.IO) {
        fileFor(placeId).delete()
        Unit
    }

    suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun fileFor(placeId: String) = File(directory, "${placeId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.json")
}
