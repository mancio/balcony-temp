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
 *
 * The values are written by the CasinaWifiTemp firmware (NodeMCU v2 / ESP8266) which wakes
 * from deep sleep once per hour, reads a DS18B20 plus the battery voltage divider, and then
 * pushes `temp`, `voltage` and `time` as three separate RTDB writes.
 */
data class ThermometerData(
    val temperature: Double,
    val timeUnix: Long,
    val voltage: Double
)

/**
 * Reads the thermometer values from the public Firebase Realtime Database REST endpoint.
 * The database allows public reads, so no key or sign-in is required.
 */
object TempRepository {

    // Public endpoint for the balcony thermometer (Casina/temp, Casina/time, Casina/voltage).
    const val DB_URL =
        "https://manciotech-244ac-default-rtdb.europe-west1.firebasedatabase.app/Casina.json"

    private const val MIN_VOLTAGE = 4.80
    private const val MAX_VOLTAGE = 5.20

    /**
     * The firmware deep-sleeps for exactly one hour between uploads, so a healthy device
     * refreshes the node every ~60 minutes. Five missed cycles means something is wrong
     * (flat battery, WiFi down, or a frozen sensor), so that is when we raise the alarm.
     */
    const val STALE_AFTER_SECONDS = 5L * 60 * 60

    /** At or below this battery percentage the reading is drawn in red. */
    const val LOW_BATTERY_PERCENT = 20

    /** Endpoint actually used by [fetch]. Overridden by tests to point at a local server. */
    @Volatile
    internal var endpoint: String = DB_URL

    /** Performs a blocking HTTP GET. Call from a background thread/coroutine. */
    @Throws(Exception::class)
    fun fetch(urlString: String = endpoint): ThermometerData {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
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
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Turns the raw RTDB JSON document into a [ThermometerData].
     *
     * The firmware writes the three fields independently, so any of them can be missing when
     * a wake cycle dies half way through: a missing number becomes NaN / 0 instead of throwing.
     * A literal `null` body means the node does not exist at all.
     */
    @Throws(Exception::class)
    fun parse(body: String): ThermometerData {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed == "null") {
            throw RuntimeException("Thermometer node is empty")
        }
        val json = JSONObject(trimmed)
        return ThermometerData(
            temperature = json.optDouble("temp", Double.NaN),
            timeUnix = json.optLong("time", 0L),
            voltage = json.optDouble("voltage", Double.NaN)
        )
    }

    /** Battery level 0-100 derived from the voltage, matching the web dashboard formula. */
    fun batteryPercentage(voltage: Double): Int {
        if (voltage.isNaN()) return 0
        val clamped = voltage.coerceIn(MIN_VOLTAGE, MAX_VOLTAGE)
        return (((clamped - MIN_VOLTAGE) / (MAX_VOLTAGE - MIN_VOLTAGE)) * 100).roundToInt()
    }

    /**
     * True when the battery is low enough to warn about. An unknown (NaN) voltage is *not*
     * reported as low, otherwise a partial Firebase write would look like a dead battery.
     */
    fun isLowBattery(voltage: Double): Boolean {
        if (voltage.isNaN()) return false
        return batteryPercentage(voltage) <= LOW_BATTERY_PERCENT
    }

    /** True when the thermometer has missed several hourly uploads (data considered stale). */
    fun isStale(timeUnix: Long, nowUnix: Long = System.currentTimeMillis() / 1000): Boolean {
        if (timeUnix <= 0) return true
        return (nowUnix - timeUnix) > STALE_AFTER_SECONDS
    }

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
