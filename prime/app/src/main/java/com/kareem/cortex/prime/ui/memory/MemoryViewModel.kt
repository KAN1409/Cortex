package com.kareem.cortex.prime.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.cortex.prime.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MemoryViewModel(private val graph: AppGraph) : ViewModel() {
    val rows: StateFlow<Map<LocalDate, List<MemoryRow>>> =
        memoryRows(graph.db.evidence().timeline(), graph.jobs.observeAll(), ZoneId.systemDefault(), graph.db.perception().observeTranscripts())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val loops: StateFlow<List<com.kareem.cortex.prime.data.db.LoopWithSource>> =
        graph.db.loops().observeActiveWithSource().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything readable, as search/cluster documents (text ideas, transcripts, screenshot text). */
    val docs: StateFlow<List<com.kareem.cortex.prime.connect.Doc>> =
        kotlinx.coroutines.flow.combine(graph.db.evidence().timeline(), graph.db.perception().observeAll()) { evs, ps ->
            val byEv = ps.groupBy { it.evidenceId }
            evs.mapNotNull { e -> (e.text ?: byEv[e.id]?.firstOrNull { it.text.isNotBlank() }?.text)?.let { com.kareem.cortex.prime.connect.Doc(e.id, it, e.capturedAt) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clusters: StateFlow<List<com.kareem.cortex.prime.connect.Cluster>> =
        docs.map { com.kareem.cortex.prime.connect.IdeaClusters.build(it) }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ask(q: String): List<com.kareem.cortex.prime.ui.ask.Cited> {
        val ds = docs.value; val byId = ds.associateBy { it.id }
        val kinds = rows.value.values.flatten().associate { it.id to it.kind }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
        return com.kareem.cortex.prime.connect.MemoryIndex(ds).search(q, 6).mapNotNull { h ->
            val d = byId[h.id] ?: return@mapNotNull null
            val src = when (kinds[h.id]) { com.kareem.cortex.prime.data.db.EvidenceKind.AUDIO -> "voice note"; com.kareem.cortex.prime.data.db.EvidenceKind.IMAGE -> "screenshot"; else -> "note" }
            com.kareem.cortex.prime.ui.ask.Cited(com.kareem.cortex.prime.ui.memory.rowTitle(d.text), d.text.take(240), java.time.Instant.ofEpochMilli(d.at).atZone(java.time.ZoneId.systemDefault()).format(fmt), src)
        }
    }

    private fun io(block: () -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { block() }
    fun done(id: Long) = io { graph.loopActions.done(id) }
    fun dismiss(id: Long) = io { graph.loopActions.dismiss(id) }
    fun confirm(id: Long) = io { graph.loopActions.confirm(id) }
    fun later(id: Long) = io { graph.loopActions.snooze(id, com.kareem.cortex.prime.understand.tomorrowAt(9)) }
    fun cycleArea(id: Long, current: com.kareem.cortex.prime.data.db.Area) = io {
        val all = com.kareem.cortex.prime.data.db.Area.entries
        graph.loopActions.setArea(id, all[(all.indexOf(current) + 1) % all.size])
    }

    fun retry(evidenceId: Long) = viewModelScope.launch {
        graph.jobs.retry("perceive", evidenceId); graph.kickJobs()
    }

    fun saveIdea(text: String) = viewModelScope.launch {
        graph.evidence.captureText(text, sourceApp = "Cortex"); graph.kickJobs()
    }
}
