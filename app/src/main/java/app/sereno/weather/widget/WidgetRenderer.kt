package app.sereno.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import app.sereno.weather.MainActivity
import app.sereno.weather.R
import app.sereno.weather.data.prefs.SerenoSettings
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.Nowcast
import app.sereno.weather.domain.model.NowcastKind
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.Formatter

/**
 * Builds the home-screen widget.
 *
 * One provider serves three sizes, chosen from the host's reported cell size
 * rather than from three separate widget definitions — the user resizes one
 * widget and it gains detail, which is what people expect a widget to do.
 */
object WidgetRenderer {

    fun update(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        bundle: ForecastBundle?,
        settings: SerenoSettings,
        copy: Copy,
        nowEpoch: Long,
    ) {
        val options = manager.getAppWidgetOptions(widgetId)
        val views = build(context, options, bundle, settings, copy, nowEpoch)
        manager.updateAppWidget(widgetId, views)
    }

    private fun build(
        context: Context,
        options: Bundle,
        bundle: ForecastBundle?,
        settings: SerenoSettings,
        copy: Copy,
        nowEpoch: Long,
    ): RemoteViews {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)

        val layout = when {
            minHeight >= 200 && minWidth >= 250 -> R.layout.widget_large
            minHeight >= 100 && minWidth >= 200 -> R.layout.widget_medium
            else -> R.layout.widget_compact
        }

        val views = RemoteViews(context.packageName, layout)
        val ink = colorOf(context, R.color.widget_ink)
        val accent = colorOf(context, R.color.widget_accent)
        val formatter = Formatter(settings, copy, bundle?.utcOffsetSeconds ?: 0)

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

        if (bundle == null) {
            views.setTextViewText(R.id.widget_temperature, copy.noData)
            views.setTextViewText(R.id.widget_place, copy.loading)
            views.setTextViewText(R.id.widget_nowcast, "")
            views.setTextViewText(R.id.widget_range, "")
            return views
        }

        val current = bundle.current
        val hour = bundle.hours.firstOrNull { it.epochSeconds >= nowEpoch - 3600 }
        val temperature = current?.temperature ?: hour?.temperature
        val code = current?.weatherCode ?: hour?.weatherCode
        val isDay = current?.isDay ?: hour?.isDay ?: true
        val today = bundle.days.firstOrNull()

        views.setTextViewText(R.id.widget_temperature, formatter.temperature(temperature))
        views.setTextViewText(R.id.widget_place, bundle.place.name)
        views.setTextViewText(R.id.widget_nowcast, nowcastText(bundle.nowcast, copy))
        views.setTextViewText(
            R.id.widget_range,
            if (today == null) "" else
                "${formatter.degrees(today.temperatureMax)}° / ${formatter.degrees(today.temperatureMin)}°",
        )
        views.setImageViewBitmap(
            R.id.widget_icon,
            WidgetGlyphs.render(code, isDay, iconSize(layout), ink, accent),
        )

        if (layout == R.layout.widget_medium || layout == R.layout.widget_large) {
            views.removeAllViews(R.id.widget_hours)
            bundle.hours
                .filter { it.epochSeconds > nowEpoch }
                .take(5)
                .forEach { point ->
                    val item = RemoteViews(context.packageName, R.layout.widget_hour_item)
                    item.setTextViewText(R.id.hour_label, formatter.hourShort(point.epochSeconds))
                    item.setTextViewText(R.id.hour_temperature, formatter.temperature(point.temperature))
                    item.setImageViewBitmap(
                        R.id.hour_icon,
                        WidgetGlyphs.render(point.weatherCode, point.isDay, 64, ink, accent),
                    )
                    views.addView(R.id.widget_hours, item)
                }
        }

        if (layout == R.layout.widget_large) {
            views.removeAllViews(R.id.widget_days)
            bundle.days.take(5).forEach { day ->
                val item = RemoteViews(context.packageName, R.layout.widget_day_item)
                item.setTextViewText(R.id.day_label, formatter.dayLabel(day.epochSeconds, nowEpoch))
                item.setTextViewText(R.id.day_min, "${formatter.degrees(day.temperatureMin)}°")
                item.setTextViewText(R.id.day_max, "${formatter.degrees(day.temperatureMax)}°")
                item.setImageViewBitmap(
                    R.id.day_icon,
                    WidgetGlyphs.render(day.weatherCode, true, 56, ink, accent),
                )
                views.addView(R.id.widget_days, item)
            }
        }

        return views
    }

    private fun nowcastText(nowcast: Nowcast?, copy: Copy): String = when (nowcast?.kind) {
        null, NowcastKind.Unknown -> ""
        NowcastKind.Dry -> copy.noRainFor(nowcast.horizonHours)
        NowcastKind.StartingSoon -> copy.rainStartingIn(
            nowcast.startMinutesLow ?: 0,
            nowcast.startMinutesHigh ?: 0,
            nowcast.isSnow,
        )
        NowcastKind.Stopping -> copy.rainStoppingIn(nowcast.endMinutesLow ?: 0, nowcast.endMinutesHigh ?: 0)
        NowcastKind.Ongoing -> if (nowcast.isSnow) copy.snowingNow else copy.rainingNow
        NowcastKind.Intermittent -> copy.intermittentFor(nowcast.horizonHours)
    }

    private fun iconSize(layout: Int): Int = when (layout) {
        R.layout.widget_large -> 112
        R.layout.widget_medium -> 100
        else -> 84
    }

    private fun colorOf(context: Context, resId: Int): Color =
        Color(ContextCompat.getColor(context, resId))

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** True when the host is currently in dark mode, for logging/debug use. */
    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
