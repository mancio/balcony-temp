package com.balcony.temp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Staleness detection.
 *
 * `goToDeepSleep()` in the firmware sleeps for exactly one hour
 * (`EspClass::deepSleep(convertToMicroseconds(1, HOUR))`), so a healthy thermometer refreshes
 * the node roughly every 60 minutes. Anything past five missed cycles means the device is
 * not reporting and the UI must say so in red.
 */
class StalenessTest {

    private val now = 1785375875L // 2026-07-29T01:44:35Z, the timestamp from the live node
    private val hour = 3600L

    @Test
    fun `the threshold is five hours`() {
        assertEquals(5 * hour, TempRepository.STALE_AFTER_SECONDS)
    }

    @Test
    fun `a reading from the current wake cycle is fresh`() {
        assertFalse(TempRepository.isStale(now, nowUnix = now))
        assertFalse(TempRepository.isStale(now - 60, nowUnix = now))
    }

    @Test
    fun `a few missed hourly cycles are still tolerated`() {
        assertFalse(TempRepository.isStale(now - 1 * hour, nowUnix = now))
        assertFalse(TempRepository.isStale(now - 3 * hour, nowUnix = now))
        assertFalse(TempRepository.isStale(now - 4 * hour, nowUnix = now))
    }

    @Test
    fun `exactly five hours old is not yet stale`() {
        assertFalse(TempRepository.isStale(now - 5 * hour, nowUnix = now))
    }

    @Test
    fun `one second past five hours is stale`() {
        assertTrue(TempRepository.isStale(now - 5 * hour - 1, nowUnix = now))
    }

    @Test
    fun `six hours without an update is stale`() {
        assertTrue(TempRepository.isStale(now - 6 * hour, nowUnix = now))
    }

    @Test
    fun `the reported bug case - over a day old - is stale`() {
        // This used to sit just under the old 24 h threshold and showed no warning at all.
        assertTrue(TempRepository.isStale(now - 23 * hour, nowUnix = now))
        assertTrue(TempRepository.isStale(now - 26 * hour, nowUnix = now))
    }

    @Test
    fun `a missing or zero timestamp counts as stale`() {
        assertTrue(TempRepository.isStale(0L, nowUnix = now))
        assertTrue(TempRepository.isStale(-1L, nowUnix = now))
    }

    @Test
    fun `a timestamp in the future is not stale`() {
        // Phone clock behind the NTP-synced sensor, or a leap during DST changes.
        assertFalse(TempRepository.isStale(now + hour, nowUnix = now))
    }
}
