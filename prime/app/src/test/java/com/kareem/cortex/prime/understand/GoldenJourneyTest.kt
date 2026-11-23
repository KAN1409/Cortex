package com.kareem.cortex.prime.understand

import com.kareem.cortex.prime.data.db.Area
import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.LoopState
import com.kareem.cortex.prime.data.db.OpenLoop
import com.kareem.cortex.prime.data.db.Perception
import com.kareem.cortex.prime.data.db.Reminder
import com.kareem.cortex.prime.data.db.ReminderState
import com.kareem.cortex.prime.testDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

/** Spec §11 golden journey: voice transcript → open loop with source span → reminders 29th 20:00 + 30th 09:00 → done cancels. */
@RunWith(RobolectricTestRunner::class)
class GoldenJourneyTest {
    private lateinit var db: CortexDatabase
    private val zone = ZoneId.of("Africa/Cairo")
    private val now = LocalDateTime.of(2026, 11, 23, 21, 0)
    private val scheduled = mutableListOf<Long>(); private val cancelled = mutableListOf<Long>()
    private val scheduler = object : ReminderScheduler {
        override fun schedule(r: Reminder, loop: OpenLoop) { scheduled += r.fireAt }
        override fun cancel(reminderId: Long) { cancelled += reminderId }
    }
    @Before fun setUp() { db = testDb() }
    @After fun tearDown() = db.close()

    private fun at(y: Int, m: Int, d: Int, h: Int) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun licenceVoiceNoteBecomesReminders() = runTest {
        val said = "لازم أجدد رخصة العربية قبل يوم تلاتين"
        val id = db.evidence().insert(Evidence(kind = EvidenceKind.AUDIO, originalPath = "/x.wav", text = null, sha256 = "a", mime = "audio/wav", sizeBytes = 1, sourceApp = null, capturedAt = 1))
        db.perception().insert(Perception(evidenceId = id, kind = "transcript", text = said, language = "ar", confidence = 0.8, engine = "t", createdAt = 1))
        val h = UnderstandHandler(db.evidence(), db.perception(), db.loops(), scheduler, { now }, zone)
        h.run(id); h.run(id) // idempotent

        val loop = db.loops().byId(1)!!
        assertEquals(LoopState.OPEN, loop.state)
        assertEquals(said, said.substring(loop.spanStart, loop.spanEnd)) // the whole spoken commitment is the source span
        assertEquals("أجدد رخصة العربية", loop.action)
        assertTrue(said.substring(loop.spanStart, loop.spanEnd).contains("رخصة"))
        assertEquals(listOf(at(2026, 11, 29, 20), at(2026, 11, 30, 9)), scheduled)

        LoopActions(db.loops(), scheduler, { System.currentTimeMillis() }).done(loop.id)
        assertEquals(LoopState.DONE, db.loops().byId(1)!!.state)
        assertTrue(db.loops().scheduledFor(1).isEmpty()); assertEquals(2, cancelled.size)
    }

    @Test fun areaIsGuessed() {
        assertEquals(Area.FAMILY, AreaGuess.guess("أكلم ماما بكرة"))
        assertEquals(Area.HEALTH, AreaGuess.guess("dentist appointment Friday"))
        assertEquals(Area.WORK, AreaGuess.guess("send the invoice to the client"))
        assertEquals(Area.LIFE, AreaGuess.guess("renew the car licence"))
    }
}
