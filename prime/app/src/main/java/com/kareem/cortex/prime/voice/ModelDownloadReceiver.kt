package com.kareem.cortex.prime.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kareem.cortex.prime.graph

/** A finished download wakes the job worker, which verifies the model and unparks waiting notes. */
class ModelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = context.graph.kickJobs()
}
