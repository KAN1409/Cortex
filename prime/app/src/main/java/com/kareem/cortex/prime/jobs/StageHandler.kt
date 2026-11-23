package com.kareem.cortex.prime.jobs

interface StageHandler {
    val stage: String
    val timeLimitMs: Long
    suspend fun run(evidenceId: Long)
    /** Stage to enqueue for the same evidence once this one is done (null = end of the pipeline). */
    val next: String? get() = null
}

/** Thrown by a handler for evidence it cannot read yet; the runner parks the job. */
class NotYetSupported(message: String = "not_supported_yet") : Exception(message)
