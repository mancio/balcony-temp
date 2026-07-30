package com.balcony.temp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget that mirrors the balcony thermometer.
 *
 * It always paints the last known reading from [TempCache] first, so a redraw (reboot,
 * launcher restart, `updatePeriodMillis` tick) never blanks the widget, and refreshes in
 * the background via [TempRefreshWorker] or on tap.
 */
class TempWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Paint the cached reading immediately so the widget is never empty.
        val cached = TempCache.load(context)
        val views = buildViews(
            context,
            data = cached,
            status = if (cached == null) "Updating…" else null
        )
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
        TempRefreshWorker.schedule(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TempRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TempRefreshWorker.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
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

    companion object {
        const val ACTION_REFRESH = "com.balcony.temp.ACTION_REFRESH"

        /** Ask every placed widget instance to re-fetch and redraw. */
        fun refreshAll(context: Context) {
            val intent = Intent(context, TempWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        /** Downloads a fresh reading, caches it, then repaints every widget instance. */
        fun fetchAndRender(context: Context) {
            runCatching { TempRepository.fetch() }
                .onSuccess { TempCache.save(context, it) }
            render(context)
        }

        /** Repaints every widget instance from whatever is currently cached. */
        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TempWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val cached = TempCache.load(context)
            val views = buildViews(
                context,
                data = cached,
                status = if (cached == null) "Tap to retry" else null
            )
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        internal fun buildViews(
            context: Context,
            data: ThermometerData? = null,
            status: String? = null
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_temp)
            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent(context))

            if (data == null) {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_temp_mild)
                views.setTextViewText(R.id.widget_temperature, "--")
                views.setTextViewText(R.id.widget_status, status ?: "…")
                views.setTextColor(R.id.widget_status, color(context, R.color.text_secondary))
                views.setTextViewText(R.id.widget_battery, "")
                return views
            }

            views.setImageViewResource(
                R.id.widget_icon,
                TempRepository.temperatureIconRes(data.temperature)
            )
            views.setTextViewText(
                R.id.widget_temperature,
                if (data.temperature.isNaN()) "--" else "%.1f°".format(data.temperature)
            )

            val stale = TempRepository.isStale(data.timeUnix)
            views.setTextViewText(
                R.id.widget_status,
                if (stale) {
                    context.getString(R.string.widget_status_stale)
                } else {
                    context.getString(
                        R.string.widget_status_updated,
                        TempRepository.timeAgo(data.timeUnix)
                    )
                }
            )
            views.setTextColor(
                R.id.widget_status,
                color(context, if (stale) R.color.error else R.color.text_secondary)
            )

            // The battery line stays visible even when the data is stale: a flat battery is
            // the most likely reason the thermometer stopped reporting in the first place.
            views.setTextViewText(
                R.id.widget_battery,
                if (data.voltage.isNaN()) {
                    "🔋 --"
                } else {
                    context.getString(
                        R.string.widget_battery_value,
                        TempRepository.batteryPercentage(data.voltage)
                    )
                }
            )
            views.setTextColor(
                R.id.widget_battery,
                color(
                    context,
                    if (TempRepository.isLowBattery(data.voltage)) R.color.error
                    else R.color.text_secondary
                )
            )
            return views
        }

        private fun color(context: Context, resId: Int) = ContextCompat.getColor(context, resId)

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, TempWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }
    }
}
