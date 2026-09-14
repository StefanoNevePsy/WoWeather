package app.sereno.weather.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.sereno.weather.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/** What the map paints. */
enum class FieldMetric(val apiField: String) {
    Precipitation("precipitation"),
    Cloud("cloud_cover"),
    Wind("wind_gusts_10m"),
}

/**
 * A gridded forecast field over a geographic box, through time.
 *
 * Values are stored flat, indexed `hour * width * height + row * width + column`,
 * with row 0 at the *north* edge so the layout matches screen space directly.
 */
class WeatherField(
    val bounds: GeoBounds,
    val width: Int,
    val height: Int,
    val startEpoch: Long,
    val hourCount: Int,
    private val values: FloatArray,
    val metric: FieldMetric,
) {
    fun valueAt(hour: Int, column: Int, row: Int): Float {
        val h = hour.coerceIn(0, hourCount - 1)
        val c = column.coerceIn(0, width - 1)
        val r = row.coerceIn(0, height - 1)
        return values[h * width * height + r * width + c]
    }

    /** Bilinear sample in normalised grid space, for rendering between nodes. */
    fun sample(hour: Int, u: Float, v: Float): Float {
        val x = (u * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0

        val top = valueAt(hour, x0, y0) * (1 - fx) + valueAt(hour, x1, y0) * fx
        val bottom = valueAt(hour, x0, y1) * (1 - fx) + valueAt(hour, x1, y1) * fx
        return top * (1 - fy) + bottom * fy
    }

    val isEmpty: Boolean get() = values.all { it <= 0.001f }
}

/**
 * Samples the forecast on a grid to build a map layer.
 *
 * This is a genuinely different thing from a radar image and is labelled as
 * such in the UI: it is what the *models* expect, sampled at grid points and
 * interpolated, which means it extends hours into the future — something radar
 * fundamentally cannot do — while being coarser than radar in the present.
 *
 * Open-Meteo accepts a comma-separated list of coordinates and answers with one
 * object per location in a single response, so an entire 11x11 field costs one
 * request rather than 121.
 */
class WeatherFieldSource(private val http: Http) {

    suspend fun fetch(
        bounds: GeoBounds,
        metric: FieldMetric,
        gridSize: Int = DEFAULT_GRID,
        hours: Int = 24,
    ): WeatherField? = withContext(Dispatchers.IO) {
        val latitudes = mutableListOf<Double>()
        val longitudes = mutableListOf<Double>()

        // Row 0 is the north edge so the grid indexes like the screen.
        for (row in 0 until gridSize) {
            val latitude = bounds.north - bounds.latitudeSpan * row / (gridSize - 1).toDouble()
            for (column in 0 until gridSize) {
                val longitude = bounds.west + bounds.longitudeSpan * column / (gridSize - 1).toDouble()
                latitudes += latitude
                longitudes += longitude
            }
        }

        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(latitudes.joinToString(",") { fmt(it) })
            append("&longitude=").append(longitudes.joinToString(",") { fmt(it) })
            append("&hourly=").append(metric.apiField)
            append("&forecast_days=2&timeformat=unixtime&wind_speed_unit=kmh&precipitation_unit=mm")
        }

        val response = runCatching { http.get(url) }.getOrNull() ?: return@withContext null
        val root = runCatching { http.json.parseToJsonElement(response.body) }.getOrNull() ?: return@withContext null

        // One location returns an object; several return an array of them.
        val locations: List<JsonObject> = when (root) {
            is JsonArray -> root.map { it.jsonObject }
            is JsonObject -> listOf(root)
            else -> return@withContext null
        }
        if (locations.size != gridSize * gridSize) return@withContext null

        val firstHourly = locations.first()["hourly"]?.jsonObject ?: return@withContext null
        val times = firstHourly["time"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }
            ?: return@withContext null
        if (times.isEmpty()) return@withContext null

        val hourCount = minOf(hours, times.size)
        val values = FloatArray(hourCount * gridSize * gridSize)

        locations.forEachIndexed { index, location ->
            val series = location["hourly"]?.jsonObject
                ?.get(metric.apiField)?.jsonArray
                ?.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
                ?: return@forEachIndexed
            val row = index / gridSize
            val column = index % gridSize
            for (hour in 0 until hourCount) {
                values[hour * gridSize * gridSize + row * gridSize + column] =
                    (series.getOrNull(hour) ?: 0.0).toFloat()
            }
        }

        WeatherField(
            bounds = bounds,
            width = gridSize,
            height = gridSize,
            startEpoch = times.first(),
            hourCount = hourCount,
            values = values,
            metric = metric,
        )
    }

    private fun fmt(value: Double) = "%.3f".format(Locale.US, value)

    companion object {
        /** 121 points: dense enough to show structure, small enough for one URL. */
        const val DEFAULT_GRID = 11
    }
}

