package com.kareem.cortex.prime.vision

/** Spec §6.3 OCR rules: shrink only, Arabic pass only when the Latin pass suggests it, merge without duplicates. */
object OcrPolicy {
    const val MAX_SIDE = 2000
    const val MIN_LATIN_SIGNAL = 12

    fun isArabic(c: Char) = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'

    fun shouldTryArabic(latinText: String): Boolean =
        latinText.count(::isArabic) >= 2 || latinText.filter { it.isLetterOrDigit() }.length < MIN_LATIN_SIGNAL

    /** Factor ≤ 1 so the longest side is at most MAX_SIDE; never enlarges (enlarging is what stalled v159). */
    fun scaleFor(width: Int, height: Int): Float {
        val side = maxOf(width, height)
        return if (side <= MAX_SIDE) 1f else MAX_SIDE.toFloat() / side
    }

    fun merge(latin: String, arabic: String): String {
        val seen = LinkedHashSet<String>()
        return (latin.lines() + arabic.lines()).map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }.joinToString("\n")
    }
}

fun isScreenshot(relativePath: String?, displayName: String?): Boolean =
    (relativePath ?: "").contains("screenshot", ignoreCase = true) || (displayName ?: "").startsWith("Screenshot", ignoreCase = true)
