package com.balcony.temp

import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * Background refresh.
 *
 * The widget used to rely on `AlarmManager.setInexactRepeating(ELAPSED_REALTIME, ...)`, a
 * non-wakeup inexact alarm that Doze defers for hours - which is why the widget stopped
 * updating until it was tapped. It is now a constrained periodic WorkManager job that also
 * writes every success into [TempCache].
 */
@RunWith(RobolectricTestRunner::class)
class TempRefreshWorkerTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        server = MockWebServer()
        server.start()
        TempRepository.endpoint = server.url("/Casina.json").toString()
        TempCache.clear(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        TempRepository.endpoint = TempRepository.DB_URL
        TempCache.clear(context)
    }

    private fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<TempRefreshWorker>(context).build().startWork().get()

    @Test
    fun `a successful run caches the reading`() {
        server.enqueue(
            MockResponse().setBody("""{"temp":17.9375,"time":1785375875,"voltage":4.78968}""")
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val cached = TempCache.load(context)
        assertNotNull(cached)
        assertEquals(17.9375, cached!!.temperature, 1e-4)
        assertEquals(1785375875L, cached.timeUnix)
    }

    @Test
    fun `a failed run asks WorkManager to retry`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `a failed run does not wipe the previously cached reading`() {
        // This is the "widget lost the data" regression.
        TempCache.save(context, ThermometerData(17.9375, 1785375875L, 5.10))
        server.enqueue(MockResponse().setResponseCode(500))

        runWorker()

        val cached = TempCache.load(context)
        assertNotNull(cached)
        assertEquals(17.9375, cached!!.temperature, 1e-4)
        assertEquals(1785375875L, cached.timeUnix)
    }

    @Test
    fun `a malformed response does not wipe the cache either`() {
        TempCache.save(context, ThermometerData(20.0, 1785375875L, 5.10))
        server.enqueue(MockResponse().setBody("null"))

        runWorker()

        assertEquals(20.0, TempCache.load(context)!!.temperature, 1e-4)
    }

    @Test
    fun `scheduling enqueues a single periodic job`() {
        TempRefreshWorker.schedule(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("balcony_temp_refresh")
            .get()

        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.first().state)
    }

    @Test
    fun `scheduling twice does not stack duplicate jobs`() {
        TempRefreshWorker.schedule(context)
        TempRefreshWorker.schedule(context)
        TempRefreshWorker.schedule(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("balcony_temp_refresh")
            .get()

        assertEquals(1, infos.size)
    }

    @Test
    fun `cancelling removes the periodic job`() {
        TempRefreshWorker.schedule(context)
        TempRefreshWorker.cancel(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("balcony_temp_refresh")
            .get()

        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `removing the last widget cancels the job`() {
        TempRefreshWorker.schedule(context)

        TempWidgetProvider().onDisabled(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("balcony_temp_refresh")
            .get()

        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `adding the first widget schedules the job`() {
        TempWidgetProvider().onEnabled(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("balcony_temp_refresh")
            .get()

        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.first().state)
    }

    @Test
    fun `fetchAndRender stores a good reading`() {
        server.enqueue(MockResponse().setBody("""{"temp":-3.5,"time":1785375875,"voltage":5.2}"""))

        TempWidgetProvider.fetchAndRender(context)

        assertEquals(-3.5, TempCache.load(context)!!.temperature, 1e-4)
    }

    @Test
    fun `fetchAndRender keeps the cache empty when the very first fetch fails`() {
        server.enqueue(MockResponse().setResponseCode(503))

        TempWidgetProvider.fetchAndRender(context)

        assertNull(TempCache.load(context))
    }
}
