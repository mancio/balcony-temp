package com.balcony.temp

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodically downloads the thermometer reading, stores it in [TempCache] and repaints the
 * widget.
 *
 * This replaces the previous `AlarmManager.setInexactRepeating(ELAPSED_REALTIME, ...)` alarm,
 * which was a non-wakeup inexact alarm and therefore got deferred for hours by Doze — the
 * reason the widget went stale until it was tapped.
 */
class TempRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val data = runCatching { TempRepository.fetch() }.getOrElse {
            // Keep whatever is cached and let WorkManager retry with backoff.
            TempWidgetProvider.render(applicationContext)
            return Result.retry()
        }
        TempCache.save(applicationContext, data)
        TempWidgetProvider.render(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "balcony_temp_refresh"

        /**
         * 15 minutes is the shortest period WorkManager allows. The sensor only publishes
         * once an hour, so this is comfortably often enough to look live.
         */
        private const val REFRESH_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TempRefreshWorker>(
                REFRESH_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
