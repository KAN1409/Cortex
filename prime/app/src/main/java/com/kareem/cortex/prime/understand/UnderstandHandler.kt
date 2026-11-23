package com.kareem.cortex.prime.understand

import com.kareem.cortex.prime.data.db.Area
import com.kareem.cortex.prime.data.db.EvidenceDao
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.LoopDao
import com.kareem.cortex.prime.data.db.LoopState
import com.kareem.cortex.prime.data.db.OpenLoop
import com.kareem.cortex.prime.data.db.PerceptionDao
import com.kareem.cortex.prime.data.db.Reminder
import com.kareem.cortex.prime.data.db.ReminderState
import com.kareem.cortex.prime.jobs.StageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

const val STAGE_UNDERSTAND = "understand"

interface ReminderScheduler {
    fun schedule(r: Reminder, loop: OpenLoop)
    fun cancel(reminderId: Long)
}

/** Understand stage: perception text → open loops (with source spans) → planned reminders. Idempotent per evidence. */
class UnderstandHandler(
    private val evidence: EvidenceDao,
    private val perception: PerceptionDao,
    private val loops: LoopDao,
    private val scheduler: ReminderScheduler,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) : StageHandler {
    override val stage = STAGE_UNDERSTAND
    override val timeLimitMs = 20_000L

    override suspend fun run(evidenceId: Long) = withContext(Dispatchers.IO) {
        if (loops.countFor(evidenceId) > 0) return@withContext
        val e = evidence.byId(evidenceId) ?: return@withContext
        val p = perception.forEvidence(evidenceId).firstOrNull { it.text.isNotBlank() } ?: return@withContext
        val source = when (e.kind) { EvidenceKind.AUDIO -> SourceKind.VOICE; EvidenceKind.IMAGE -> SourceKind.SCREENSHOT; else -> SourceKind.TEXT }
        val t = now(); val stamp = t.atZone(zone).toInstant().toEpochMilli()
        for (c in OpenLoopExtractor.extract(p.text, t, source)) {
            val state = if (c.confidence >= 0.8) LoopState.OPEN else LoopState.UNCONFIRMED
            val dueAt = c.due?.let { d -> (d.time?.let { d.date.atTime(it) } ?: d.date.atTime(9, 0)).atZone(zone).toInstant().toEpochMilli() }
            val loop = OpenLoop(
                evidenceId = evidenceId, action = c.action.ifBlank { p.text.take(80) }, sourceText = p.text, spanStart = c.start, spanEnd = c.end,
                dueAt = dueAt, duePrecision = c.due?.precision?.name, confidence = c.confidence, state = state,
                area = AreaGuess.guess(p.text.substring(c.start, c.end)), createdBy = "rules", createdAt = stamp, updatedAt = stamp,
            )
            val id = loops.insert(loop)
            if (state == LoopState.OPEN && c.due != null) planAndSchedule(loop.copy(id = id), c.due, t)
        }
    }

    private fun planAndSchedule(loop: OpenLoop, due: Due, t: LocalDateTime) {
        for (at in ReminderPlanner.plan(due, t)) {
            val r = Reminder(openLoopId = loop.id, fireAt = at.atZone(zone).toInstant().toEpochMilli(), state = ReminderState.SCHEDULED)
            scheduler.schedule(r.copy(id = loops.insertReminder(r)), loop)
        }
    }
}

/** User actions on a loop. Every change also settles its reminders. */
class LoopActions(private val loops: LoopDao, private val scheduler: ReminderScheduler, private val clock: () -> Long = System::currentTimeMillis) {
    private fun cancelAll(loopId: Long) = loops.scheduledFor(loopId).forEach { scheduler.cancel(it.id); loops.setReminderState(it.id, ReminderState.CANCELLED) }

    fun done(loopId: Long) { val l = loops.byId(loopId) ?: return; cancelAll(loopId); loops.update(l.copy(state = LoopState.DONE, updatedAt = clock())) }
    fun dismiss(loopId: Long) { val l = loops.byId(loopId) ?: return; cancelAll(loopId); loops.update(l.copy(state = LoopState.DISMISSED, updatedAt = clock())) }

    /** "Is this a reminder?" → Yes: becomes open; one reminder at its due moment if it has one. */
    fun confirm(loopId: Long) {
        val l = loops.byId(loopId) ?: return
        loops.update(l.copy(state = LoopState.OPEN, updatedAt = clock()))
        l.dueAt?.takeIf { it > clock() }?.let { at -> snooze(loopId, at - 3_600_000L.coerceAtMost(at - clock() - 60_000)) }
    }

    fun snooze(loopId: Long, until: Long) {
        val l = loops.byId(loopId) ?: return
        cancelAll(loopId)
        val r = Reminder(openLoopId = loopId, fireAt = until, state = ReminderState.SCHEDULED)
        scheduler.schedule(r.copy(id = loops.insertReminder(r)), l)
    }

    fun setArea(loopId: Long, area: Area) { val l = loops.byId(loopId) ?: return; loops.update(l.copy(area = area, updatedAt = clock())) }
}

object AreaGuess {
    private val family = listOf("ماما", "بابا", "امي", "ابويا", "اخويا", "اختي", "مراتي", "جوزي", "الولاد", "ابني", "بنتي", "المدرسه", "mum", "mom", "dad", "mother", "father", "wife", "husband", "kids", "son", "daughter", "school", "family")
    private val health = listOf("دكتور", "الدكتور", "دوا", "الدوا", "صيدليه", "مستشفي", "تحليل", "اشعه", "doctor", "dentist", "medicine", "pharmacy", "hospital", "clinic", "pills", "gym", "appointment with dr")
    private val work = listOf("شغل", "الشغل", "اجتماع", "عميل", "العميل", "فاتوره", "مشروع", "المكتب", "مدير", "meeting", "client", "office", "invoice", "project", "manager", "deadline", "report", "po", "quotation")

    fun guess(text: String): Area {
        val n = DateRules.normalize(text)
        fun any(ws: List<String>) = ws.any { w -> Regex("(?<![\\p{L}])" + Regex.escape(DateRules.normalize(w)) + "(?![\\p{L}])").containsMatchIn(n) }
        return when { any(family) -> Area.FAMILY; any(health) -> Area.HEALTH; any(work) -> Area.WORK; else -> Area.LIFE }
    }
}
