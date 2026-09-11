package com.kareem.cortex.visualmemory

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kareem.cortex.visualmemory.data.db.MediaItemEntity
import com.kareem.cortex.visualmemory.data.media.MediaIndexer
import com.kareem.cortex.visualmemory.data.ocr.OcrWorker
import com.kareem.cortex.visualmemory.data.search.EmbeddingGemmaSemanticEngine
import com.kareem.cortex.visualmemory.data.search.EmbeddingGemmaEmbedder
import com.kareem.cortex.visualmemory.data.search.EmbeddingWorker
import com.kareem.cortex.visualmemory.data.search.HybridSearchRepository
import com.kareem.cortex.visualmemory.data.search.SemanticModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class VisualMemoryStats(
    val pictures: Int,
    val screenshots: Int,
    val ocrReady: Int,
    val ocrPending: Int,
    val ocrFailed: Int,
    val semanticIndexed: Int,
    val semanticFailed: Int,
    val modelInstalled: Boolean
)

data class VisualMemoryItem(
    val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val capturedAtMillis: Long,
    val ocrText: String,
    val ocrState: String,
    val semanticState: String,
    val semanticLastError: String,
    val origin: String,
    val selfReferenceScore: Float,
    val derivationDepth: Int,
    val knowledgeEligible: Boolean,
    val provenanceReason: String
)

object VisualMemoryRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    interface Callback {
        fun success(message: String)
        fun failure(message: String)
    }

    @JvmStatic
    fun sync(context: Context, callback: Callback?) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val dao = VisualMemoryStore.database(app).mediaItemDao()
                val syncResult = MediaIndexer(app, dao).reconcile()
                enqueueOcr(app)
                "Indexed " + syncResult.indexed + " images"
            }.onSuccess { callback?.success(it) }
                .onFailure { callback?.failure(it.message ?: it::class.java.simpleName) }
        }
    }

    @JvmStatic
    fun enqueueOcr(context: Context) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            OcrWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    @JvmStatic
    fun enqueueSemantic(context: Context, restart: Boolean) {
        val app = context.applicationContext
        scope.launch {
            if (restart) {
                VisualMemoryStore.database(app).mediaItemDao().resetSemanticFailures()
            }
            val request = OneTimeWorkRequestBuilder<EmbeddingWorker>().build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                EmbeddingWorker.UNIQUE_WORK_NAME,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    @JvmStatic
    fun downloadSemanticModel(context: Context, callback: Callback?) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                SemanticModelStore(app).downloadOfficial()
                "EmbeddingGemma installed"
            }.onSuccess { callback?.success(it) }
                .onFailure { callback?.failure(it.message ?: it::class.java.simpleName) }
        }
    }

    @JvmStatic
    fun stats(context: Context): VisualMemoryStats = runBlocking(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = VisualMemoryStore.database(app).mediaItemDao()
        val all = dao.getAll()
        val screenshots = all.filter { it.isScreenshot }
        val semanticIndexed = dao.getEmbeddings(
            EmbeddingGemmaEmbedder.MODEL_ID,
            EmbeddingGemmaEmbedder.TARGET_DIMENSIONS
        ).map { it.mediaId }.distinct().size
        VisualMemoryStats(
            pictures = all.size,
            screenshots = screenshots.size,
            ocrReady = screenshots.count { it.ocrState == "DONE" },
            ocrPending = screenshots.count { it.ocrState == "NOT_PROCESSED" },
            ocrFailed = screenshots.count { it.ocrState == "FAILED" },
            semanticIndexed = semanticIndexed,
            semanticFailed = screenshots.count { it.semanticState == "FAILED" },
            modelInstalled = SemanticModelStore(app).isInstalled()
        )
    }

    @JvmStatic
    fun recent(context: Context, limit: Int): List<VisualMemoryItem> = runBlocking(Dispatchers.IO) {
        VisualMemoryStore.database(context.applicationContext).mediaItemDao().getAll()
            .asSequence()
            .filter { it.isScreenshot }
            .sortedByDescending { it.dateTakenMillis ?: it.dateAddedSeconds * 1000L }
            .take(limit)
            .map(::toDto)
            .toList()
    }

    @JvmStatic
    fun search(context: Context, query: String, limit: Int): List<VisualMemoryItem> = runBlocking(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = VisualMemoryStore.database(app).mediaItemDao()
        val corpus = dao.getAll().filter { it.isScreenshot && it.ocrState == "DONE" }
        val semantic = if (SemanticModelStore(app).isInstalled()) {
            EmbeddingGemmaSemanticEngine(app, dao)
        } else null
        try {
            val repository = if (semantic != null) HybridSearchRepository(dao, semantic) else HybridSearchRepository(dao)
            repository.search(query, corpus, limit).map(::toDto)
        } finally {
            semantic?.close()
        }
    }

    private fun toDto(item: MediaItemEntity): VisualMemoryItem = VisualMemoryItem(
        mediaId = item.mediaId,
        contentUri = item.contentUri,
        displayName = item.displayName.orEmpty(),
        capturedAtMillis = item.dateTakenMillis ?: item.dateAddedSeconds * 1000L,
        ocrText = item.ocrText.orEmpty(),
        ocrState = item.ocrState,
        semanticState = item.semanticState,
        semanticLastError = item.semanticLastError.orEmpty(),
        origin = item.origin,
        selfReferenceScore = item.selfReferenceScore,
        derivationDepth = item.derivationDepth,
        knowledgeEligible = item.knowledgeEligible,
        provenanceReason = item.provenanceReason.orEmpty()
    )
}
