package app.sereno.weather.ui.map

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.sereno.weather.data.net.Http
import app.sereno.weather.domain.model.Coordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** The Web Mercator projection, in the usual 256px-tile form. */
object Mercator {

    const val TILE_SIZE = 256.0

    fun worldSize(zoom: Double): Double = TILE_SIZE * 2.0.pow(zoom)

    fun worldX(longitude: Double, zoom: Double): Double =
        (longitude + 180.0) / 360.0 * worldSize(zoom)

    fun worldY(latitude: Double, zoom: Double): Double {
        // Clamped to the Mercator limit; beyond it the projection diverges.
        val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
        val radians = Math.toRadians(clamped)
        val y = ln(tan(radians) + 1.0 / cos(radians))
        return (1.0 - y / PI) / 2.0 * worldSize(zoom)
    }

    fun longitudeAt(worldX: Double, zoom: Double): Double =
        worldX / worldSize(zoom) * 360.0 - 180.0

    fun latitudeAt(worldY: Double, zoom: Double): Double {
        val n = PI - 2.0 * PI * worldY / worldSize(zoom)
        return Math.toDegrees(atan(sinh(n)))
    }
}

/** Where the map is looking. */
@Immutable
data class MapCamera(
    val center: Coordinates,
    val zoom: Double,
) {
    fun clampedZoom(): MapCamera = copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** Screen position of a coordinate, given a viewport size in pixels. */
    fun project(coordinates: Coordinates, viewportWidth: Float, viewportHeight: Float): Offset {
        val centreX = Mercator.worldX(center.longitude, zoom)
        val centreY = Mercator.worldY(center.latitude, zoom)
        val x = Mercator.worldX(coordinates.longitude, zoom)
        val y = Mercator.worldY(coordinates.latitude, zoom)
        return Offset(
            (x - centreX + viewportWidth / 2.0).toFloat(),
            (y - centreY + viewportHeight / 2.0).toFloat(),
        )
    }

    /** The coordinate under a screen position. */
    fun unproject(position: Offset, viewportWidth: Float, viewportHeight: Float): Coordinates {
        val centreX = Mercator.worldX(center.longitude, zoom)
        val centreY = Mercator.worldY(center.latitude, zoom)
        val worldX = centreX + (position.x - viewportWidth / 2.0)
        val worldY = centreY + (position.y - viewportHeight / 2.0)
        return Coordinates(
            latitude = Mercator.latitudeAt(worldY, zoom),
            longitude = Mercator.longitudeAt(worldX, zoom),
        )
    }

    /** Pans by a screen-space delta. */
    fun panBy(delta: Offset): MapCamera {
        val centreX = Mercator.worldX(center.longitude, zoom) - delta.x
        val centreY = Mercator.worldY(center.latitude, zoom) - delta.y
        return copy(
            center = Coordinates(
                latitude = Mercator.latitudeAt(centreY, zoom).coerceIn(-84.0, 84.0),
                longitude = wrapLongitude(Mercator.longitudeAt(centreX, zoom)),
            ),
        )
    }

    fun zoomBy(factor: Float): MapCamera =
        copy(zoom = (zoom + ln(factor.toDouble()) / ln(2.0))).clampedZoom()

    /** The geographic bounds currently visible. */
    fun bounds(viewportWidth: Float, viewportHeight: Float): GeoBounds {
        val topLeft = unproject(Offset(0f, 0f), viewportWidth, viewportHeight)
        val bottomRight = unproject(Offset(viewportWidth, viewportHeight), viewportWidth, viewportHeight)
        return GeoBounds(
            south = bottomRight.latitude,
            west = topLeft.longitude,
            north = topLeft.latitude,
            east = bottomRight.longitude,
        )
    }

    companion object {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 11.0
    }
}

private fun wrapLongitude(longitude: Double): Double {
    var value = longitude
    while (value > 180.0) value -= 360.0
    while (value < -180.0) value += 360.0
    return value
}

@Immutable
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val latitudeSpan: Double get() = north - south
    val longitudeSpan: Double get() = east - west

    /** True when [other] differs enough to be worth refetching the field for. */
    fun differsMateriallyFrom(other: GeoBounds): Boolean {
        val latShift = kotlin.math.abs(center().first - other.center().first)
        val lonShift = kotlin.math.abs(center().second - other.center().second)
        val spanRatio = latitudeSpan / other.latitudeSpan.coerceAtLeast(1e-6)
        return latShift > latitudeSpan * 0.25 ||
            lonShift > longitudeSpan * 0.25 ||
            spanRatio < 0.7 || spanRatio > 1.4
    }

    fun center(): Pair<Double, Double> = (south + north) / 2.0 to (west + east) / 2.0
}

data class TileKey(val zoom: Int, val x: Int, val y: Int)

/**
 * Where basemap tiles come from.
 *
 * Kept as an explicit, swappable thing after a hard lesson: the first release
 * used CARTO's public basemap endpoint, which started stamping "API KEY
 * REQUIRED" across every tile. Against a dark basemap that turned the whole map
 * black with a watermark over it. A tile provider is somebody else's service
 * and can change its terms overnight, so changing provider must be a one-line
 * edit and running without one must remain survivable.
 */
