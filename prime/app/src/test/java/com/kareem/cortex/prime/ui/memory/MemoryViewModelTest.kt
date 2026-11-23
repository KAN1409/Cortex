package com.kareem.cortex.prime.ui.memory

import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.testDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryViewModelTest {
    private lateinit var db: CortexDatabase
    @Before fun setUp() { db = testDb() }
    @After fun tearDown() = db.close()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 23)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ev(sha: String, t: Long) = Evidence(kind = EvidenceKind.TEXT, originalPath = null, text = sha, sha256 = sha, mime = "text/plain", sizeBytes = 1, sourceApp = null, capturedAt = t)

    @Test fun rowsGroupByDayNewestFirst() = runTest {
        db.evidence().insert(ev("a", at(today, 9))); db.evidence().insert(ev("b", at(today, 18)))
        db.evidence().insert(ev("c", at(today.minusDays(1), 20)))
        val g = memoryRows(db.evidence().timeline(), db.jobs().observeAll(), zone).first { it.isNotEmpty() }
        assertEquals(listOf(today, today.minusDays(1)), g.keys.toList())
        assertEquals(listOf("b", "a"), g[today]!!.map { it.title })
    }
}
