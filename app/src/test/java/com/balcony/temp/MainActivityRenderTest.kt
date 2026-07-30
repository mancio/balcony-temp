package com.balcony.temp

import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end rendering of the main screen against a stubbed Firebase endpoint.
 *
 * Verifies the two reported defects on the app screen itself: the red low-battery banner and
 * the red "no update for over 5 hours" banner.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityRenderTest {

    private lateinit var server: MockWebServer
    private val now get() = System.currentTimeMillis() / 1000

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        server = MockWebServer()
        server.start()
        TempCache.clear(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        server.shutdown()
        TempRepository.endpoint = TempRepository.DB_URL
        TempCache.clear(ApplicationProvider.getApplicationContext())
    }

    /** Answers every request with the same document, however many times the app asks. */
    private fun serve(body: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(body)
        }
        TempRepository.endpoint = server.url("/Casina.json").toString()
    }

    private fun launchAndRender(body: String, assertions: (View) -> Unit) {
        serve(body)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.content)
                awaitContent(root)
                assertions(root.rootView)
            }
        }
    }

    /** Pumps the main looper until the async load has rendered, or fails after a few seconds. */
    private fun awaitContent(content: View) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (content.visibility == View.VISIBLE) return
            Thread.sleep(10)
        }
        throw AssertionError("The reading never rendered")
    }

    private fun text(root: View, id: Int) = root.findViewById<TextView>(id).text.toString()
    private fun visibility(root: View, id: Int) = root.findViewById<View>(id).visibility
    private fun textColor(root: View, id: Int) = root.findViewById<TextView>(id).currentTextColor

    private fun errorColor(root: View) = ContextCompat.getColor(root.context, R.color.error)

    @Test
    fun `a healthy fresh reading shows no warnings`() {
        launchAndRender("""{"temp":17.9375,"time":${now - 15 * 60},"voltage":5.10}""") { root ->
            assertEquals("17.9 °C", text(root, R.id.temperature))
            assertEquals("75%", text(root, R.id.batteryPercent))
            assertEquals("5.10 V", text(root, R.id.voltage))
            assertEquals(View.GONE, visibility(root, R.id.batteryLow))
            assertEquals(View.GONE, visibility(root, R.id.batteryStale))
            assertNotEquals(errorColor(root), textColor(root, R.id.batteryPercent))
        }
    }

    @Test
    fun `a flat battery shows the red low battery banner`() {
        // The exact payload the live node was serving while the app showed nothing.
        launchAndRender("""{"temp":17.9375,"time":${now - 15 * 60},"voltage":4.78968}""") { root ->
            assertEquals(View.VISIBLE, visibility(root, R.id.batteryLow))
            assertEquals("0%", text(root, R.id.batteryPercent))
            assertEquals(errorColor(root), textColor(root, R.id.batteryPercent))
            assertEquals(errorColor(root), textColor(root, R.id.batteryLow))
        }
    }

    @Test
    fun `more than five hours without an update shows the red stale banner`() {
        launchAndRender("""{"temp":17.9375,"time":${now - 6 * 3600},"voltage":5.10}""") { root ->
            assertEquals(View.VISIBLE, visibility(root, R.id.batteryStale))
            assertEquals(errorColor(root), textColor(root, R.id.batteryStale))
            assertEquals(errorColor(root), textColor(root, R.id.updatedAgo))
        }
    }

    @Test
    fun `four hours without an update shows no stale banner`() {
        launchAndRender("""{"temp":17.9375,"time":${now - 4 * 3600},"voltage":5.10}""") { root ->
            assertEquals(View.GONE, visibility(root, R.id.batteryStale))
            assertNotEquals(errorColor(root), textColor(root, R.id.updatedAgo))
        }
    }

    @Test
    fun `the battery stays visible when the reading is stale`() {
        // Hiding the battery exactly when the sensor stops reporting hid the actual cause.
        launchAndRender("""{"temp":17.9375,"time":${now - 30 * 3600},"voltage":4.78968}""") { root ->
            assertEquals(View.VISIBLE, visibility(root, R.id.batteryRow))
            assertEquals(View.VISIBLE, visibility(root, R.id.batteryLow))
            assertEquals(View.VISIBLE, visibility(root, R.id.batteryStale))
            assertEquals("4.79 V", text(root, R.id.voltage))
        }
    }

    @Test
    fun `a partial firebase write renders placeholders instead of NaN`() {
        launchAndRender("""{"time":${now - 15 * 60}}""") { root ->
            assertEquals("--", text(root, R.id.temperature))
            assertEquals("--", text(root, R.id.batteryPercent))
            assertEquals("--", text(root, R.id.voltage))
            assertEquals(View.GONE, visibility(root, R.id.batteryLow))
        }
    }

    @Test
    fun `a successful load caches the reading for the widget`() {
        val timestamp = now - 15 * 60
        launchAndRender("""{"temp":17.9375,"time":$timestamp,"voltage":5.10}""") {
            val cached = TempCache.load(ApplicationProvider.getApplicationContext())
            assertTrue(cached != null && cached.timeUnix == timestamp)
        }
    }

    @Test
    fun `a server error shows the error message and no content`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
        }
        TempRepository.endpoint = server.url("/Casina.json").toString()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val error = activity.findViewById<TextView>(R.id.errorText)
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline && error.visibility != View.VISIBLE) {
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
                assertEquals(View.VISIBLE, error.visibility)
                assertTrue(error.text.contains("500"))
                assertEquals(View.GONE, activity.findViewById<ProgressBar>(R.id.progress).visibility)
            }
        }
    }
}
