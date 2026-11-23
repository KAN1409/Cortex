package com.kareem.cortex.prime.jobs

import com.kareem.cortex.prime.data.db.Job
import com.kareem.cortex.prime.data.db.JobDao
import com.kareem.cortex.prime.data.db.JobState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

const val MAX_ATTEMPTS = 3
const val BASE_BACKOFF_MS = 30_000L

class RoomJobQueue(
    private val dao: JobDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : JobQueue {

    override suspend fun enqueue(stage: String, evidenceId: Long): Unit = io {
        val now = clock()
        dao.insert(Job(stage = stage, evidenceId = evidenceId, state = JobState.WAITING, attempts = 0, leaseUntil = 0, lastError = null, nextRunAt = now, updatedAt = now))
    }

    override suspend fun claim(stage: String, leaseMs: Long): Job? = io {
        // Optimistic: re-check the row has not changed between select and update, so two
        // workers can never hold the same lease.
        repeat(5) {
            val now = clock()
            val candidate = dao.nextClaimable(stage, now) ?: return@io null
            if (dao.takeLease(candidate.id, candidate.state, candidate.updatedAt, now + leaseMs, now) == 1) return@io dao.byId(candidate.id)
        }
        null
    }

    override suspend fun complete(id: Long): Unit = io {
        val j = dao.byId(id)
        dao.update(j.copy(state = JobState.DONE, lastError = null, leaseUntil = 0, updatedAt = clock()))
    }

    override suspend fun fail(id: Long, error: String): Unit = io {
        val j = dao.byId(id); val now = clock()
        dao.update(
            if (j.attempts >= MAX_ATTEMPTS) j.copy(state = JobState.FAILED, lastError = error, leaseUntil = 0, updatedAt = now)
            else j.copy(state = JobState.WAITING, lastError = error, leaseUntil = 0, nextRunAt = now + BASE_BACKOFF_MS * (1L shl (j.attempts - 1).coerceAtLeast(0)), updatedAt = now),
        )
    }

    override suspend fun park(id: Long, reason: String): Unit = io {
        val j = dao.byId(id)
        // Parking is not a try: give back the attempt the claim took.
        dao.update(j.copy(state = JobState.WAITING, attempts = (j.attempts - 1).coerceAtLeast(0), lastError = reason, leaseUntil = 0, nextRunAt = Long.MAX_VALUE, updatedAt = clock()))
    }

    override suspend fun retry(stage: String, evidenceId: Long): Unit = io {
        val j = dao.forEvidence(evidenceId, stage) ?: return@io
        dao.update(j.copy(state = JobState.WAITING, attempts = 0, lastError = null, leaseUntil = 0, nextRunAt = clock(), updatedAt = clock()))
    }

    override fun observeAll(): Flow<List<Job>> = dao.observeAll()

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