enum class BasemapStyle(val attribution: String) {
    /**
     * Esri's Gray Canvas, which is purpose-built as a backdrop for data. It is
     * label-light and almost colourless, which is exactly what a precipitation
     * field needs behind it — a full-colour street map would fight it.
     */
    EsriGrayCanvas("© Esri · OpenStreetMap contributors");

    fun url(key: TileKey, dark: Boolean): String = when (this) {
        EsriGrayCanvas -> {
            val layer = if (dark) "World_Dark_Gray_Base" else "World_Light_Gray_Base"
            // Note the y/x order: ArcGIS REST puts row before column, unlike the
            // usual {z}/{x}/{y} slippy-map convention.
            "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/" +
                "$layer/MapServer/tile/${key.zoom}/${key.y}/${key.x}"
        }
    }
}

/** How the basemap is doing, so the UI can say something true about it. */
enum class BasemapHealth { Idle, Loading, Ok, Failing }

/**
 * Raster basemap tiles.
 *
 * Tiles are kept in memory and backed by the shared OkHttp disk cache, and
 * in-flight loads are capped so that a fast pan cannot open fifty sockets at
 * once. Failures are counted rather than swallowed: the map reports when the
 * basemap is unavailable instead of silently showing an empty rectangle.
 */
class TileSource(
    private val http: Http,
    private val scope: CoroutineScope,
    private val style: BasemapStyle = BasemapStyle.EsriGrayCanvas,
) {
    private val tiles = mutableStateMapOf<TileKey, ImageBitmap>()
    private val inFlight = mutableSetOf<TileKey>()
    private val failed = mutableSetOf<TileKey>()
    private val gate = Semaphore(6)

    var loaded by mutableIntStateOf(0)
        private set
    var failures by mutableIntStateOf(0)
        private set

    val attribution: String get() = style.attribution

    val health: BasemapHealth
        get() = when {
            loaded > 0 -> BasemapHealth.Ok
            failures >= 3 -> BasemapHealth.Failing
            inFlight.isNotEmpty() -> BasemapHealth.Loading
            else -> BasemapHealth.Idle
        }

    fun tile(key: TileKey, dark: Boolean): ImageBitmap? {
        tiles[key]?.let { return it }
        if (key in inFlight || key in failed) return null

        inFlight += key
        scope.launch {
            val bitmap = gate.withPermit { load(key, dark) }
            inFlight -= key
            if (bitmap != null) {
                tiles[key] = bitmap
                loaded += 1
            } else {
                failed += key
                failures += 1
            }
        }
        return null
    }

    private suspend fun load(key: TileKey, dark: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = http.getBytes(style.url(key, dark))
            if (bytes.isEmpty()) return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    /** Clears the failure memo so a pan back over a tile retries it. */
    fun retryFailures() {
        failed.clear()
        failures = 0
    }

    fun memoryTileCount(): Int = tiles.size
}

/** Which tiles cover the viewport at this camera. */
fun visibleTiles(camera: MapCamera, viewportWidth: Float, viewportHeight: Float): List<Pair<TileKey, Offset>> {
    val zoomLevel = camera.zoom.toInt().coerceIn(MapCamera.MIN_ZOOM.toInt(), MapCamera.MAX_ZOOM.toInt())
    val tileCount = 1 shl zoomLevel

    // Tiles are drawn at the integer zoom and scaled to the fractional one, so
    // pinching is continuous rather than snapping between levels.
    val scale = 2.0.pow(camera.zoom - zoomLevel)
    val tileScreenSize = (Mercator.TILE_SIZE * scale).toFloat()

    val centreX = Mercator.worldX(camera.center.longitude, zoomLevel.toDouble())
    val centreY = Mercator.worldY(camera.center.latitude, zoomLevel.toDouble())

    val halfWidth = viewportWidth / 2.0 / scale
    val halfHeight = viewportHeight / 2.0 / scale

    val minTileX = floor((centreX - halfWidth) / Mercator.TILE_SIZE).toInt()
    val maxTileX = floor((centreX + halfWidth) / Mercator.TILE_SIZE).toInt()
    val minTileY = floor((centreY - halfHeight) / Mercator.TILE_SIZE).toInt().coerceAtLeast(0)
    val maxTileY = floor((centreY + halfHeight) / Mercator.TILE_SIZE).toInt().coerceAtMost(tileCount - 1)

    val result = mutableListOf<Pair<TileKey, Offset>>()
    for (tileY in minTileY..maxTileY) {
        for (tileX in minTileX..maxTileX) {
            // Wrap horizontally so panning across the antimeridian keeps working.
            val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
            val screenX = ((tileX * Mercator.TILE_SIZE - centreX) * scale + viewportWidth / 2.0).toFloat()
            val screenY = ((tileY * Mercator.TILE_SIZE - centreY) * scale + viewportHeight / 2.0).toFloat()
            result += TileKey(zoomLevel, wrappedX, tileY) to Offset(screenX, screenY)
        }
    }
    return result
}

/** Screen size of one tile at the current fractional zoom. */
fun tileScreenSize(camera: MapCamera): Float {
    val zoomLevel = camera.zoom.toInt().coerceIn(MapCamera.MIN_ZOOM.toInt(), MapCamera.MAX_ZOOM.toInt())
    val scale = 2.0.pow(camera.zoom - zoomLevel)
    return (Mercator.TILE_SIZE * scale).toFloat()
}
