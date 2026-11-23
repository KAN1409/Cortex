package com.kareem.cortex.prime.voice

import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.Perception
import com.kareem.cortex.prime.data.db.PerceptionDao
import com.kareem.cortex.prime.jobs.NotYetSupported
import com.kareem.cortex.prime.jobs.StageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

const val MODEL_MISSING = "model_missing"
fun transcribeLimitMs(durationMs: Long): Long = 4 * durationMs + 60_000L

class TranscribeHandler(
    private val evidence: EvidenceDao,
    private val perception: PerceptionDao,
    private val modelReady: () -> Boolean,
    private val engine: () -> SpeechEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(evidenceId: Long) {
        val e = withContext(Dispatchers.IO) { evidence.byId(evidenceId) } ?: error("evidence missing")
        if (e.kind != EvidenceKind.AUDIO) throw NotYetSupported()
        if (!modelReady()) throw NotYetSupported(MODEL_MISSING)
        val file = File(e.originalPath ?: error("audio file missing"))
        val pcm = withContext(Dispatchers.IO) { WavCodec.pcmToFloat(WavCodec.readPcm16(file)) }
        val durationMs = pcm.size * 1000L / 16_000
        val result = withTimeout(transcribeLimitMs(durationMs)) { engine().transcribe(pcm) }
        val text = TranscriptCleaner.clean(result.text)
        withContext(Dispatchers.IO) {
            if (perception.forEvidence(evidenceId).none { it.kind == "transcript" })
                perception.insert(Perception(evidenceId = evidenceId, kind = "transcript", text = text, language = result.language, confidence = 0.8, engine = "whisper.cpp", createdAt = clock()))
        }
    }

}

/** The single "perceive" stage: text reads itself; anything text can't read goes to voice, which refuses non-audio. */
class PerceiveRouter(private val text: StageHandler, private val voice: TranscribeHandler) : StageHandler {
    override val stage = "perceive"
    // Covers the longest note we accept; the voice handler applies its own per-note limit inside.
    override val timeLimitMs = 30 * 60_000L

    override suspend fun run(evidenceId: Long) {
        try { text.run(evidenceId) } catch (e: NotYetSupported) { voice.run(evidenceId) }
    }
}
