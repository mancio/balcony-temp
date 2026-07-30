package com.balcony.temp

import android.content.Context

/**
 * Last successfully fetched reading, persisted so the widget can redraw itself without
 * a network round trip.
 *
 * Widgets are rebuilt by the launcher on reboot, on app update, when the launcher process
 * is restarted and on every `updatePeriodMillis` tick. Without a cache each of those events
 * wiped the visible values back to "--" until a fetch completed (and left them blank forever
 * if the fetch failed), which is why the widget appeared to "lose the data".
 */
object TempCache {

    private const val PREFS = "balcony_temp_cache"
    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_TIME = "time"
    private const val KEY_VOLTAGE = "voltage"
    private const val KEY_FETCHED_AT = "fetched_at"

    fun save(context: Context, data: ThermometerData) {
        prefs(context).edit()
            .putFloat(KEY_TEMPERATURE, data.temperature.toFloat())
            .putLong(KEY_TIME, data.timeUnix)
            .putFloat(KEY_VOLTAGE, data.voltage.toFloat())
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    /** The last reading we managed to download, or null if we have never had one. */
    fun load(context: Context): ThermometerData? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_TIME)) return null
        return ThermometerData(
            temperature = prefs.getFloat(KEY_TEMPERATURE, Float.NaN).toDouble(),
            timeUnix = prefs.getLong(KEY_TIME, 0L),
            voltage = prefs.getFloat(KEY_VOLTAGE, Float.NaN).toDouble()
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
