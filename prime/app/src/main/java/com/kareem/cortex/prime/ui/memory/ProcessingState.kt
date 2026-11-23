package com.kareem.cortex.prime.ui.memory

import com.kareem.cortex.prime.data.db.Evidence
import com.kareem.cortex.prime.data.db.EvidenceKind
import com.kareem.cortex.prime.data.db.Job
import com.kareem.cortex.prime.data.db.JobState
import com.kareem.cortex.prime.data.evidence.STAGE_PERCEIVE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ProcessingLabel(val text: String) {
    WAITING("Waiting"), READING("Reading"), DONE("Done"), FAILED("Couldn't read"),
    LATER("Saved · reading comes in a later update"),
    MODEL("Waiting for the voice model"),
}

data class MemoryRow(val id: Long, val kind: EvidenceKind, val title: String, val preview: String?, val capturedAt: Long, val label: ProcessingLabel, val error: String?)

/** Only states the job engine actually recorded; never a guess. */
fun processingLabel(job: Job?): ProcessingLabel = when {
    job == null -> ProcessingLabel.DONE
    job.state == JobState.WAITING && job.lastError == "not_supported_yet" -> ProcessingLabel.LATER
    job.state == JobState.WAITING && job.lastError == "model_missing" -> ProcessingLabel.MODEL
    job.state == JobState.WAITING -> ProcessingLabel.WAITING
    job.state == JobState.RUNNING -> ProcessingLabel.READING
    job.state == JobState.DONE -> ProcessingLabel.DONE
    else -> ProcessingLabel.FAILED
}

fun rowTitle(text: String): String = text.trim().lineSequence().first().trim().take(60)

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

fun memoryRows(evidence: Flow<List<Evidence>>, jobs: Flow<List<Job>>, zone: ZoneId, transcripts: Flow<List<com.kareem.cortex.prime.data.db.Perception>> = kotlinx.coroutines.flow.flowOf(emptyList())): Flow<Map<LocalDate, List<MemoryRow>>> =
    combine(evidence, jobs, transcripts) { evs, js, ts ->
        val heard = ts.associate { it.evidenceId to it.text }
        val byEvidence = js.filter { it.stage == STAGE_PERCEIVE }.associateBy { it.evidenceId }
        evs.map { e ->
            val job = byEvidence[e.id]
            val at = Instant.ofEpochMilli(e.capturedAt).atZone(zone)
            val said = heard[e.id]
            val title = e.text?.let(::rowTitle) ?: said?.takeIf { it.isNotBlank() }?.let(::rowTitle) ?: if (said != null) (if (e.kind == EvidenceKind.IMAGE) "Screenshot · no text found" else "Voice note · no speech found") else "${e.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${at.format(timeFmt)}"
            val preview = e.text?.trim()?.lineSequence()?.drop(1)?.joinToString(" ")?.trim()?.takeIf { it.isNotEmpty() }
            MemoryRow(e.id, e.kind, title, preview, e.capturedAt, processingLabel(job), job?.lastError?.takeIf { job.state == JobState.FAILED })
        }.groupBy { Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() }
    }
