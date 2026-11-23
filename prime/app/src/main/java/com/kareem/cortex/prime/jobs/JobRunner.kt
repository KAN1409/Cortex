package com.kareem.cortex.prime.jobs

class JobRunner(private val queue: JobQueue, private val handlers: List<StageHandler>) {
    suspend fun drain(maxJobs: Int = 50): Int = TODO()
}
