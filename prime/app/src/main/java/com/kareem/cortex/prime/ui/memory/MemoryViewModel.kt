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
        memoryRows(graph.db.evidence().timeline(), graph.jobs.observeAll(), ZoneId.systemDefault())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun retry(evidenceId: Long) = viewModelScope.launch {
        graph.jobs.retry("perceive", evidenceId); graph.kickJobs()
    }

    fun saveIdea(text: String) = viewModelScope.launch {
        graph.evidence.captureText(text, sourceApp = "Cortex"); graph.kickJobs()
    }
}
