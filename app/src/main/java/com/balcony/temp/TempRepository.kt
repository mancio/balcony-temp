package com.balcony.temp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Snapshot of the balcony thermometer as stored in the Firebase Realtime Database
 * at the "Casina" node: { "temp": Double, "time": Long (unix seconds), "voltage": Double }.
 */
data class ThermometerData(
    val temperature: Double,
    val timeUnix: Long,
    val voltage: Double
)

/**
 * Reads the thermometer values from the user's Firebase Realtime Database REST endpoint.
 * The database key (its base URL) is supplied by the user, not bundled with the app.
 */
object TempRepository {

    // Data node inside the database (Casina/temp, Casina/time, Casina/voltage).
    private const val NODE = "Casina"

    private const val MIN_VOLTAGE = 4.80
    private const val MAX_VOLTAGE = 5.20
    private const val ONE_DAY_SECONDS = 24L * 60 * 60

    /** Builds the REST endpoint from the database key (a base URL or a full ".json" URL). */
    private fun endpointFor(dbKey: String): String {
        val trimmed = dbKey.trim().trimEnd('/')
        return if (trimmed.endsWith(".json")) trimmed else "$trimmed/$NODE.json"
    }

    /** Performs a blocking HTTP GET. Call from a background thread/coroutine. */
    @Throws(Exception::class)
    fun fetch(dbKey: String): ThermometerData {
        require(dbKey.isNotBlank()) { "Missing database key" }
        val connection = (URL(endpointFor(dbKey)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw RuntimeException("Server returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            return ThermometerData(
                temperature = json.optDouble("temp", Double.NaN),
                timeUnix = json.optLong("time", 0L),
                voltage = json.optDouble("voltage", Double.NaN)
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Battery level 0-100 derived from the voltage, matching the web dashboard formula. */
    fun batteryPercentage(voltage: Double): Int {
        if (voltage.isNaN()) return 0
        val clamped = voltage.coerceIn(MIN_VOLTAGE, MAX_VOLTAGE)
        return (((clamped - MIN_VOLTAGE) / (MAX_VOLTAGE - MIN_VOLTAGE)) * 100).roundToInt()
    }

    /** True when the last thermometer update is older than one day (data considered stale). */
    fun isStale(timeUnix: Long, nowUnix: Long = System.currentTimeMillis() / 1000): Boolean {
        if (timeUnix <= 0) return true
        return (nowUnix - timeUnix) > ONE_DAY_SECONDS
    }

    /** Battery level, forced to 0 when the reading is older than a day. */
    fun displayBattery(voltage: Double, timeUnix: Long): Int =
        if (isStale(timeUnix)) 0 else batteryPercentage(voltage)

    /**
     * Drawable icon chosen from the temperature, using the same buckets and artwork
     * as the mancioweb dashboard (camel = hot, temperate beach = mild, frozen = cold).
     */
    fun temperatureIconRes(temperature: Double): Int = when {
        temperature.isNaN() -> R.drawable.ic_temp_mild
        temperature > 29 -> R.drawable.ic_temp_hot     // hot
        temperature >= 13 -> R.drawable.ic_temp_mild   // mild
        else -> R.drawable.ic_temp_cold                // cold
    }

    /** Human readable "x ago" string built from the last-update unix timestamp. */
    fun timeAgo(timeUnix: Long, nowUnix: Long = System.currentTimeMillis() / 1000): String {
        if (timeUnix <= 0) return "unknown"
        val diff = nowUnix - timeUnix
        if (diff < 0) return "just now"
        val days = diff / 86_400
        val hours = (diff % 86_400) / 3_600
        val minutes = (diff % 3_600) / 60
        return when {
            days > 0 -> "$days d, $hours h ago"
            hours > 0 -> "$hours h, $minutes min ago"
            else -> "$minutes min ago"
        }
    }

    /** Absolute last-update time formatted in the thermometer's local (Warsaw) time zone. */
    fun formatTimestamp(timeUnix: Long): String {
        if (timeUnix <= 0) return "--"
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Warsaw")
        }
        return formatter.format(Date(timeUnix * 1000))
    }
}
