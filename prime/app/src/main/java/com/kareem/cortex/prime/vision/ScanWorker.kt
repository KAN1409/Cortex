package com.kareem.cortex.prime.vision

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kareem.cortex.prime.graph
import java.util.concurrent.TimeUnit

/** Picks up new screenshots even when the app isn't open (every 30 min, batteries permitting). */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        if (!g.canReadScreenshots()) return Result.success()
        if (g.scanner.scan() > 0) g.kickJobs()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "prime-screenshot-scan", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ScanWorker>(30, TimeUnit.MINUTES).build(),
        )
    }
}
