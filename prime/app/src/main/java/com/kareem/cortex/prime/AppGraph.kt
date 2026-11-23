package com.kareem.cortex.prime

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kareem.cortex.prime.data.db.CortexDatabase
import com.kareem.cortex.prime.data.evidence.EvidenceRepository
import com.kareem.cortex.prime.data.evidence.OriginalStore
import com.kareem.cortex.prime.jobs.JobQueue
import com.kareem.cortex.prime.jobs.JobRunner
import com.kareem.cortex.prime.jobs.JobWorker
import com.kareem.cortex.prime.jobs.RoomJobQueue
import com.kareem.cortex.prime.jobs.TextPerceiveHandler
import com.kareem.cortex.prime.ui.settings.Prefs
import com.kareem.cortex.prime.voice.ModelManager
import com.kareem.cortex.prime.voice.PerceiveRouter
import com.kareem.cortex.prime.voice.TranscribeHandler
import com.kareem.cortex.prime.voice.WhisperEngine

/** Hand wiring for the whole app (no DI framework). */
class AppGraph(private val context: Context) {
    val db: CortexDatabase = CortexDatabase.build(context)
    val jobs: JobQueue = RoomJobQueue(db.jobs())
    val evidence = EvidenceRepository(db.evidence(), OriginalStore(context.filesDir), jobs)
    val models = ModelManager(context)
    private val whisper by lazy { WhisperEngine(models.file) }
    val runner = JobRunner(jobs, listOf(PerceiveRouter(
        TextPerceiveHandler(db.evidence(), db.perception()),
        TranscribeHandler(db.evidence(), db.perception(), modelReady = { models.isReady() }, engine = { whisper }),
    )))
    val prefs = Prefs(context)

    /** Ask WorkManager to drain the queue now (idempotent: appends to the running chain). */
    fun kickJobs() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            JobWorker.UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<JobWorker>().build(),
        )
    }
}
