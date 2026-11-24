package com.kareem.cortex.prime.connect

import com.kareem.cortex.prime.understand.DateRules
import kotlin.math.sqrt

data class Doc(val id: Long, val text: String, val at: Long)
data class Hit(val id: Long, val score: Double)

/**
 * On-phone similarity for search and idea clusters (free, no model download): normalised words
 * (Arabic letter variants folded, stop-words dropped) plus character trigrams, hashed into a vector.
 */
object TextVector {
    private val stop = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "it", "at", "be", "my", "me", "i", "you", "this", "that", "with", "about",
        "في", "من", "علي", "على", "عن", "الي", "إلى", "ان", "انا", "ده", "دي", "اللي", "مع", "و", "يا", "بس", "كمان", "عشان", "لازم",
    )
    const val DIM = 4096

    fun tokens(text: String): List<String> = DateRules.normalize(text)
        .split(Regex("[^\\p{L}\\p{N}]+")).map { it.removePrefix("ال").takeIf { t -> t.length >= 2 } ?: it }
        .filter { it.length >= 3 && it !in stop }
        .map { if (it.length > 4 && it.endsWith("ه")) it.dropLast(1) else it }

    fun vector(text: String): FloatArray {
        val v = FloatArray(DIM)
        for (t in tokens(text)) {
            v[Math.floorMod(t.hashCode(), DIM)] += 1.0f
            if (t.length >= 4) for (i in 0..t.length - 3) v[Math.floorMod(("#" + t.substring(i, i + 3)).hashCode(), DIM)] += 0.35f
        }
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        if (n > 0) for (i in v.indices) v[i] /= n
        return v
    }

    fun cosine(a: FloatArray, b: FloatArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }
}

class MemoryIndex(private val docs: List<Doc>) {
    private val vectors = docs.associate { it.id to TextVector.vector(it.text) }
    private val tokenSets = docs.associate { it.id to TextVector.tokens(it.text).toSet() }

    /** Ranked by similarity, with a bonus for exact word overlap; nothing below a floor. */
    fun search(query: String, limit: Int = 20): List<Hit> {
        val q = TextVector.vector(query); val qt = TextVector.tokens(query).toSet()
        if (qt.isEmpty()) return emptyList()
        return docs.map { d ->
            val overlap = qt.count { t -> tokenSets.getValue(d.id).any { it == t || (t.length >= 4 && (it.startsWith(t) || t.startsWith(it))) } }
            Hit(d.id, TextVector.cosine(q, vectors.getValue(d.id)) + 0.25 * overlap / qt.size)
        }.filter { it.score >= 0.2 }.sortedByDescending { it.score }.take(limit)
    }
}
