package com.kareem.cortex.prime.understand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val now = LocalDateTime.of(2026, 11, 23, 21, 0)

    @Test fun dateOnlyGetsEveningBeforeAndMorningOf() =
        assertEquals(listOf(LocalDateTime.of(2026, 11, 29, 20, 0), LocalDateTime.of(2026, 11, 30, 9, 0)),
            ReminderPlanner.plan(Due(LocalDate.of(2026, 11, 30), null, Precision.DAY, 0, 0), now, relativeTomorrow = false))

    @Test fun tomorrowWithoutTimeIsTenAm() =
        assertEquals(listOf(LocalDateTime.of(2026, 11, 24, 10, 0)),
            ReminderPlanner.plan(Due(LocalDate.of(2026, 11, 24), null, Precision.DAY, 0, 0), now, relativeTomorrow = true))

    @Test fun timedIsOneHourBefore() =
        assertEquals(listOf(LocalDateTime.of(2026, 11, 24, 16, 30)),
            ReminderPlanner.plan(Due(LocalDate.of(2026, 11, 24), LocalTime.of(17, 30), Precision.TIME, 0, 0), now, relativeTomorrow = false))

    @Test fun pastTimesAreDroppedAndQuietHoursPushed() {
        // due 07:30: one hour before is 06:30, inside quiet hours; 08:00 would be after the event, so it goes to 22:00 the evening before
        val p = ReminderPlanner.plan(Due(LocalDate.of(2026, 11, 24), LocalTime.of(7, 30), Precision.TIME, 0, 0), now, relativeTomorrow = false)
        assertEquals(listOf(LocalDateTime.of(2026, 11, 23, 22, 0)), p)
        val today = ReminderPlanner.plan(Due(LocalDate.of(2026, 11, 23), null, Precision.DAY, 0, 0), now, relativeTomorrow = false)
        assertTrue(today.isEmpty() || today.all { it.isAfter(now) })
    }
}
