package com.kareem.cortex.prime.ui.memory

import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.Job
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

enum class ProcessingLabel(val text: String) {
    WAITING("Waiting"), READING("Reading"), DONE("Done"), FAILED("Couldn't read"),
    LATER("Saved · reading comes in a later update"),
}

data class MemoryRow(val id: Long, val kind: EvidenceKind, val title: String, val preview: String?, val capturedAt: Long, val label: ProcessingLabel, val error: String?)

fun processingLabel(job: Job?): ProcessingLabel = TODO()
fun rowTitle(text: String): String = TODO()
fun memoryRows(evidence: Flow<List<Evidence>>, jobs: Flow<List<Job>>, zone: ZoneId): Flow<Map<LocalDate, List<MemoryRow>>> = TODO()