/**
 * Colour ramps for the map layers.
 *
 * The precipitation ramp climbs in lightness as well as hue, so heavier rain
 * reads as heavier even in greyscale, and starts fully transparent so a dry map
 * shows the basemap rather than a uniform blue wash.
 */
object FieldPalette {

    private data class Stop(val value: Float, val color: Int)

    private val precipitation = listOf(
        Stop(0.00f, 0x00000000),
        Stop(0.08f, 0x268FC0E0.toInt()),
        Stop(0.40f, 0x6B5FA8D8.toInt()),
        Stop(1.20f, 0x993E86C4.toInt()),
        Stop(3.00f, 0xB32F8F86.toInt()),
        Stop(6.00f, 0xC6C9A227.toInt()),
        Stop(12.0f, 0xD1D4552F.toInt()),
        Stop(25.0f, 0xDBA03060.toInt()),
    )

    private val cloud = listOf(
        Stop(0f, 0x00000000),
        Stop(25f, 0x1AFFFFFF),
        Stop(55f, 0x4DE8ECF0.toInt()),
        Stop(80f, 0x80D2D8DE.toInt()),
        Stop(100f, 0xA6BCC4CC.toInt()),
    )

    private val wind = listOf(
        Stop(0f, 0x00000000),
        Stop(20f, 0x262F8F86),
        Stop(40f, 0x66339E93),
        Stop(60f, 0x99C9A227.toInt()),
        Stop(85f, 0xBFD4552F.toInt()),
        Stop(120f, 0xD9A03060.toInt()),
    )

    fun colorFor(metric: FieldMetric, value: Float): Int {
        val stops = when (metric) {
            FieldMetric.Precipitation -> precipitation
            FieldMetric.Cloud -> cloud
            FieldMetric.Wind -> wind
        }
        if (value <= stops.first().value) return stops.first().color
        if (value >= stops.last().value) return stops.last().color
        for (i in 0 until stops.size - 1) {
            val low = stops[i]
            val high = stops[i + 1]
            if (value in low.value..high.value) {
                val fraction = (value - low.value) / (high.value - low.value)
                return blend(low.color, high.color, fraction)
            }
        }
        return stops.last().color
    }

    /** The legend stops, for the scale shown under the map. */
    fun legend(metric: FieldMetric): List<Pair<Float, Int>> = when (metric) {
        FieldMetric.Precipitation -> listOf(0.1f, 0.5f, 2f, 5f, 10f, 20f)
        FieldMetric.Cloud -> listOf(20f, 40f, 60f, 80f, 100f)
        FieldMetric.Wind -> listOf(20f, 40f, 60f, 80f, 110f)
    }.map { it to colorFor(metric, it) }

    private fun blend(a: Int, b: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val low = (a shr shift) and 0xFF
            val high = (b shr shift) and 0xFF
            return (low + (high - low) * f).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/**
 * Renders one hour of a field into a bitmap.
 *
 * The grid is only 11x11, so it is resampled here to [resolution] with bilinear
 * interpolation and then drawn scaled across the viewport, letting the GPU
 * smooth it the rest of the way. Interpolating in code first — rather than
 * relying purely on hardware filtering of an 11px image — is what keeps the
 * result looking like a weather field instead of a chequerboard.
 */
fun renderField(
    field: WeatherField,
    hour: Int,
    resolution: Int = 160,
): ImageBitmap? = runCatching {
    val pixels = IntArray(resolution * resolution)
    for (y in 0 until resolution) {
        val v = y / (resolution - 1).toFloat()
        for (x in 0 until resolution) {
            val u = x / (resolution - 1).toFloat()
            val value = field.sample(hour, u, v)
            pixels[y * resolution + x] = FieldPalette.colorFor(field.metric, value)
        }
    }
    Bitmap.createBitmap(pixels, resolution, resolution, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
