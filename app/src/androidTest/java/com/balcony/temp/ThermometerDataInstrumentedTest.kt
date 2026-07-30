package com.balcony.temp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks for the pure logic and the cache, running against the real Android
 * framework (real SharedPreferences, real `org.json`) rather than Robolectric's doubles.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ThermometerDataInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        TempCache.clear(context)
    }

    @Test
    fun parsesTheRealFirebasePayload() {
        val data = TempRepository.parse("""{"temp":17.9375,"time":1785375875,"voltage":4.78968}""")

        assertEquals(17.9375, data.temperature, 1e-6)
        assertEquals(1785375875L, data.timeUnix)
        assertEquals(4.78968, data.voltage, 1e-6)
    }

    @Test
    fun survivesAPartialFirebaseWrite() {
        val data = TempRepository.parse("""{"temp":17.9375,"time":1785375875}""")

        assertTrue(data.voltage.isNaN())
        assertFalse(TempRepository.isLowBattery(data.voltage))
    }

    @Test
    fun flagsTheFlatBatteryFromTheLiveNode() {
        assertEquals(0, TempRepository.batteryPercentage(4.78968))
        assertTrue(TempRepository.isLowBattery(4.78968))
    }

    @Test
    fun flagsMoreThanFiveHoursWithoutAnUpdate() {
        val now = 1785398195L

        assertFalse(TempRepository.isStale(now - 4 * 3600, now))
        assertFalse(TempRepository.isStale(now - 5 * 3600, now))
        assertTrue(TempRepository.isStale(now - 5 * 3600 - 1, now))
        assertTrue(TempRepository.isStale(now - 6 * 3600, now))
    }

    @Test
    fun cacheRoundTripsThroughRealSharedPreferences() {
        TempCache.save(context, ThermometerData(17.9375, 1785375875L, 4.78968))

        val restored = TempCache.load(context)

        assertNotNull(restored)
        assertEquals(17.9375, restored!!.temperature, 1e-4)
        assertEquals(1785375875L, restored.timeUnix)
        assertEquals(4.78968, restored.voltage, 1e-4)
    }

    @Test
    fun cacheIsEmptyBeforeAnyFetch() {
        assertEquals(null, TempCache.load(context))
    }

    @Test
    fun timestampIsRenderedInWarsawTime() {
        assertEquals("30/07/2026 03:44", TempRepository.formatTimestamp(1785375875L))
    }
}
