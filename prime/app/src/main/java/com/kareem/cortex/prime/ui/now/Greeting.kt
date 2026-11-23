package com.kareem.cortex.prime.ui.now

/** Morning 05–11, afternoon 12–16, evening 17–04; the name is appended only when it is not blank. */
fun greeting(hour: Int, name: String?): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val n = name?.trim().orEmpty()
    return if (n.isEmpty()) part else "$part, $n"
}
