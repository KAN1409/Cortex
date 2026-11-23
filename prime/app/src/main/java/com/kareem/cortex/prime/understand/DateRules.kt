package com.kareem.cortex.prime.understand

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class Precision { DAY, TIME }

/** A due moment found in text; [start]/[end] are offsets into the original string (the exact source words). */
data class Due(val date: LocalDate, val time: LocalTime?, val precision: Precision, val start: Int, val end: Int, val relativeDay: Boolean = false)

/**
 * Rule-based date/time finder for Egyptian Arabic and English (spec §6.4).
 * Works on a same-length normalised copy so every match maps back to the original characters.
 */
object DateRules {
    private const val L = "(?<![\\p{L}\\p{N}])"
    private const val R = "(?![\\p{L}\\p{N}])"
    private const val AR_PRE = "(?:و|ف|ب)?"

    fun normalize(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(when (c) {
            in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰')
            'أ', 'إ', 'آ' -> 'ا'; 'ة' -> 'ه'; 'ى' -> 'ي'
            else -> c
        })
    }.let { if (it.length == s.length) it else s.lowercase() }

    private val units = mapOf(
        "واحد" to 1, "اتنين" to 2, "اثنين" to 2, "تلاته" to 3, "ثلاثه" to 3, "اربعه" to 4, "خمسه" to 5, "سته" to 6,
        "سبعه" to 7, "تمانيه" to 8, "ثمانيه" to 8, "تسعه" to 9,
    )
    private val numberWords = units + mapOf(
        "عشره" to 10, "حداشر" to 11, "اتناشر" to 12, "تلتاشر" to 13, "اربعتاشر" to 14, "خمستاشر" to 15, "ستاشر" to 16,
        "سبعتاشر" to 17, "تمنتاشر" to 18, "تسعتاشر" to 19, "عشرين" to 20, "تلاتين" to 30, "ثلاثين" to 30,
    )
    private val unitAlt = units.keys.joinToString("|")
    private val numAlt = "(?:(?:$unitAlt)\\s+و\\s*(?:عشرين|تلاتين|ثلاثين)|${numberWords.keys.sortedByDescending { it.length }.joinToString("|")}|\\d{1,2})"

    private fun num(s: String): Int? {
        s.trim().toIntOrNull()?.let { return it }
        Regex("($unitAlt)\\s+و\\s*(عشرين|تلاتين|ثلاثين)").matchEntire(s.trim())?.let { m -> return units[m.groupValues[1]]!! + numberWords[m.groupValues[2]]!! }
        return numberWords[s.trim()]
    }

    private val enMonths = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val arMonths = listOf("يناير", "فبراير", "مارس", "ابريل", "مايو", "يونيو", "يوليو", "اغسطس", "سبتمبر", "اكتوبر", "نوفمبر", "ديسمبر")
    private val enMonthRe = "(${enMonths.joinToString("|")})[a-z]*\\.?"
    private val arMonthRe = "(${arMonths.joinToString("|")})"

