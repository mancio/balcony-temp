package com.balcony.temp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parsing of the exact JSON documents the CasinaWifiTemp firmware produces.
 *
 * The firmware pushes `temp`, `voltage` and `time` with three *separate*
 * `Firebase.RTDB.set*` calls, so a wake cycle that dies half way through (WiFi dropped,
 * supercapacitor drained) leaves the node with only some of the fields updated. Parsing
 * must survive every one of those partial documents.
 *
 * Runs under Robolectric because `org.json` is an Android API: on a bare JVM it is stubbed out.
 */
@RunWith(RobolectricTestRunner::class)
class TempRepositoryParseTest {

    @Test
    fun `parses a complete payload`() {
        val data = TempRepository.parse("""{"temp":17.9375,"time":1785375875,"voltage":4.78968}""")

        assertEquals(17.9375, data.temperature, 1e-6)
        assertEquals(1785375875L, data.timeUnix)
        assertEquals(4.78968, data.voltage, 1e-6)
    }

    @Test
    fun `field order does not matter`() {
        val data = TempRepository.parse("""{"voltage":5.0,"temp":-3.5,"time":1700000000}""")

        assertEquals(-3.5, data.temperature, 1e-6)
        assertEquals(1700000000L, data.timeUnix)
        assertEquals(5.0, data.voltage, 1e-6)
    }

    @Test
    fun `whole numbers are serialised without a decimal point by Firebase`() {
        // setFloat(18.0f) comes back as `18`, not `18.0`.
        val data = TempRepository.parse("""{"temp":18,"time":1700000000,"voltage":5}""")

        assertEquals(18.0, data.temperature, 1e-6)
        assertEquals(5.0, data.voltage, 1e-6)
    }

    @Test
    fun `time written with setDouble in exponent form still parses`() {
        // `time` is stored via RTDB.setDouble, so it can come back as 1.785375875E9.
        val data = TempRepository.parse("""{"temp":10.0,"time":1.785375875E9,"voltage":5.0}""")

        assertEquals(1785375875L, data.timeUnix)
    }

    @Test
    fun `missing voltage becomes NaN rather than throwing`() {
        val data = TempRepository.parse("""{"temp":17.9,"time":1785375875}""")

        assertTrue(data.voltage.isNaN())
        assertEquals(17.9, data.temperature, 1e-6)
        assertEquals(1785375875L, data.timeUnix)
    }

    @Test
    fun `missing temperature becomes NaN rather than throwing`() {
        val data = TempRepository.parse("""{"time":1785375875,"voltage":4.9}""")

        assertTrue(data.temperature.isNaN())
        assertEquals(4.9, data.voltage, 1e-6)
    }

    @Test
    fun `missing time becomes zero and is therefore treated as stale`() {
        val data = TempRepository.parse("""{"temp":17.9,"voltage":4.9}""")

        assertEquals(0L, data.timeUnix)
        assertTrue(TempRepository.isStale(data.timeUnix, nowUnix = 1785375875L))
    }

    @Test
    fun `a null node throws`() {
        // Firebase returns the literal `null` when the path does not exist.
        assertThrows(Exception::class.java) { TempRepository.parse("null") }
    }

    @Test
    fun `an empty body throws`() {
        assertThrows(Exception::class.java) { TempRepository.parse("") }
        assertThrows(Exception::class.java) { TempRepository.parse("   ") }
    }

    @Test
    fun `a malformed body throws`() {
        assertThrows(Exception::class.java) { TempRepository.parse("""{"temp":""") }
        assertThrows(Exception::class.java) { TempRepository.parse("<html>error</html>") }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val data = TempRepository.parse("\n  {\"temp\":1.0,\"time\":5,\"voltage\":5.0}  \n")

        assertEquals(1.0, data.temperature, 1e-6)
    }
}
