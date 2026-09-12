package com.kareem.cortex.visualmemory.data.search

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kareem.cortex.visualmemory.VisualMemoryRuntime
import java.util.concurrent.TimeUnit

/** Resilient background bootstrap for the official semantic model. */
class SemanticModelInstallWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = SemanticModelStore(applicationContext)
        if (store.isInstalled()) {
            VisualMemoryRuntime.enqueueSemantic(applicationContext, false)
            return Result.success(workDataOf(KEY_STATE to "installed"))
        }
        return try {
            val bytes = store.downloadOfficial { progress ->
                setProgressAsync(workDataOf(KEY_STATE to "downloading", KEY_PROGRESS to progress))
            }
            if (!store.isInstalled()) return Result.retry()
            VisualMemoryRuntime.enqueueSemantic(applicationContext, true)
            Result.success(workDataOf(KEY_STATE to "installed", KEY_BYTES to bytes, KEY_PROGRESS to 100))
        } catch (t: Throwable) {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                Result.failure(workDataOf(KEY_STATE to "failed", KEY_ERROR to describe(t)))
            } else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cortex-semantic-model-bootstrap-v1"
        const val KEY_STATE = "state"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 5

        @JvmStatic fun enqueue(context: Context) {
            val app = context.applicationContext
            if (SemanticModelStore(app).isInstalled()) {
                VisualMemoryRuntime.enqueueSemantic(app, false)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<SemanticModelInstallWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun describe(t: Throwable): String {
            val root = generateSequence(t) { it.cause }.last()
            return "${root::class.java.simpleName}: ${root.message.orEmpty()}".take(800)
        }
    }
}