    private val weekdaysEn = mapOf("monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY)
    private val weekdaysAr = mapOf(
        "الاحد" to DayOfWeek.SUNDAY, "الحد" to DayOfWeek.SUNDAY, "الاتنين" to DayOfWeek.MONDAY, "الاثنين" to DayOfWeek.MONDAY,
        "التلات" to DayOfWeek.TUESDAY, "الثلاثاء" to DayOfWeek.TUESDAY, "التلاتاء" to DayOfWeek.TUESDAY, "الاربع" to DayOfWeek.WEDNESDAY,
        "الاربعاء" to DayOfWeek.WEDNESDAY, "الخميس" to DayOfWeek.THURSDAY, "الجمعه" to DayOfWeek.FRIDAY, "السبت" to DayOfWeek.SATURDAY,
    )

    private val prefix = "(?:(?:before|by|on|until|due|قبل|لحد|في)\\s+)?"

    private data class Hit(val start: Int, val end: Int, val date: LocalDate, val relative: Boolean = false)

    fun find(text: String, now: LocalDateTime): Due? {
        val n = normalize(text)
        val today = now.toLocalDate()
        val hits = mutableListOf<Hit>()
        fun add(m: MatchResult, d: LocalDate?, rel: Boolean = false) { if (d != null) hits += Hit(m.range.first, m.range.last + 1, d, rel) }

        // dd/mm(/yyyy)
        Regex("$L$prefix(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?$R").findAll(n).forEach { m ->
            val dd = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt(); val yy = m.groupValues[3]
            add(m, safeDate(yy.takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }, mm, dd, today))
        }
        // 3 December / December 3 / ٣٠ نوفمبر
        Regex("$L$prefix(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$enMonthRe$R").findAll(n).forEach { m -> add(m, safeDate(null, enMonths.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt(), today)) }
        Regex("$L$prefix$enMonthRe\\s+(\\d{1,2})(?:st|nd|rd|th)?$R").findAll(n).forEach { m -> add(m, safeDate(null, enMonths.indexOf(m.groupValues[1]) + 1, m.groupValues[2].toInt(), today)) }
        Regex("$L$prefix(?:يوم\\s+)?(\\d{1,2})\\s+$arMonthRe$R").findAll(n).forEach { m -> add(m, safeDate(null, arMonths.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt(), today)) }
        // the 30th
        Regex("$L$prefix(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)$R").findAll(n).forEach { m -> add(m, dayOfMonth(m.groupValues[1].toInt(), today)) }
        // يوم تلاتين / قبل ٣٠ / قبل يوم ٣٠
        Regex("$L(?:(?:قبل|لحد|في)\\s+)?يوم\\s+($numAlt)$R").findAll(n).forEach { m -> num(m.groupValues[1])?.let { add(m, dayOfMonth(it, today)) } }
        Regex("$L(?:قبل|لحد)\\s+(\\d{1,2})$R").findAll(n).forEach { m -> add(m, dayOfMonth(m.groupValues[1].toInt(), today)) }
        // relative days
        Regex("${L}(?:day after tomorrow|${AR_PRE}بعد بكره)$R").findAll(n).forEach { m -> add(m, today.plusDays(2), rel = true) }
        Regex("${L}(?:tomorrow|${AR_PRE}بكره)$R").findAll(n).forEach { m -> if (hits.none { h -> m.range.first >= h.start && m.range.last < h.end }) add(m, today.plusDays(1), rel = true) }
        Regex("${L}(?:today|tonight|${AR_PRE}النهارده|${AR_PRE}الليله)$R").findAll(n).forEach { m -> add(m, today, rel = true) }
        Regex("${L}(?:in\\s+(\\d{1,2})\\s+days?|كمان\\s+(\\d{1,2})\\s+(?:ايام|يوم))$R").findAll(n).forEach { m -> add(m, today.plusDays((m.groupValues[1].ifEmpty { m.groupValues[2] }).toLong())) }
        Regex("${L}(?:in\\s+(\\d{1,2})\\s+weeks?|كمان\\s+(\\d{1,2})\\s+(?:اسابيع|اسبوع))$R").findAll(n).forEach { m -> add(m, today.plusWeeks((m.groupValues[1].ifEmpty { m.groupValues[2] }).toLong())) }
        Regex("${L}(?:next week|الاسبوع الجاي)$R").findAll(n).forEach { m -> add(m, today.plusWeeks(1).with(DayOfWeek.SUNDAY)) }
        // weekdays
        Regex("$L(?:(?:next|this|on)\\s+)?(${weekdaysEn.keys.joinToString("|")})$R").findAll(n).forEach { m -> add(m, nextWeekday(weekdaysEn.getValue(m.groupValues[1]), today)) }
        Regex("$L(?:يوم\\s+)?$AR_PRE(${weekdaysAr.keys.sortedByDescending { it.length }.joinToString("|")})(?:\\s+(?:الجاي|الجايه))?$R").findAll(n).forEach { m -> add(m, nextWeekday(weekdaysAr.getValue(m.groupValues[1]), today)) }

        val time = findTime(n)
        val hit = hits.minWithOrNull(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start })
        if (hit == null && time == null) return null
        val date = hit?.date ?: if (time!!.first.isAfter(now.toLocalTime())) today else today.plusDays(1)
        val start = minOf(hit?.start ?: Int.MAX_VALUE, time?.second ?: Int.MAX_VALUE)
        val end = maxOf(hit?.end ?: 0, if (time != null && hit == null) time.third else hit?.end ?: 0)
        return Due(date, time?.first, if (time != null) Precision.TIME else Precision.DAY, start, end, relativeDay = hit?.relative == true && hit.date == today.plusDays(1))
    }

    private fun findTime(n: String): Triple<LocalTime, Int, Int>? {
        fun hour(h: Int, period: String?): Int = when {
            period == null -> if (h in 1..7) h + 12 else h
            period in setOf("pm", "العصر", "الضهر", "الظهر", "بعد الضهر", "بالليل", "مساء", "المغرب", "بليل") -> if (h < 12) h + 12 else h
            else -> if (h == 12) 0 else h
        }
        val per = "(am|pm|الصبح|صباحا|العصر|الضهر|الظهر|بعد الضهر|بالليل|بليل|مساء|المغرب)"
        Regex("${L}الساعه\\s+($numAlt)(?::(\\d{2}))?(?:\\s+$per)?").find(n)?.let { m ->
            val h = num(m.groupValues[1]) ?: return@let
            return Triple(LocalTime.of(hour(h, m.groupValues[3].ifEmpty { null }).coerceIn(0, 23), m.groupValues[2].toIntOrNull() ?: 0), m.range.first, m.range.last + 1)
        }
        Regex("$L(?:at\\s+)?(\\d{1,2}):(\\d{2})\\s*(am|pm)?$R").find(n)?.let { m ->
            val h = m.groupValues[1].toInt(); val p = m.groupValues[3].ifEmpty { null }
            return Triple(LocalTime.of((if (p == null) h else hour(h, p)).coerceIn(0, 23), m.groupValues[2].toInt().coerceIn(0, 59)), m.range.first, m.range.last + 1)
        }
        Regex("$L(?:at\\s+)?(\\d{1,2})\\s*(am|pm)$R").find(n)?.let { m ->
            return Triple(LocalTime.of(hour(m.groupValues[1].toInt(), m.groupValues[2]).coerceIn(0, 23), 0), m.range.first, m.range.last + 1)
        }
        return null
    }

    private fun safeDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val y = year ?: today.year
        val d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) runCatching { LocalDate.of(y + 1, month, day) }.getOrNull() else d
    }

    private fun dayOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var m = if (day >= today.dayOfMonth) today.withDayOfMonth(1) else today.withDayOfMonth(1).plusMonths(1)
        repeat(3) { if (day <= m.lengthOfMonth()) return m.withDayOfMonth(day); m = m.plusMonths(1) }
        return null
    }

    private fun nextWeekday(w: DayOfWeek, today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(w))
}
