package com.kareem.cortex.prime.voice

import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.PerceptionDao
import com.kareem.cortex.prime.jobs.StageHandler

const val MODEL_MISSING = "model_missing"
fun transcribeLimitMs(durationMs: Long): Long = TODO()

class TranscribeHandler(
    private val evidence: EvidenceDao,
    private val perception: PerceptionDao,
    private val modelReady: () -> Boolean,
    private val engine: () -> SpeechEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(evidenceId: Long): Unit = TODO()
    fun limitFor(evidenceId: Long): Long = TODO()
}

/** The single "perceive" stage: routes each evidence kind to the handler that can read it. */
class PerceiveRouter(private val text: StageHandler, private val voice: TranscribeHandler) : StageHandler {
    override val stage = "perceive"
    override val timeLimitMs = 30 * 60_000L
    override suspend fun run(evidenceId: Long): Unit = TODO()
}
