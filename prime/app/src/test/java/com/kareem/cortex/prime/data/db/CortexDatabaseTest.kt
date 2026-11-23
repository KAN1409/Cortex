package com.kareem.cortex.prime.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CortexDatabaseTest {
    private lateinit var db: CortexDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CortexDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    private fun ev(sha: String, t: Long = 1) =
        Evidence(kind = EvidenceKind.TEXT, originalPath = null, text = sha, sha256 = sha, mime = "text/plain", sizeBytes = 1, sourceApp = null, capturedAt = t)

    private fun job(stage: String, evidenceId: Long) =
        Job(stage = stage, evidenceId = evidenceId, state = JobState.WAITING, attempts = 0, leaseUntil = 0, lastError = null, nextRunAt = 0, updatedAt = 0)

    @Test fun duplicateShaIsIgnored() {
        val a = ev("x")
        assertTrue(db.evidence().insert(a) > 0)
        assertEquals(-1L, db.evidence().insert(a.copy(capturedAt = 2)))
    }

    @Test fun timelineIsNewestFirst() = runTest {
        db.evidence().insert(ev("a", t = 1)); db.evidence().insert(ev("b", t = 2))
        assertEquals(listOf("b", "a"), db.evidence().timeline().first().map { it.sha256 })
    }

    @Test fun oneJobPerStageAndEvidence() {
        assertTrue(db.jobs().insert(job("perceive", 1)) > 0)
        assertEquals(-1L, db.jobs().insert(job("perceive", 1)))
    }
}
