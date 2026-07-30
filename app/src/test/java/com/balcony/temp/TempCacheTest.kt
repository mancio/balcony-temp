package com.balcony.temp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The persistent last-known reading.
 *
 * This is what stops the widget from "losing the data": the launcher can rebuild the widget
 * at any time (reboot, app update, `updatePeriodMillis` tick) and the cache lets it repaint
 * real values without waiting for - or depending on - a network round trip.
 */
@RunWith(RobolectricTestRunner::class)
class TempCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TempCache.clear(context)
    }

    @After
    fun tearDown() {
        TempCache.clear(context)
    }

    @Test
    fun `nothing cached before the first successful fetch`() {
        assertNull(TempCache.load(context))
    }

    @Test
    fun `a saved reading survives and round trips`() {
        TempCache.save(context, ThermometerData(17.9375, 1785375875L, 4.78968))

        val restored = TempCache.load(context)

        assertNotNull(restored)
        assertEquals(17.9375, restored!!.temperature, 1e-4)
        assertEquals(1785375875L, restored.timeUnix)
        assertEquals(4.78968, restored.voltage, 1e-4)
    }

    @Test
    fun `saving again overwrites the previous reading`() {
        TempCache.save(context, ThermometerData(10.0, 1785000000L, 5.0))
        TempCache.save(context, ThermometerData(20.0, 1785375875L, 4.9))

        val restored = TempCache.load(context)!!

        assertEquals(20.0, restored.temperature, 1e-4)
        assertEquals(1785375875L, restored.timeUnix)
        assertEquals(4.9, restored.voltage, 1e-4)
    }

    @Test
    fun `a partial reading with NaN fields round trips as NaN`() {
        TempCache.save(context, ThermometerData(Double.NaN, 1785375875L, Double.NaN))

        val restored = TempCache.load(context)!!

        assertTrue(restored.temperature.isNaN())
        assertTrue(restored.voltage.isNaN())
        assertEquals(1785375875L, restored.timeUnix)
    }

    @Test
    fun `negative temperatures survive the float round trip`() {
        TempCache.save(context, ThermometerData(-12.5, 1785375875L, 5.05))

        assertEquals(-12.5, TempCache.load(context)!!.temperature, 1e-4)
    }

    @Test
    fun `clear removes the cached reading`() {
        TempCache.save(context, ThermometerData(17.9, 1785375875L, 4.9))
        TempCache.clear(context)

        assertNull(TempCache.load(context))
    }

    @Test
    fun `a stale cached reading is still returned so the widget can warn about it`() {
        val old = 1785375875L - 30 * 3600
        TempCache.save(context, ThermometerData(17.9, old, 4.78968))

        val restored = TempCache.load(context)!!

        assertTrue(TempRepository.isStale(restored.timeUnix, nowUnix = 1785375875L))
        assertTrue(TempRepository.isLowBattery(restored.voltage))
    }
}
