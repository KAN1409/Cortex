package com.kareem.cortex.prime.jobs

import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.Perception
import com.kareem.cortex.prime.data.db.PerceptionDao
import com.kareem.cortex.prime.data.evidence.STAGE_PERCEIVE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Milestone 1 perception: text is its own perception; other kinds wait for later milestones. */
class TextPerceiveHandler(
    private val evidence: EvidenceDao,
    private val perception: PerceptionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : StageHandler {
    override val stage = STAGE_PERCEIVE
    override val timeLimitMs = 5_000L

    override suspend fun run(evidenceId: Long) = withContext(Dispatchers.IO) {
        val e = evidence.byId(evidenceId) ?: throw IllegalStateException("evidence missing")
        if (e.kind != EvidenceKind.TEXT || e.text == null) throw NotYetSupported()
        if (perception.forEvidence(evidenceId).none { it.kind == "text" })
            perception.insert(Perception(evidenceId = evidenceId, kind = "text", text = e.text, language = null, confidence = 1.0, engine = "identity", createdAt = clock()))
        Unit
    }
}
