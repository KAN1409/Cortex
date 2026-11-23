package com.kareem.cortex.prime.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EvidenceKind { TEXT, IMAGE, PDF, AUDIO, LINK }

/** One captured thing. Immutable: corrections live in later tables, never here. */
@Entity(tableName = "evidence", indices = [Index(value = ["sha256"], unique = true), Index("capturedAt")])
data class Evidence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EvidenceKind,
    val originalPath: String?,
    val text: String?,
    val sha256: String,
    val mime: String?,
    val sizeBytes: Long,
    val sourceApp: String?,
    val capturedAt: Long,
)

/** What a perception stage read out of evidence (text, OCR, transcript). */
@Entity(tableName = "perception", indices = [Index("evidenceId")])
data class Perception(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val evidenceId: Long,
    val kind: String,
    val text: String,
    val language: String?,
    val confidence: Double,
    val engine: String,
    val createdAt: Long,
)

enum class JobState { WAITING, RUNNING, DONE, FAILED }

@Entity(
    tableName = "job",
    indices = [Index(value = ["stage", "evidenceId"], unique = true), Index(value = ["stage", "state", "nextRunAt"])],
)
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stage: String,
    val evidenceId: Long,
    val state: JobState,
    val attempts: Int,
    val leaseUntil: Long,
    val lastError: String?,
    val nextRunAt: Long,
    val updatedAt: Long,
)
