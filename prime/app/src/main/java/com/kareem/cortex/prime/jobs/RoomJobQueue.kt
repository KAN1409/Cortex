package com.kareem.cortex.prime.jobs

import com.kareem.cortex.prime.data.db.Job
import com.kareem.cortex.prime.data.db.JobDao
import kotlinx.coroutines.flow.Flow

class RoomJobQueue(private val dao: JobDao, private val clock: () -> Long) : JobQueue {
    override suspend fun enqueue(stage: String, evidenceId: Long): Unit = TODO()
    override suspend fun claim(stage: String, leaseMs: Long): Job? = TODO()
    override suspend fun complete(id: Long): Unit = TODO()
    override suspend fun fail(id: Long, error: String): Unit = TODO()
    override suspend fun park(id: Long, reason: String): Unit = TODO()
    override fun observeAll(): Flow<List<Job>> = dao.observeAll()
}
