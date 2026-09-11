package com.kareem.cortex.visualmemory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kareem.cortex.KnowledgeV2Scheduler
import com.kareem.cortex.KnowledgeV2Store

class VisualEvidenceBackfillWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = VisualMemoryStore.database(applicationContext).mediaItemDao()
        return try {
            val items = dao.getAll()
                .asSequence()
                .filter { it.isScreenshot && it.ocrState == "DONE" }
                .toList()

            for (item in items) {
                if (isStopped) break
                // Blank OCR is still accounted for as SKIPPED instead of disappearing from totals.
                KnowledgeV2Store.registerVisualEvidence(
                    applicationContext,
                    item,
                    item.ocrText.orEmpty()
                )
            }

            KnowledgeV2Scheduler.enqueue(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cortex-kv2-visual-evidence-backfill"
    }
}
