package com.balcony.temp

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The REST call against the Firebase Realtime Database `.../Casina.json` endpoint,
 * exercised against a local mock server.
 */
@RunWith(RobolectricTestRunner::class)
class TempRepositoryHttpTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url() = server.url("/Casina.json").toString()

    @Test
    fun `reads and parses a successful response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"temp":17.9375,"time":1785375875,"voltage":4.78968}""")
        )

        val data = TempRepository.fetch(url())

        assertEquals(17.9375, data.temperature, 1e-6)
        assertEquals(1785375875L, data.timeUnix)
        assertEquals(4.78968, data.voltage, 1e-6)
    }

    @Test
    fun `sends a GET asking for json`() {
        server.enqueue(MockResponse().setBody("""{"temp":1.0,"time":2,"voltage":5.0}"""))

        TempRepository.fetch(url())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/Casina.json", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `a not found response throws`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val error = assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
        assertTrue(error.message!!.contains("404"))
    }

    @Test
    fun `a permission denied response throws`() {
        // What Firebase returns if the public read rule is ever removed.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Permission denied"}"""))

        assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
    }

    @Test
    fun `a server error throws`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
    }

    @Test
    fun `a deleted node throws`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("null"))

        assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
    }

    @Test
    fun `a truncated body throws instead of returning garbage`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"temp":17.9,"tim"""))

        assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
    }

    @Test
    fun `a dropped connection throws`() {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        assertThrows(Exception::class.java) { TempRepository.fetch(url()) }
    }

    @Test
    fun `the production endpoint points at the Casina node`() {
        assertTrue(TempRepository.DB_URL.endsWith("/Casina.json"))
        assertTrue(TempRepository.DB_URL.startsWith("https://"))
    }
}
