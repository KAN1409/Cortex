package com.kareem.cortex.prime.understand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Reference moment: Monday 23 Nov 2026, 21:00 (the owner's real usage period). */
class DateRulesTest {
    private val now = LocalDateTime.of(2026, 11, 23, 21, 0)
    private fun d(s: String) = DateRules.find(s, now)
    private fun day(y: Int, m: Int, dd: Int) = LocalDate.of(y, m, dd)

    @Test fun egyptianRelativeDays() {
        assertEquals(day(2026, 11, 24), d("لازم أكلم ماما بكرة")!!.date)
        assertEquals(day(2026, 11, 24), d("بكره الصبح")!!.date)
        assertEquals(day(2026, 11, 25), d("بعد بكرة")!!.date)
        assertEquals(day(2026, 11, 23), d("النهاردة بالليل")!!.date)
        assertEquals(day(2026, 11, 26), d("كمان ٣ أيام")!!.date)
    }

    @Test fun arabicWeekdays() {
        assertEquals(day(2026, 11, 27), d("موضوع الجمعة")!!.date)
        assertEquals(day(2026, 11, 27), d("يوم الجمعة الجاية")!!.date)
        assertEquals(day(2026, 11, 29), d("الحد")!!.date)
        assertEquals(day(2026, 11, 24), d("يوم التلات")!!.date)
    }

    @Test fun arabicDayOfMonthInWordsAndDigits() {
        assertEquals(day(2026, 11, 30), d("قبل يوم تلاتين")!!.date)
        assertEquals(day(2026, 11, 30), d("قبل يوم ٣٠")!!.date)
        assertEquals(day(2026, 12, 5), d("يوم خمسة")!!.date)          // the 5th has passed this month → next month
        assertEquals(day(2026, 11, 30), d("تاريخ الانتهاء ٣٠/١١/٢٠٢٦")!!.date)
        assertEquals(day(2026, 12, 21), d("يوم واحد وعشرين")!!.date)
    }

    @Test fun englishForms() {
        assertEquals(day(2026, 11, 24), d("call the bank tomorrow")!!.date)
        assertEquals(day(2026, 11, 25), d("day after tomorrow")!!.date)
        assertEquals(day(2026, 11, 27), d("next Friday")!!.date)
        assertEquals(day(2026, 11, 30), d("renew before the 30th")!!.date)
        assertEquals(day(2026, 11, 30), d("expires 30/11/2026")!!.date)
        assertEquals(day(2026, 11, 26), d("Return by Nov 26")!!.date)
        assertEquals(day(2026, 12, 3), d("due 3 December")!!.date)
        assertEquals(day(2026, 11, 30), d("in 7 days")!!.date)
    }

    @Test fun timesOfDay() {
        val t = d("tomorrow at 5:30 pm")!!
        assertEquals(LocalTime.of(17, 30), t.time); assertEquals(Precision.TIME, t.precision)
        assertEquals(LocalTime.of(17, 0), d("بكرة الساعة ٥ العصر")!!.time)
        assertEquals(LocalTime.of(9, 0), d("بكرة الساعة تسعة الصبح")!!.time)
        assertEquals(Precision.DAY, d("بكرة")!!.precision)
    }

    @Test fun spanPointsAtTheWords() {
        val s = "please renew the licence before the 30th, thanks"
        val r = DateRules.find(s, now)!!
        assertEquals("before the 30th", s.substring(r.start, r.end))
    }

    @Test fun noDateMeansNull() {
        assertNull(d("I like this song"))
        assertNull(d("فكرة حلوة للمشروع"))
    }
}
