package app.sereno.weather.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.sereno.weather.domain.model.Coordinates
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LocationResult {
    data class Success(val coordinates: Coordinates, val label: String?) : LocationResult
    data object PermissionDenied : LocationResult
    data object Disabled : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Device location, using the platform [LocationManager] only.
 *
 * Play Services' fused provider is more accurate, but pulling in Google Play
 * Services for one call would contradict the app's no-tracking stance and add
 * a dependency that cannot be shipped outside Google's ecosystem. For choosing
 * which town's weather to show, the platform providers are ample.
 */
class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.Unavailable

        val enabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        if (!enabled) return LocationResult.Disabled

        // A recent last-known fix is worth far more than a fresh one: it is
        // instant, and for city-level weather a fix from ten minutes ago is
        // identical in practice.
        val cached = lastKnown(manager)
        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_ENOUGH_MS) {
            return success(cached)
        }

        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestFix(manager) }
        val location = fresh ?: cached ?: return LocationResult.Unavailable
        return success(location)
    }

    private suspend fun success(location: Location) =
        LocationResult.Success(
            coordinates = Coordinates(location.latitude, location.longitude),
            label = reverseGeocode(location.latitude, location.longitude),
        )

    // Every caller reaches this only after hasPermission() has returned true,
    // and each call is additionally wrapped so a revoked permission surfaces as
    // "no fix" rather than a crash. Lint cannot see across that guard.
    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    @Suppress("MissingPermission")
    private suspend fun requestFix(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = android.os.CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, executor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Required by the pre-R LocationListener interface")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    /**
     * Turns coordinates into a place name. Best-effort: the platform geocoder
     * needs network and is absent on some devices, and a nameless location is
     * still a perfectly good forecast.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
            results?.firstOrNull()?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea
            }
        }.getOrNull()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private companion object {
        const val FRESH_ENOUGH_MS = 10 * 60 * 1000L
        const val FIX_TIMEOUT_MS = 8_000L
    }
}
