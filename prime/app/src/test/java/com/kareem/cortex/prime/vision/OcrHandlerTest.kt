package com.kareem.cortex.prime.vision

import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.JobState
import com.kareem.cortex.prime.jobs.JobRunner
import com.kareem.cortex.prime.jobs.RoomJobQueue
import com.kareem.cortex.prime.jobs.TextPerceiveHandler
import com.kareem.cortex.prime.testDb
import com.kareem.cortex.prime.voice.PerceiveRouter
import com.kareem.cortex.prime.voice.SpeechEngine
import com.kareem.cortex.prime.voice.EngineResult
import com.kareem.cortex.prime.voice.TranscribeHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrHandlerTest {
    private lateinit var db: CortexDatabase
    private lateinit var q: RoomJobQueue
    @Before fun setUp() { db = testDb(); q = RoomJobQueue(db.jobs()) { 1_000L } }
    @After fun tearDown() = db.close()

    private fun image(path: String) = db.evidence().insert(Evidence(kind = EvidenceKind.IMAGE, originalPath = path, text = null, sha256 = path, mime = "image/png", sizeBytes = 1, sourceApp = "Screenshots", capturedAt = 1))

    private fun runner(ocr: OcrEngine) = JobRunner(q, listOf(PerceiveRouter(
        TextPerceiveHandler(db.evidence(), db.perception()),
        TranscribeHandler(db.evidence(), db.perception(), { true }, { object : SpeechEngine { override suspend fun transcribe(pcm: FloatArray) = EngineResult("", null) } }),
        OcrHandler(db.evidence(), db.perception(), ocr, limitMs = 300),
    )))

    @Test fun ocrTextIsStored() = runBlocking<Unit> {
        val id = image("content://a"); q.enqueue("perceive", id)
        runner(object : OcrEngine { override suspend fun read(path: String) = OcrOutput("Licence expires 30/11", "en") }).drain()
        val j = db.jobs().forEvidence(id)!!
        assertEquals("lastError=${j.lastError}", JobState.DONE, j.state)
        assertEquals("Licence expires 30/11", db.perception().forEvidence(id).single { it.kind == "ocr" }.text)
    }

    @Test fun oneStuckImageDoesNotBlockTheNext() = runBlocking<Unit> {
        val slow = image("content://slow"); val fast = image("content://fast")
        q.enqueue("perceive", slow); q.enqueue("perceive", fast)
        runner(object : OcrEngine { override suspend fun read(path: String): OcrOutput { if (path.endsWith("slow")) delay(5_000); return OcrOutput("ok text here", "en") } }).drain()
        assertEquals("timeout", db.jobs().forEvidence(slow)!!.lastError)
        val f = db.jobs().forEvidence(fast)!!
        assertEquals("lastError=${f.lastError}", JobState.DONE, f.state)
    }
}
