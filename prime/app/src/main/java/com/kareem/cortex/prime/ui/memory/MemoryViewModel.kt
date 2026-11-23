package com.kareem.cortex.prime.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.cortex.prime.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MemoryViewModel(private val graph: AppGraph) : ViewModel() {
    val rows: StateFlow<Map<LocalDate, List<MemoryRow>>> =
        memoryRows(graph.db.evidence().timeline(), graph.jobs.observeAll(), ZoneId.systemDefault(), graph.db.perception().observeTranscripts())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val loops: StateFlow<List<com.kareem.cortex.prime.data.db.LoopWithSource>> =
        graph.db.loops().observeActiveWithSource().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
