package app.sereno.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.sereno.weather.work.WeatherUpdateWorker

/**
 * The home-screen widget.
 *
 * The provider itself does almost nothing: broadcast receivers get a very short
 * window to run and none of it on a background thread, so every update is
 * handed to [WeatherUpdateWorker], which can take the time to hit the network
 * and still paint the cached forecast immediately if it cannot.
 */
class SerenoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WeatherUpdateWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resizing changes which layout applies, so redraw immediately from
        // cache rather than waiting for the next scheduled refresh.
        WeatherUpdateWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        WeatherUpdateWorker.schedule(context)
        WeatherUpdateWorker.refreshNow(context)
    }
}
