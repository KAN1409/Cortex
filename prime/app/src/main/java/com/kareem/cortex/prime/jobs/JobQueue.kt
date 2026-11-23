package com.kareem.cortex.prime.jobs

import com.kareem.cortex.prime.data.db.Job
import kotlinx.coroutines.flow.Flow

/** The processing queue. Interface so capture code can be tested with a fake. */
interface JobQueue {
    suspend fun enqueue(stage: String, evidenceId: Long)
    suspend fun claim(stage: String, leaseMs: Long): Job?
    suspend fun complete(id: Long)
    suspend fun fail(id: Long, error: String)
    /** Park a job whose evidence kind no stage can read yet; does not count as an attempt. */
    suspend fun park(id: Long, reason: String)
    /** Wake every job parked with [reason] (e.g. once the voice model is downloaded). */
    suspend fun unpark(reason: String)
    /** User-requested retry of a failed job: fresh attempts, due now. */
    suspend fun retry(stage: String, evidenceId: Long)
    fun observeAll(): Flow<List<Job>>
}
