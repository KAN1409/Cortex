package com.kareem.cortex.prime.data.evidence

import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.jobs.JobQueue
import java.io.InputStream

sealed interface CaptureResult {
    data class Saved(val id: Long, val duplicate: Boolean) : CaptureResult
    data class Rejected(val message: String) : CaptureResult
}

class EvidenceRepository(
    private val dao: EvidenceDao,
    private val store: OriginalStore,
    private val jobs: JobQueue,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun captureText(text: String, sourceApp: String?): CaptureResult = TODO()
    suspend fun captureStream(open: () -> InputStream, mime: String, sourceApp: String?): CaptureResult = TODO()
}
