package com.kareem.cortex.prime.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kareem.cortex.prime.graph

class JobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.graph.runner.drain()
        return Result.success()
    }

    companion object { const val UNIQUE = "prime-jobs" }
}
