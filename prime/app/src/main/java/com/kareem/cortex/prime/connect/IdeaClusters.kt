package com.kareem.cortex.prime.connect

data class Cluster(val title: String, val memberIds: List<Long>, val lastAt: Long)

/** Greedy single-pass clustering of related captures; a cluster is shown once it has 3+ members (spec §6.6). */
object IdeaClusters {
    const val JOIN = 0.28

    fun build(docs: List<Doc>, minSize: Int = 3): List<Cluster> {
        data class Acc(val ids: MutableList<Long>, val centroid: FloatArray, val words: MutableMap<String, Int>, var lastAt: Long)
        val accs = mutableListOf<Acc>()
        for (d in docs.sortedBy { it.at }) {
            val v = TextVector.vector(d.text); val words = TextVector.tokens(d.text).toSet()
            val best = accs.maxByOrNull { TextVector.cosine(v, it.centroid) }
            val sim = best?.let { TextVector.cosine(v, it.centroid) } ?: 0.0
            if (best != null && sim >= JOIN && words.any { it in best.words }) {
                val n = best.ids.size
                for (i in v.indices) best.centroid[i] = (best.centroid[i] * n + v[i]) / (n + 1)
                best.ids += d.id; words.forEach { best.words[it] = (best.words[it] ?: 0) + 1 }; best.lastAt = maxOf(best.lastAt, d.at)
            } else accs += Acc(mutableListOf(d.id), v.copyOf(), words.associateWith { 1 }.toMutableMap(), d.at)
        }
        return accs.filter { it.ids.size >= minSize }.map { a ->
            val title = a.words.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(3).joinToString(" · ") { it.key }
            Cluster(title.ifBlank { "Related notes" }.replaceFirstChar { it.uppercase() }, a.ids.toList(), a.lastAt)
        }.sortedByDescending { it.lastAt }
    }
}
