package com.balcony.temp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "x ago" and absolute timestamp rendering.
 *
 * `time` is the UNIX epoch in **seconds, UTC**, taken from `NTPClient.getEpochTime()` on the
 * sensor. The absolute string is deliberately rendered in Europe/Warsaw, where the balcony is,
 * regardless of the phone's own time zone.
 */
class TimeFormattingTest {

    private val now = 1785398195L // 2026-07-30T07:56:35Z
    private val hour = 3600L

    @Test
    fun `minutes only for a recent reading`() {
        assertEquals("0 min ago", TempRepository.timeAgo(now, nowUnix = now))
        assertEquals("30 min ago", TempRepository.timeAgo(now - 30 * 60, nowUnix = now))
        assertEquals("59 min ago", TempRepository.timeAgo(now - 59 * 60, nowUnix = now))
    }

    @Test
    fun `hours and minutes once past an hour`() {
        assertEquals("1 h, 0 min ago", TempRepository.timeAgo(now - hour, nowUnix = now))
        assertEquals("1 h, 30 min ago", TempRepository.timeAgo(now - 90 * 60, nowUnix = now))
        assertEquals("6 h, 12 min ago", TempRepository.timeAgo(now - 6 * hour - 12 * 60, nowUnix = now))
    }

    @Test
    fun `days and hours once past a day`() {
        assertEquals("1 d, 0 h ago", TempRepository.timeAgo(now - 24 * hour, nowUnix = now))
        assertEquals("1 d, 1 h ago", TempRepository.timeAgo(now - 25 * hour, nowUnix = now))
        assertEquals("3 d, 4 h ago", TempRepository.timeAgo(now - 76 * hour, nowUnix = now))
    }

    @Test
    fun `a missing timestamp reads as unknown`() {
        assertEquals("unknown", TempRepository.timeAgo(0L, nowUnix = now))
        assertEquals("unknown", TempRepository.timeAgo(-5L, nowUnix = now))
    }

    @Test
    fun `a future timestamp reads as just now`() {
        assertEquals("just now", TempRepository.timeAgo(now + 120, nowUnix = now))
    }

    @Test
    fun `absolute timestamp is rendered in Warsaw time`() {
        // 1785375875 is 2026-07-30 01:44:35 UTC, which is 03:44 in Warsaw (CEST, UTC+2).
        assertEquals("30/07/2026 03:44", TempRepository.formatTimestamp(1785375875L))
    }

    @Test
    fun `absolute timestamp honours winter time`() {
        // 2026-01-15 12:00:00 UTC is 13:00 in Warsaw (CET, UTC+1).
        assertEquals("15/01/2026 13:00", TempRepository.formatTimestamp(1768478400L))
    }

    @Test
    fun `a missing timestamp renders as a placeholder`() {
        assertEquals("--", TempRepository.formatTimestamp(0L))
        assertEquals("--", TempRepository.formatTimestamp(-1L))
    }
}
