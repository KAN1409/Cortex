package com.kareem.cortex.prime.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Drains claimable jobs. Each job runs under its stage's time limit, so one bad item never stalls the rest. */
class JobRunner(private val queue: JobQueue, private val handlers: List<StageHandler>) {

    suspend fun drain(maxJobs: Int = 50): Int {
        var processed = 0
        for (h in handlers) {
            while (processed < maxJobs) {
                val job = queue.claim(h.stage, h.timeLimitMs + LEASE_MARGIN_MS) ?: break
                try {
                    withTimeout(h.timeLimitMs) { h.run(job.evidenceId) }
                    queue.complete(job.id)
                    h.next?.let { queue.enqueue(it, job.evidenceId) }
                } catch (e: TimeoutCancellationException) {
                    queue.fail(job.id, "timeout")
                } catch (e: NotYetSupported) {
                    queue.park(job.id, e.message ?: "not_supported_yet")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    queue.fail(job.id, e.message ?: e::class.java.simpleName)
                }
                processed++
            }
        }
        return processed
    }

    private companion object { const val LEASE_MARGIN_MS = 30_000L }
}
