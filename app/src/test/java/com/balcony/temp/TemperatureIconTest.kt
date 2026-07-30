package com.balcony.temp

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Temperature -> artwork buckets, matching the mancioweb dashboard:
 * camel above 29 C, temperate scene from 13 C to 29 C, frozen scene below 13 C.
 */
@RunWith(RobolectricTestRunner::class)
class TemperatureIconTest {

    @Test
    fun `hot above twenty nine`() {
        assertEquals(R.drawable.ic_temp_hot, TempRepository.temperatureIconRes(29.1))
        assertEquals(R.drawable.ic_temp_hot, TempRepository.temperatureIconRes(35.0))
    }

    @Test
    fun `twenty nine exactly is still mild`() {
        assertEquals(R.drawable.ic_temp_mild, TempRepository.temperatureIconRes(29.0))
    }

    @Test
    fun `mild between thirteen and twenty nine`() {
        assertEquals(R.drawable.ic_temp_mild, TempRepository.temperatureIconRes(13.0))
        assertEquals(R.drawable.ic_temp_mild, TempRepository.temperatureIconRes(17.9375))
        assertEquals(R.drawable.ic_temp_mild, TempRepository.temperatureIconRes(28.9))
    }

    @Test
    fun `cold below thirteen`() {
        assertEquals(R.drawable.ic_temp_cold, TempRepository.temperatureIconRes(12.9))
        assertEquals(R.drawable.ic_temp_cold, TempRepository.temperatureIconRes(0.0))
        assertEquals(R.drawable.ic_temp_cold, TempRepository.temperatureIconRes(-15.5))
    }

    @Test
    fun `a missing temperature falls back to the mild artwork`() {
        assertEquals(R.drawable.ic_temp_mild, TempRepository.temperatureIconRes(Double.NaN))
    }

    @Test
    fun `the DS18B20 disconnected sentinel maps to cold`() {
        // DallasTemperature returns -127 C when the OneWire probe is not answering.
        assertEquals(R.drawable.ic_temp_cold, TempRepository.temperatureIconRes(-127.0))
    }
}
