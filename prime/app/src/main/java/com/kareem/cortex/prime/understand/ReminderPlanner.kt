package com.kareem.cortex.prime.understand

import java.time.LocalDateTime
import java.time.LocalTime

/** Spec §6.5 timing: date-only → 20:00 the evening before + 09:00 on the day; "tomorrow" → 10:00; timed → 1 h before. */
object ReminderPlanner {
    val QUIET_START: LocalTime = LocalTime.of(23, 0)
    val QUIET_END: LocalTime = LocalTime.of(8, 0)

    private fun quiet(t: LocalTime) = t >= QUIET_START || t < QUIET_END

    fun plan(due: Due, now: LocalDateTime, relativeTomorrow: Boolean = due.relativeDay): List<LocalDateTime> {
        val eventAt = due.time?.let { due.date.atTime(it) }
        val raw = when {
            eventAt != null -> listOf(eventAt.minusHours(1))
            relativeTomorrow -> listOf(due.date.atTime(10, 0))
            else -> listOf(due.date.minusDays(1).atTime(20, 0), due.date.atTime(9, 0))
        }
        return raw.map { t ->
            if (!quiet(t.toLocalTime())) t else {
                val pushed = if (t.toLocalTime() >= QUIET_START) t.toLocalDate().plusDays(1).atTime(QUIET_END) else t.toLocalDate().atTime(QUIET_END)
                // Never remind after the thing itself: fall back to 22:00 the evening before.
                if (eventAt != null && !pushed.isBefore(eventAt)) eventAt.toLocalDate().minusDays(1).atTime(22, 0) else pushed
            }
        }.filter { it.isAfter(now) }.distinct().sorted()
    }
}
