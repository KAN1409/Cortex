package com.kareem.cortex.visualmemory.data.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kareem.cortex.visualmemory.data.search.EmbeddingWorker
import com.kareem.cortex.visualmemory.data.search.SemanticModelStore
import com.kareem.cortex.visualmemory.VisualMemoryStore
import com.kareem.cortex.KnowledgeV2Scheduler

class OcrWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!hasImageReadPermission(applicationContext)) return Result.success()

        val dao = VisualMemoryStore.database(applicationContext).mediaItemDao()
        val engine = HybridOcrEngine(applicationContext)
        val processor = ScreenshotOcrProcessor(applicationContext, dao, engine)

        return try {
            dao.resetOcrFromOlderEngines(HybridOcrEngine.ID)

            var totalAttempted = 0
            var totalSuccess = 0
            var totalFailed = 0

            while (!isStopped) {
                val batch = processor.processPending(BATCH_SIZE)
                totalAttempted += batch.attempted
                totalSuccess += batch.success
                totalFailed += batch.failed

                setProgress(
                    workDataOf(
                        KEY_ATTEMPTED to totalAttempted,
                        KEY_SUCCESS to totalSuccess,
                        KEY_FAILED to totalFailed
                    )
                )

                if (batch.attempted == 0) break
            }

            if (totalSuccess > 0) {
                KnowledgeV2Scheduler.enqueue(applicationContext)
            }

            if (totalSuccess > 0 && SemanticModelStore(applicationContext).isInstalled()) {
                val semanticRequest = OneTimeWorkRequestBuilder<EmbeddingWorker>().build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    EmbeddingWorker.UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    semanticRequest
                )
            }

            Result.success(
                workDataOf(
                    KEY_ATTEMPTED to totalAttempted,
                    KEY_SUCCESS to totalSuccess,
                    KEY_FAILED to totalFailed
                )
            )
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            engine.close()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "picbrain-screenshot-ocr"
        const val KEY_ATTEMPTED = "attempted"
        const val KEY_SUCCESS = "success"
        const val KEY_FAILED = "failed"
        private const val BATCH_SIZE = 25

        fun hasImageReadPermission(context: Context): Boolean = when {
            Build.VERSION.SDK_INT >= 34 ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= 33 ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            else ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
