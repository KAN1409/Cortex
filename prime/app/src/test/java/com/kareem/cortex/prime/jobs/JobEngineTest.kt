package com.kareem.cortex.prime.jobs

import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.JobState
import com.kareem.cortex.prime.testDb
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobEngineTest {
    private lateinit var db: CortexDatabase
    private var now = 1_000_000L
    private lateinit var q: RoomJobQueue

    @Before fun setUp() { db = testDb(); q = RoomJobQueue(db.jobs()) { now } }
    @After fun tearDown() = db.close()

    @Test fun enqueueIsIdempotent() = runTest {
        q.enqueue("perceive", 1); q.enqueue("perceive", 1)
        assertEquals(JobState.WAITING, db.jobs().forEvidence(1)!!.state)
        q.claim("perceive", 1_000); assertNull(q.claim("perceive", 1_000))
    }

    @Test fun expiredLeaseIsReclaimed() = runTest {
        q.enqueue("perceive", 1)
        val j = q.claim("perceive", 1_000)!!
        now += 1_001
        val again = q.claim("perceive", 1_000)!!
        assertEquals(j.id, again.id)
        assertEquals(2, again.attempts)
    }

    @Test fun liveLeaseIsNotDoubleClaimed() = runTest {
        q.enqueue("perceive", 1)
        q.claim("perceive", 1_000)
        assertNull(q.claim("perceive", 1_000))
    }

    @Test fun threeFailuresEndFailed() = runTest {
        q.enqueue("perceive", 1)
        repeat(3) { val j = q.claim("perceive", 1_000)!!; q.fail(j.id, "boom"); now += 10 * 60_000 }
        assertEquals(JobState.FAILED, db.jobs().forEvidence(1)!!.state)
    }

    @Test fun failureBacksOffExponentially() = runTest {
        q.enqueue("perceive", 1)
        val j = q.claim("perceive", 1_000)!!; q.fail(j.id, "boom")
        assertEquals(now + 30_000, db.jobs().byId(j.id).nextRunAt)
        now += 30_000
        val j2 = q.claim("perceive", 1_000)!!; q.fail(j2.id, "boom")
        assertEquals(now + 60_000, db.jobs().byId(j.id).nextRunAt)
    }

    private class Handler(override val stage: String, override val timeLimitMs: Long, val body: suspend (Long) -> Unit) : StageHandler {
        override suspend fun run(evidenceId: Long) = body(evidenceId)
    }

    @Test fun slowHandlerTimesOutAndQueueMovesOn() = runTest {
        q.enqueue("perceive", 1); q.enqueue("perceive", 2)
        val runner = JobRunner(q, listOf(Handler("perceive", 100) { id -> if (id == 1L) delay(10_000) }))
        runner.drain()
        assertEquals("timeout", db.jobs().forEvidence(1)!!.lastError)
        assertEquals(JobState.DONE, db.jobs().forEvidence(2)!!.state)
    }

    @Test fun unsupportedKindIsParkedNotFailed() = runTest {
        q.enqueue("perceive", 7)
        val runner = JobRunner(q, listOf(Handler("perceive", 100) { throw NotYetSupported() }))
        runner.drain()
        val j = db.jobs().forEvidence(7)!!
        assertEquals(JobState.WAITING, j.state)
        assertEquals("not_supported_yet", j.lastError)
        assertEquals(0, j.attempts)
        assertEquals(Long.MAX_VALUE, j.nextRunAt)
    }

    @Test fun retryResetsAFailedJob() = runTest {
        q.enqueue("perceive", 1)
        repeat(3) { val j = q.claim("perceive", 1_000)!!; q.fail(j.id, "boom"); now += 10 * 60_000 }
        q.retry("perceive", 1)
        val j = db.jobs().forEvidence(1)!!
        assertEquals(JobState.WAITING, j.state); assertEquals(0, j.attempts); assertEquals(null, j.lastError)
    }
}
