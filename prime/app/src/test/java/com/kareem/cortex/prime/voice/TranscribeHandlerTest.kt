package com.kareem.cortex.prime.voice

import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.JobState
import com.kareem.cortex.prime.jobs.JobRunner
import com.kareem.cortex.prime.jobs.RoomJobQueue
import com.kareem.cortex.prime.jobs.TextPerceiveHandler
import com.kareem.cortex.prime.testDb
import com.kareem.cortex.prime.ui.memory.ProcessingLabel
import com.kareem.cortex.prime.ui.memory.processingLabel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranscribeHandlerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: CortexDatabase
    private var ready = false
    private lateinit var q: RoomJobQueue
    private val engine = object : SpeechEngine {
        override suspend fun transcribe(pcm: FloatArray) = EngineResult("يعني لازم أجدد الرخصة", "ar")
    }

    @Before fun setUp() { db = testDb(); q = RoomJobQueue(db.jobs()) { 1_000L } }
    @After fun tearDown() = db.close()

    private fun audio(): Long {
        val f = tmp.newFile("n.wav")
        val pcm = ShortArray(16_000)
        f.outputStream().use { WavCodec.writeHeader(it, 16_000, 1, pcm.size * 2); it.write(WavCodec.toBytes(pcm)) }
        return db.evidence().insert(Evidence(kind = EvidenceKind.AUDIO, originalPath = f.absolutePath, text = null, sha256 = "s", mime = "audio/wav", sizeBytes = f.length(), sourceApp = null, capturedAt = 1))
    }

    private fun runner() = JobRunner(q, listOf(PerceiveRouter(TextPerceiveHandler(db.evidence(), db.perception()), TranscribeHandler(db.evidence(), db.perception(), { ready }, { engine }))))

    @Test fun parkedWhileModelMissingThenTranscribedAfterReady() = runTest {
        val id = audio(); q.enqueue("perceive", id)
        runner().drain()
        val parked = db.jobs().forEvidence(id)!!
        assertEquals("lastError=${parked.lastError} attempts=${parked.attempts}", ProcessingLabel.MODEL, processingLabel(parked))
        ready = true; q.unpark(MODEL_MISSING)
        runner().drain()
        assertEquals(JobState.DONE, db.jobs().forEvidence(id)!!.state)
        val p = db.perception().forEvidence(id).single()
        assertEquals("transcript", p.kind); assertEquals("لازم أجدد الرخصة", p.text); assertEquals("ar", p.language)
    }

    @Test fun timeLimitScalesWithDuration() = assertEquals(4 * 90_000L + 60_000L, transcribeLimitMs(90_000))
}
