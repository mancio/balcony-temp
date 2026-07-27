package com.balcony.temp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget that mirrors the balcony thermometer. It refreshes roughly every
 * 10 minutes (via an AlarmManager alarm) and whenever the widget is tapped.
 */
class TempWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Show a lightweight placeholder immediately; the network refresh happens in onReceive.
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, status = "Updating…"))
        }
        // (Re)arm the 10-minute refresh alarm (also covers reboots and re-adds).
        scheduleUpdates(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    fetchAndRender(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun fetchAndRender(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TempWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val views = runCatching { TempRepository.fetch() }
            .fold(
                onSuccess = { data -> buildViews(context, data = data) },
                onFailure = { buildViews(context, status = "Tap to retry") }
            )
        for (id in ids) {
            manager.updateAppWidget(id, views)
        }
    }

    private fun buildViews(
        context: Context,
        data: ThermometerData? = null,
        status: String? = null
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_temp)

        if (data != null) {
            views.setImageViewResource(R.id.widget_icon, TempRepository.temperatureIconRes(data.temperature))
            views.setTextViewText(
                R.id.widget_temperature,
                if (data.temperature.isNaN()) "--" else "%.1f°".format(data.temperature)
            )
            views.setTextViewText(
                R.id.widget_status,
                "Updated ${TempRepository.timeAgo(data.timeUnix)}"
            )
            if (TempRepository.isStale(data.timeUnix)) {
                views.setTextViewText(R.id.widget_battery, "⚠ No update >1d")
                views.setTextColor(R.id.widget_battery, ContextCompat.getColor(context, R.color.error))
            } else {
                views.setTextViewText(
                    R.id.widget_battery,
                    "🔋 ${TempRepository.batteryPercentage(data.voltage)}%"
                )
                views.setTextColor(R.id.widget_battery, ContextCompat.getColor(context, R.color.text_secondary))
            }
        } else {
            views.setImageViewResource(R.id.widget_icon, R.drawable.ic_temp_mild)
            views.setTextViewText(R.id.widget_temperature, "--")
            views.setTextViewText(R.id.widget_status, status ?: "…")
            views.setTextViewText(R.id.widget_battery, "")
        }

        views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent(context))
        return views
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TempWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        const val ACTION_REFRESH = "com.balcony.temp.ACTION_REFRESH"
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L

        /** Ask every placed widget instance to re-fetch and redraw. */
        fun refreshAll(context: Context) {
            val intent = Intent(context, TempWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        /** Inexact repeating alarm that broadcasts a refresh roughly every 10 minutes. */
        fun scheduleUpdates(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                alarmIntent(context)
            )
        }

        fun cancelUpdates(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(alarmIntent(context))
        }

        private fun alarmIntent(context: Context): PendingIntent {
            val intent = Intent(context, TempWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 1001, intent, flags)
        }
    }
}
