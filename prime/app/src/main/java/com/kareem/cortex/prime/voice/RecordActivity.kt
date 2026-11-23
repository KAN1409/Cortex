package com.kareem.cortex.prime.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kareem.cortex.prime.graph
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.systemAnimationsEnabled

/** Full-screen voice capture, reachable from the nav button and the Quick Settings tile. */
class RecordActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (!ok) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO)
        val motion = systemAnimationsEnabled(this) && !graph.prefs.reduceMotion
        setContent {
            CompositionLocalProvider(LocalMotionEnabled provides motion) {
                if (granted) RecordScreen(graph, onClose = { finish() })
            }
        }
    }
}
