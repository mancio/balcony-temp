package com.balcony.temp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Battery percentage and the low-battery warning.
 *
 * The firmware measures the pack through a 60k/10k divider (x7.0) against the 3.3 V ADC
 * reference with a -0.02 V fine-tune offset, so one ADC step is ~0.023 V. The dashboard maps
 * the useful 4.80 V - 5.20 V window onto 0-100 %.
 */
class BatteryLevelTest {

    @Test
    fun `full battery is one hundred percent`() {
        assertEquals(100, TempRepository.batteryPercentage(5.20))
    }

    @Test
    fun `empty battery is zero percent`() {
        assertEquals(0, TempRepository.batteryPercentage(4.80))
    }

    @Test
    fun `midpoint is fifty percent`() {
        assertEquals(50, TempRepository.batteryPercentage(5.00))
    }

    @Test
    fun `voltage above the window is clamped to one hundred`() {
        assertEquals(100, TempRepository.batteryPercentage(5.9))
        // USB powered: 3.3 V ADC ref x 7.0 is the highest the divider can report.
        assertEquals(100, TempRepository.batteryPercentage(23.08))
    }

    @Test
    fun `voltage below the window is clamped to zero`() {
        assertEquals(0, TempRepository.batteryPercentage(4.0))
        assertEquals(0, TempRepository.batteryPercentage(0.0))
    }

    @Test
    fun `a missing voltage field reads as zero percent`() {
        assertEquals(0, TempRepository.batteryPercentage(Double.NaN))
    }

    @Test
    fun `the live reading that triggered the bug report is flat and low`() {
        // Actual value served by the Casina node while the widget showed no warning.
        val voltage = 4.78968

        assertEquals(0, TempRepository.batteryPercentage(voltage))
        assertTrue(TempRepository.isLowBattery(voltage))
    }

    @Test
    fun `low battery triggers at or below the threshold`() {
        val thresholdVoltage = 4.80 + 0.40 * (TempRepository.LOW_BATTERY_PERCENT / 100.0)

        assertEquals(TempRepository.LOW_BATTERY_PERCENT, TempRepository.batteryPercentage(thresholdVoltage))
        assertTrue(TempRepository.isLowBattery(thresholdVoltage))
    }

    @Test
    fun `a healthy battery is not low`() {
        assertFalse(TempRepository.isLowBattery(5.00))
        assertFalse(TempRepository.isLowBattery(5.20))
        assertFalse(TempRepository.isLowBattery(4.90))
    }

    @Test
    fun `an unknown voltage is not reported as low battery`() {
        // A partial Firebase write must not be mistaken for a dead battery.
        assertFalse(TempRepository.isLowBattery(Double.NaN))
    }

    @Test
    fun `percentage is monotonic across the window`() {
        var previous = -1
        var voltage = 4.80
        while (voltage <= 5.20) {
            val percent = TempRepository.batteryPercentage(voltage)
            assertTrue("percentage went backwards at $voltage", percent >= previous)
            assertTrue("percentage out of range at $voltage", percent in 0..100)
            previous = percent
            voltage += 0.01
        }
    }
}
