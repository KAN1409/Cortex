package com.kareem.cortex.prime.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kareem.cortex.prime.graph

class JobWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        // A finished model download wakes every voice note that was waiting for it.
        if (g.models.refresh()) g.jobs.unpark(com.kareem.cortex.prime.voice.MODEL_MISSING)
        g.runner.drain()
        return Result.success()
    }

    companion object { const val UNIQUE = "prime-jobs" }
}
