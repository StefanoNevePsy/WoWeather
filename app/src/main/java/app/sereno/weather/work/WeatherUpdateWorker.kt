package app.sereno.weather.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sereno.weather.SerenoApplication
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.Place
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import app.sereno.weather.notify.Notifier
import app.sereno.weather.widget.SerenoWidgetProvider
import app.sereno.weather.widget.WidgetRenderer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * The background refresh.
 *
 * One worker does all the off-screen work — widgets and notifications — because
 * both need exactly the same thing (a current forecast for the active place)
 * and running two would mean fetching it twice.
 *
 * It is deliberately conservative about battery: hourly at most, only on an
 * unmetered-agnostic connection, never when the battery is low, and it falls
 * back to the cache rather than retrying aggressively.
 */
class WeatherUpdateWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? SerenoApplication ?: return Result.success()
        val container = application.container

        val settings = runCatching { container.settings.settings.first() }.getOrNull() ?: return Result.success()
        val language = settings.language
            ?: Lang.fromTag(applicationContext.resources.configuration.locales[0]?.language ?: "en")
        val copy = Copy(language)

        val place = resolvePlace(container) ?: return Result.success()

        val bundle: ForecastBundle? = runCatching { container.repository.fetch(place, copy) }
            .getOrElse { container.repository.cachedOnly(place) }

        updateWidgets(bundle, settings, copy)

        if (bundle != null) {
            val notifier = Notifier(applicationContext)
            notifier.ensureChannels()
            notifier.prune()
            if (settings.rainNotifications) notifier.notifyRain(bundle, copy)
            if (settings.severeNotifications) notifier.notifySevere(bundle, copy)
        }

        return Result.success()
    }

    /**
     * Which place to refresh in the background.
     *
     * The selected place may be the GPS entry, whose coordinates live only in
     * the cached bundle — background location is a permission Sereno does not
     * ask for. Reading the coordinates back out of the cache keeps widgets
     * working for a GPS-tracking user without ever requesting it.
     */
    private suspend fun resolvePlace(container: app.sereno.weather.core.Container): Place? {
        val selectedId = runCatching { container.places.selectedPlaceId.first() }.getOrNull()
        val saved = runCatching { container.places.places.first() }.getOrNull().orEmpty()

        selectedId?.let { id ->
            saved.firstOrNull { it.id == id }?.let { return it }
            container.cache.read(id)?.place?.let { return it }
        }
        return saved.firstOrNull()
            ?: container.cache.read(Place.CURRENT_LOCATION_ID)?.place
    }

    private fun updateWidgets(
        bundle: ForecastBundle?,
        settings: app.sereno.weather.data.prefs.SerenoSettings,
        copy: Copy,
    ) {
        val manager = AppWidgetManager.getInstance(applicationContext) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(applicationContext, SerenoWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis() / 1000
        ids.forEach { id ->
            runCatching {
                WidgetRenderer.update(applicationContext, manager, id, bundle, settings, copy, now)
            }
        }
    }

    companion object {
        private const val PERIODIC = "sereno-periodic-update"
        private const val ONE_SHOT = "sereno-one-shot-update"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WeatherUpdateWorker>(1, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .build(),
                )
            }
        }

        fun refreshNow(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_SHOT,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                        .setConstraints(constraints)
                        .build(),
                )
            }
        }
    }
}
