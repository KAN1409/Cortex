package com.kareem.cortex.prime.vision

import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.Perception
import com.kareem.cortex.prime.data.db.PerceptionDao
import com.kareem.cortex.prime.jobs.NotYetSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

const val OCR_LIMIT_MS = 45_000L

/** Reads text out of images. One image never holds the queue: 45 s limit, then the job is marked timed out. */
class OcrHandler(
    private val evidence: EvidenceDao,
    private val perception: PerceptionDao,
    private val engine: OcrEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limitMs: Long = OCR_LIMIT_MS,
) {
    suspend fun run(evidenceId: Long) {
        val e = withContext(Dispatchers.IO) { evidence.byId(evidenceId) } ?: error("evidence missing")
        if (e.kind != EvidenceKind.IMAGE) throw NotYetSupported()
        val out = withTimeout(limitMs) { engine.read(e.originalPath ?: error("image missing")) }
        withContext(Dispatchers.IO) {
            if (perception.forEvidence(evidenceId).none { it.kind == "ocr" })
                perception.insert(Perception(evidenceId = evidenceId, kind = "ocr", text = out.text.trim(), language = out.language, confidence = 0.7, engine = "mlkit+tesseract-ara-fast", createdAt = clock()))
        }
    }
}
