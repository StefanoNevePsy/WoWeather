package app.sereno.weather.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.sereno.weather.MainActivity
import app.sereno.weather.R
import app.sereno.weather.domain.model.AlertSeverity
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.NowcastKind
import app.sereno.weather.i18n.Copy

/**
 * Notifications, kept deliberately rare.
 *
 * The rule this enforces is the one the brief asked for: do not bombard. Rain
 * is announced only when it is genuinely imminent *and* the models broadly
 * agree; warnings are announced only at Warning level and above. Everything is
 * de-duplicated, so a forecast that keeps saying the same thing stays silent
 * after the first time it says it.
 */
class Notifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("sereno_notifications", Context.MODE_PRIVATE)

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RAIN,
                "Rain",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Rain starting soon" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEVERE,
                "Severe weather",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Storms, strong wind, ice and heat" },
        )
    }

    private fun permitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyRain(bundle: ForecastBundle, copy: Copy) {
        val nowcast = bundle.nowcast ?: return
        if (nowcast.kind != NowcastKind.StartingSoon) return
        val start = nowcast.startMinutesLow ?: return
        if (start > 60) return
        // A hedged nowcast is not worth interrupting someone for.
        if (nowcast.confidence.score < 0.5f) return

        // One notification per onset, not one per refresh.
        val key = "rain_${bundle.place.id}_${(bundle.fetchedAtEpoch + start * 60) / 1800}"
        if (prefs.getBoolean(key, false)) return

        post(
            id = NOTIFICATION_RAIN,
            channel = CHANNEL_RAIN,
            title = bundle.place.name,
            body = copy.rainStartingIn(start, nowcast.startMinutesHigh ?: start, nowcast.isSnow),
            highPriority = false,
        )
        prefs.edit().putBoolean(key, true).apply()
    }

    fun notifySevere(bundle: ForecastBundle, copy: Copy) {
        val alert = bundle.alerts
            .filter { it.severity == AlertSeverity.Warning || it.severity == AlertSeverity.Severe }
            .maxByOrNull { it.severity.ordinal } ?: return

        val key = "alert_${alert.id}"
        if (prefs.getBoolean(key, false)) return

        post(
            id = NOTIFICATION_SEVERE,
            channel = CHANNEL_SEVERE,
            title = "${copy.severity(alert.severity)} · ${alert.headline}",
            body = alert.detail,
            highPriority = true,
        )
        prefs.edit().putBoolean(key, true).apply()
    }

    // permitted() is checked on the first line; the notify call is also wrapped
    // so that a permission revoked between the two cannot crash a background
    // worker. Lint does not follow the helper.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, channel: String, title: String, body: String, highPriority: Boolean) {
        if (!permitted()) return
        val intent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Keeps the de-duplication store from growing without bound. */
    fun prune() {
        if (prefs.all.size > 200) prefs.edit().clear().apply()
    }

    private companion object {
        const val CHANNEL_RAIN = "sereno_rain"
        const val CHANNEL_SEVERE = "sereno_severe"
        const val NOTIFICATION_RAIN = 1001
        const val NOTIFICATION_SEVERE = 1002
    }
}
