package com.kareem.cortex.prime.data.evidence

import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.Job
import com.kareem.cortex.prime.jobs.JobQueue
import com.kareem.cortex.prime.testDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

class FakeJobs : JobQueue {
    val enqueued = mutableListOf<Pair<String, Long>>()
    override suspend fun enqueue(stage: String, evidenceId: Long) { enqueued += stage to evidenceId }
    override suspend fun claim(stage: String, leaseMs: Long): Job? = null
    override suspend fun complete(id: Long) {}
    override suspend fun fail(id: Long, error: String) {}
    override suspend fun park(id: Long, reason: String) {}
    override suspend fun retry(stage: String, evidenceId: Long) {}
    override suspend fun unpark(reason: String) {}
    override fun observeAll(): Flow<List<Job>> = emptyFlow()
}

/** Yields n zero bytes without allocating them. */
class ZeroStream(private var remaining: Long) : InputStream() {
    override fun read(): Int = if (remaining-- > 0) 0 else -1
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = minOf(len.toLong(), remaining).toInt(); java.util.Arrays.fill(b, off, off + n, 0); remaining -= n; return n
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EvidenceRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: CortexDatabase
    private lateinit var store: OriginalStore
    private val fakeJobs = FakeJobs()
    private lateinit var repo: EvidenceRepository

    @Before fun setUp() {
        db = testDb(); store = OriginalStore(tmp.newFolder("files"))
        repo = EvidenceRepository(db.evidence(), store, fakeJobs) { 1_000L }
    }
    @After fun tearDown() = db.close()

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun sameFileTwiceReturnsExisting() = runTest {
        val a = repo.captureStream({ bytes(1024).inputStream() }, "image/png", "Gallery") as CaptureResult.Saved
        val b = repo.captureStream({ bytes(1024).inputStream() }, "image/png", "Gallery") as CaptureResult.Saved
        assertEquals(a.id, b.id)
        assertTrue(b.duplicate)
        assertEquals(1, store.root.resolve("originals").list()!!.size)
    }

    @Test fun oversizedFileIsRefusedAndLeavesNothing() = runTest {
        val r = repo.captureStream({ ZeroStream(MAX_CAPTURE_BYTES + 1) }, "video/mp4", null)
        assertEquals(CaptureResult.Rejected("That file is too large for Cortex (limit 200 MB)"), r)
        assertTrue(store.root.walk().none { it.isFile })
    }

    @Test fun blankTextIsRejected() = runTest {
        assertEquals(CaptureResult.Rejected("Nothing to save"), repo.captureText("  \n ", null))
    }

    @Test fun newEvidenceEnqueuesPerceive() = runTest {
        val s = repo.captureText("call mum", null) as CaptureResult.Saved
        assertEquals(listOf("perceive" to s.id), fakeJobs.enqueued)
    }

    @Test fun imageKindComesFromMime() = runTest {
        val s = repo.captureStream({ bytes(10).inputStream() }, "image/jpeg", null) as CaptureResult.Saved
        assertEquals(com.kareem.cortex.prime.data.db.EvidenceKind.IMAGE, db.evidence().byId(s.id)!!.kind)
    }
}
