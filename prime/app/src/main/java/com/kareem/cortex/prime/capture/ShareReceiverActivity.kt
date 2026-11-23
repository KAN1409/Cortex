package com.kareem.cortex.prime.capture

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.kareem.cortex.prime.data.evidence.CaptureResult
import com.kareem.cortex.prime.graph
import kotlinx.coroutines.launch

/** Invisible share target: saves each shared item, says what happened, and closes. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = ShareParser.parse(intent) { contentResolver.getType(it) }
        val source = referrer?.host
        lifecycleScope.launch {
            val message = if (items.isEmpty()) "Cortex can't save this kind of item yet" else {
                val results = items.map { item ->
                    runCatching {
                        when (item) {
                            is ShareItem.Text -> graph.evidence.captureText(item.value, source)
                            is ShareItem.Stream -> graph.evidence.captureStream({ contentResolver.openInputStream(item.uri) ?: error("unreadable") }, item.mime, source)
                        }
                    }.getOrElse { CaptureResult.Rejected("Cortex couldn't read that item") }
                }
                graph.kickJobs()
                results.filterIsInstance<CaptureResult.Rejected>().firstOrNull()?.message
                    ?: if (results.all { it is CaptureResult.Saved && it.duplicate }) "Already in Cortex" else "Saved to Cortex"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
