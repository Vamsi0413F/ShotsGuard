package com.example.shortsguard

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** The "N min left today" line, shown in an ongoing notification and on the home-screen widget. */
object Status {
    private const val CH_STATUS = "status"
    private const val CH_ALERT = "alert"
    private const val ID_STATUS = 1
    private const val ID_ALERT = 2

    fun headline(prefs: Prefs): String = when {
        prefs.sleepActive() -> "Sleep mode: Reels and Shorts blocked"
        prefs.limitMinutes <= 0 -> "Reels and Shorts blocked"
        prefs.exhausted() -> "Limit used up. Resets at ${formatHour(prefs.resetHour)}"
        else -> "${(prefs.secondsLeft() + 59) / 60} min left today"
    }

    /** "12 watched today" when combined, "8 Reels, 4 Shorts today" when separate. */
    fun countText(prefs: Prefs): String {
        val reels = prefs.countToday(Detector.INSTAGRAM)
        val shorts = prefs.countToday(Detector.YOUTUBE)
        return if (prefs.combinedCount) "${reels + shorts} watched today" else "$reels Reels, $shorts Shorts today"
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Daily time left", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "ShortsGuard alerts", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Updates the notification and every placed widget. Cheap enough to call often. */
    fun refresh(context: Context, prefs: Prefs = Prefs(context)) {
        val app = context.applicationContext
        updateNotification(app, prefs)
        updateWidgets(app, prefs)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(context: Context, prefs: Prefs) {
        val nm = NotificationManagerCompat.from(context)
        if (!prefs.liveNotification || !Util.notificationsGranted(context)) {
            nm.cancel(ID_STATUS)
            return
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ShortsGuard")
            .setContentText(headline(prefs))
            .setSubText(countText(prefs))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
        if (prefs.limitMinutes > 0 && !prefs.sleepActive()) {
            val max = prefs.limitMinutes * 60
            builder.setProgress(max, prefs.usedSeconds().coerceAtMost(max), false)
        }
        nm.notify(ID_STATUS, builder.build())
    }

    fun updateWidgets(context: Context, prefs: Prefs) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ShortsWidget::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_status)
        views.setTextViewText(R.id.widget_text, headline(prefs))

        val showBar = prefs.limitMinutes > 0 && !prefs.sleepActive()
        if (showBar) {
            val max = prefs.limitMinutes * 60
            views.setTextViewText(
                R.id.widget_sub,
                "Used ${prefs.usedSeconds() / 60} of ${prefs.limitMinutes} min. ${countText(prefs)}",
            )
            views.setProgressBar(R.id.widget_progress, max, prefs.usedSeconds().coerceAtMost(max), false)
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
        } else {
            views.setTextViewText(R.id.widget_sub, countText(prefs))
            views.setViewVisibility(R.id.widget_progress, View.GONE)
        }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        manager.updateAppWidget(ids, views)
    }

    @SuppressLint("MissingPermission")
    fun showServiceOff(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.alertWhenOff || !Util.notificationsGranted(context)) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ShortsGuard is turned off")
            .setContentText("Reels and Shorts are not being blocked. Tap to turn it back on.")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
    }

    fun clearServiceOff(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALERT)
}
