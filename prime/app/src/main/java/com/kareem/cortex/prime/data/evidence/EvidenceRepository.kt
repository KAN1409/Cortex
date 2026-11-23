package com.kareem.cortex.prime.data.evidence

import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.jobs.JobQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

sealed interface CaptureResult {
    data class Saved(val id: Long, val duplicate: Boolean) : CaptureResult
    data class Rejected(val message: String) : CaptureResult
}

const val STAGE_PERCEIVE = "perceive"

class EvidenceRepository(
    private val dao: EvidenceDao,
    private val store: OriginalStore,
    private val jobs: JobQueue,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun captureText(text: String, sourceApp: String?): CaptureResult {
        val clean = text.trim()
        if (clean.isEmpty()) return CaptureResult.Rejected("Nothing to save")
        val sha = store.hashText(clean)
        return save(Evidence(kind = EvidenceKind.TEXT, originalPath = null, text = clean, sha256 = sha, mime = "text/plain", sizeBytes = clean.toByteArray().size.toLong(), sourceApp = sourceApp, capturedAt = clock()))
    }

    suspend fun captureStream(open: () -> InputStream, mime: String, sourceApp: String?): CaptureResult {
        val stored = try {
            withContext(Dispatchers.IO) { store.write(open) }
        } catch (e: TooLargeException) {
            return CaptureResult.Rejected("That file is too large for Cortex (limit 200 MB)")
        }
        return save(Evidence(kind = kindFor(mime), originalPath = stored.file.absolutePath, text = null, sha256 = stored.sha256, mime = mime, sizeBytes = stored.sizeBytes, sourceApp = sourceApp, capturedAt = clock()))
    }

    private suspend fun save(e: Evidence): CaptureResult {
        val id = withContext(Dispatchers.IO) { dao.insert(e) }
        if (id < 0) {
            val existing = withContext(Dispatchers.IO) { dao.bySha(e.sha256) }!!
            return CaptureResult.Saved(existing.id, duplicate = true)
        }
        jobs.enqueue(STAGE_PERCEIVE, id)
        return CaptureResult.Saved(id, duplicate = false)
    }

    companion object {
        fun kindFor(mime: String): EvidenceKind = when {
            mime.startsWith("image/") -> EvidenceKind.IMAGE
            mime == "application/pdf" -> EvidenceKind.PDF
            mime.startsWith("audio/") -> EvidenceKind.AUDIO
            else -> EvidenceKind.TEXT
        }
    }
}
