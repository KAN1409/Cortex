package com.kareem.cortex.prime.ui.memory

import com.kareem.cortex.prime.data.db.Job
import com.kareem.cortex.prime.data.db.JobState
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingStateTest {
    private fun job(s: JobState, err: String? = null) =
        Job(stage = "perceive", evidenceId = 1, state = s, attempts = 1, leaseUntil = 0, lastError = err, nextRunAt = 0, updatedAt = 0)

    @Test fun parkedJobIsLaterNotFailed() =
        assertEquals(ProcessingLabel.LATER, processingLabel(job(JobState.WAITING, "not_supported_yet")))

    @Test fun failedShowsCouldntRead() =
        assertEquals("Couldn't read", processingLabel(job(JobState.FAILED, "timeout")).text)

    @Test fun statesMapHonestly() {
        assertEquals(ProcessingLabel.WAITING, processingLabel(job(JobState.WAITING)))
        assertEquals(ProcessingLabel.READING, processingLabel(job(JobState.RUNNING)))
        assertEquals(ProcessingLabel.DONE, processingLabel(job(JobState.DONE)))
        assertEquals(ProcessingLabel.DONE, processingLabel(null))
    }

    @Test fun titleIsFirstLineClipped() {
        assertEquals("Call mum", rowTitle("Call mum\nabout Friday"))
        assertEquals(60, rowTitle("x".repeat(200)).length)
    }
}
