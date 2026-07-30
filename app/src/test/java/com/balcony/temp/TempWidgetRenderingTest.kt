package com.balcony.temp

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the home-screen widget actually paints.
 *
 * Covers the two reported defects: a flat battery must be shown in red, and a thermometer
 * that has missed more than five hourly wake cycles must say so instead of quietly showing
 * an old temperature as if it were current.
 */
@RunWith(RobolectricTestRunner::class)
class TempWidgetRenderingTest {

    private lateinit var context: Context
    private val now = System.currentTimeMillis() / 1000
    private val hour = 3600L

    private val errorColor get() = ContextCompat.getColor(context, R.color.error)
    private val secondaryColor get() = ContextCompat.getColor(context, R.color.text_secondary)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun inflate(data: ThermometerData?, status: String? = null): View =
        TempWidgetProvider.buildViews(context, data, status).apply(context, null)

    private fun text(root: View, id: Int) = root.findViewById<TextView>(id).text.toString()
    private fun color(root: View, id: Int) = root.findViewById<TextView>(id).currentTextColor

    @Test
    fun `a fresh healthy reading shows temperature, age and battery in the normal colour`() {
        val root = inflate(ThermometerData(17.9375, now - 20 * 60, 5.10))

        assertEquals("17.9°", text(root, R.id.widget_temperature))
        assertTrue(text(root, R.id.widget_status).contains("20 min ago"))
        assertEquals("🔋 75%", text(root, R.id.widget_battery))
        assertEquals(secondaryColor, color(root, R.id.widget_battery))
        assertEquals(secondaryColor, color(root, R.id.widget_status))
    }

    @Test
    fun `a low battery is drawn in red`() {
        // The exact reading the live node was serving when the widget showed no warning.
        val root = inflate(ThermometerData(17.9375, now - 20 * 60, 4.78968))

        assertEquals("🔋 0%", text(root, R.id.widget_battery))
        assertEquals(errorColor, color(root, R.id.widget_battery))
    }

    @Test
    fun `a battery right on the threshold is drawn in red`() {
        val thresholdVoltage = 4.80 + 0.40 * (TempRepository.LOW_BATTERY_PERCENT / 100.0)
        val root = inflate(ThermometerData(17.9, now - 10 * 60, thresholdVoltage))

        assertEquals(errorColor, color(root, R.id.widget_battery))
    }

    @Test
    fun `a healthy battery is not drawn in red`() {
        val root = inflate(ThermometerData(17.9, now - 10 * 60, 5.00))

        assertEquals(secondaryColor, color(root, R.id.widget_battery))
        assertNotEquals(errorColor, color(root, R.id.widget_battery))
    }

    @Test
    fun `more than five hours without an update shows a red warning`() {
        val root = inflate(ThermometerData(17.9, now - 6 * hour, 5.10))

        assertEquals(context.getString(R.string.widget_status_stale), text(root, R.id.widget_status))
        assertEquals(errorColor, color(root, R.id.widget_status))
    }

    @Test
    fun `four hours without an update is not flagged`() {
        val root = inflate(ThermometerData(17.9, now - 4 * hour, 5.10))

        assertNotEquals(context.getString(R.string.widget_status_stale), text(root, R.id.widget_status))
        assertEquals(secondaryColor, color(root, R.id.widget_status))
    }

    @Test
    fun `the battery is still shown when the reading is stale`() {
        // A flat battery is the most likely reason the sensor stopped reporting, so hiding
        // the battery exactly when it matters most was the wrong behaviour.
        val root = inflate(ThermometerData(17.9, now - 30 * hour, 4.78968))

        assertEquals("🔋 0%", text(root, R.id.widget_battery))
        assertEquals(errorColor, color(root, R.id.widget_battery))
        assertEquals(errorColor, color(root, R.id.widget_status))
    }

    @Test
    fun `a missing temperature renders a placeholder instead of NaN`() {
        val root = inflate(ThermometerData(Double.NaN, now - 10 * 60, 5.0))

        assertEquals("--", text(root, R.id.widget_temperature))
    }

    @Test
    fun `a missing voltage renders a placeholder and stays neutral`() {
        val root = inflate(ThermometerData(17.9, now - 10 * 60, Double.NaN))

        assertEquals("🔋 --", text(root, R.id.widget_battery))
        assertEquals(secondaryColor, color(root, R.id.widget_battery))
    }

    @Test
    fun `a missing timestamp is treated as stale`() {
        val root = inflate(ThermometerData(17.9, 0L, 5.1))

        assertEquals(context.getString(R.string.widget_status_stale), text(root, R.id.widget_status))
        assertEquals(errorColor, color(root, R.id.widget_status))
    }

    @Test
    fun `negative temperatures keep one decimal`() {
        val root = inflate(ThermometerData(-7.2, now - 10 * 60, 5.1))

        assertEquals("-7.2°", text(root, R.id.widget_temperature))
    }

    @Test
    fun `with no cached reading the widget shows the given placeholder status`() {
        val root = inflate(data = null, status = "Tap to retry")

        assertEquals("--", text(root, R.id.widget_temperature))
        assertEquals("Tap to retry", text(root, R.id.widget_status))
        assertEquals("", text(root, R.id.widget_battery))
    }
}
