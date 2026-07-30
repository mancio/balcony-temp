package com.balcony.temp

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inflates the real widget [android.widget.RemoteViews] on a device to make sure the layout
 * and the warning colours actually survive the RemoteViews round trip.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TempWidgetInstrumentedTest {

    private lateinit var context: Context
    private val now get() = System.currentTimeMillis() / 1000

    private val errorColor get() = ContextCompat.getColor(context, R.color.error)
    private val secondaryColor get() = ContextCompat.getColor(context, R.color.text_secondary)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun inflate(data: ThermometerData?, status: String? = null): View {
        var root: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root = TempWidgetProvider.buildViews(context, data, status).apply(context, null)
        }
        return root!!
    }

    private fun text(root: View, id: Int) = root.findViewById<TextView>(id).text.toString()
    private fun color(root: View, id: Int) = root.findViewById<TextView>(id).currentTextColor

    @Test
    fun healthyReadingUsesNeutralColours() {
        val root = inflate(ThermometerData(17.9375, now - 20 * 60, 5.10))

        assertEquals("17.9°", text(root, R.id.widget_temperature))
        assertEquals("🔋 75%", text(root, R.id.widget_battery))
        assertEquals(secondaryColor, color(root, R.id.widget_battery))
        assertEquals(secondaryColor, color(root, R.id.widget_status))
    }

    @Test
    fun lowBatteryIsRed() {
        val root = inflate(ThermometerData(17.9375, now - 20 * 60, 4.78968))

        assertEquals("🔋 0%", text(root, R.id.widget_battery))
        assertEquals(errorColor, color(root, R.id.widget_battery))
    }

    @Test
    fun staleReadingIsRed() {
        val root = inflate(ThermometerData(17.9375, now - 6 * 3600, 5.10))

        assertEquals(context.getString(R.string.widget_status_stale), text(root, R.id.widget_status))
        assertEquals(errorColor, color(root, R.id.widget_status))
    }

    @Test
    fun freshReadingIsNotFlaggedStale() {
        val root = inflate(ThermometerData(17.9375, now - 4 * 3600, 5.10))

        assertNotEquals(errorColor, color(root, R.id.widget_status))
        assertTrue(text(root, R.id.widget_status).contains("ago"))
    }

    @Test
    fun batteryStaysVisibleWhenStale() {
        val root = inflate(ThermometerData(17.9375, now - 30 * 3600, 4.78968))

        assertEquals("🔋 0%", text(root, R.id.widget_battery))
        assertEquals(errorColor, color(root, R.id.widget_battery))
    }

    @Test
    fun placeholderWhenNothingCached() {
        val root = inflate(data = null, status = "Tap to retry")

        assertEquals("--", text(root, R.id.widget_temperature))
        assertEquals("Tap to retry", text(root, R.id.widget_status))
        assertEquals("", text(root, R.id.widget_battery))
    }
}
