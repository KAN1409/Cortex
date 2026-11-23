package com.kareem.cortex.prime.understand

import java.time.LocalDateTime

enum class SourceKind { VOICE, TEXT, SCREENSHOT }

/** A possible thing-to-remember, tied to the exact words it came from. */
data class LoopCandidate(val action: String, val due: Due?, val confidence: Double, val start: Int, val end: Int)

/**
 * Rules layer of spec §6.4: clauses with an intent or document cue become open loops.
 * Confidence ≥ 0.8 → open + reminders; 0.5–0.8 → "Is this a reminder?"; below → dropped.
 */
object OpenLoopExtractor {
    private val strong = listOf(
        "لازم", "محتاج", "محتاجه", "ضروري", "ما تنساش", "متنساش", "افتكر", "فكرني", "ما انساش", "مانساش", "عايز افتكر",
        "need to", "have to", "has to", "must", "remember to", "remind me", "don't forget", "dont forget", "do not forget", "gotta", "i'll", "i will",
    )
    private val document = listOf(
        "expiry", "expires", "expiration", "valid until", "due date", "return by", "deadline", "appointment", "booking", "check-in", "pay by",
        "تاريخ الانتهاء", "ينتهي", "صالح حتي", "موعد", "ميعاد", "اخر ميعاد", "تاريخ الاستحقاق",
    )
    private val weak = listOf("maybe", "should", "might", "probably", "dentist", "doctor", "ممكن", "يمكن", "دكتور", "الدكتور", "المفروض")
    private val continuation = Regex("^\\s*(?:و\\s*كمان|وكمان|و|and also|and|also)\\s+")
    private val splitter = Regex("[.!?؟\\n،,؛;]+|\\s+(?=وكمان\\s)")

    private fun has(n: String, words: List<String>) = words.any { w -> Regex("(?<![\\p{L}])" + Regex.escape(DateRules.normalize(w)) + "(?![\\p{L}])").containsMatchIn(n) }

    fun extract(text: String, now: LocalDateTime, source: SourceKind): List<LoopCandidate> {
        val out = mutableListOf<LoopCandidate>()
        var pos = 0; var prevHadCue = false
        val clauses = mutableListOf<IntRange>()
        splitter.findAll(text).forEach { m -> if (m.range.first > pos) clauses += pos until m.range.first; pos = m.range.last + 1 }
        if (pos < text.length) clauses += pos until text.length
        for (r in clauses) {
            val clause = text.substring(r.first, r.last + 1)
            if (clause.isBlank()) continue
            val n = DateRules.normalize(clause)
            val due = DateRules.find(clause, now)
            val isStrong = has(n, strong) || (prevHadCue && continuation.containsMatchIn(n))
            val isDoc = has(n, document)
            val isWeak = has(n, weak)
            val confidence = when {
                isStrong && due != null -> 0.9
                isDoc && due != null -> if (source == SourceKind.SCREENSHOT) 0.85 else 0.8
                isStrong -> 0.7
                (isWeak || isDoc) && due != null -> 0.6
                else -> 0.0
            }
            prevHadCue = isStrong
            if (confidence < 0.5) continue
            val lead = clause.length - clause.trimStart().length
            val trail = clause.length - clause.trimEnd().length
            out += LoopCandidate(actionText(clause, due), due?.let { it.copy(start = it.start + r.first, end = it.end + r.first) }, confidence, r.first + lead, r.last + 1 - trail)
        }
        return out
    }

    /** The clause minus its cue words, its date words and any leading conjunction. */
    private fun actionText(clause: String, due: Due?): String {
        var s = if (due != null) clause.removeRange(due.start, due.end) else clause
        s = continuation.replace(s, "")
        for (w in strong.sortedByDescending { it.length }) s = Regex("(?<![\\p{L}])" + Regex.escape(w) + "(?![\\p{L}])", RegexOption.IGNORE_CASE).replace(s, " ")
        return s.replace(Regex("\\s+"), " ").trim(' ', ':', '-', '،', ',')
    }
}
