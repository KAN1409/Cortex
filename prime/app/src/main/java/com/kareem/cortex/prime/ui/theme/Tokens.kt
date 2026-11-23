package com.kareem.cortex.prime.ui.theme

import kotlin.math.pow

/** Spec §7.1 colour tokens, as ARGB longs so they are testable on the JVM. */
object CortexColors {
    const val Bg = 0xFF000000
    const val TextPrimary = 0xFFF4F6FF
    const val TextSecondary = 0xFFC4CAE0
    const val Label = 0xFF8C95B5
    const val Blue = 0xFF4D7CFF
    const val Violet = 0xFF7A5CFF
    const val Gold = 0xFFE8C26A
    const val SheenA = 0xFF7FE7FF
    const val SheenB = 0xFF8FB0FF
    const val ActionTop = 0xFF5A86FF
    const val ActionBottom = 0xFF2F5BFF
}

/** WCAG 2 contrast ratio between two opaque ARGB colours. */
fun contrastRatio(fg: Long, bg: Long): Double {
    fun lum(c: Long): Double {
        fun ch(shift: Int): Double {
            val v = ((c shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }
    val a = lum(fg); val b = lum(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
