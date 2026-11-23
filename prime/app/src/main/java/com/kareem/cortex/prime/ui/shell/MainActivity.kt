package com.kareem.cortex.prime.ui.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kareem.cortex.prime.graph
import com.kareem.cortex.prime.ui.capture.IdeaSheet
import com.kareem.cortex.prime.ui.memory.MemoryScreen
import com.kareem.cortex.prime.ui.memory.MemoryViewModel
import com.kareem.cortex.prime.ui.now.NowScreen
import com.kareem.cortex.prime.ui.settings.FirstRunNameSheet
import com.kareem.cortex.prime.ui.theme.Cx
import com.kareem.cortex.prime.ui.theme.HorizonBackground
import com.kareem.cortex.prime.ui.theme.LocalMotionEnabled
import com.kareem.cortex.prime.ui.theme.systemAnimationsEnabled

class MainActivity : ComponentActivity() {
    private var screenshotsAllowed by mutableStateOf(false)
    private val photos = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { refreshScreenshots() }

    private fun askPhotos() = photos.launch(if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun refreshScreenshots() {
        screenshotsAllowed = graph.canReadScreenshots()
        if (screenshotsAllowed) lifecycleScope.launch { if (graph.scanner.scan() > 0) graph.kickJobs() }
    }

    private val notifications = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onResume() {
        super.onResume(); refreshScreenshots()
        com.kareem.cortex.prime.understand.AndroidReminderScheduler.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED && graph.prefs.displayName != null)
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val g = graph
        setContent {
            val vm: MemoryViewModel = viewModel { MemoryViewModel(g) }
            val rows by vm.rows.collectAsStateWithLifecycle()
            val loops by vm.loops.collectAsStateWithLifecycle()
            val pending = rows.values.sumOf { l -> l.count { it.label in setOf(com.kareem.cortex.prime.ui.memory.ProcessingLabel.WAITING, com.kareem.cortex.prime.ui.memory.ProcessingLabel.READING, com.kareem.cortex.prime.ui.memory.ProcessingLabel.MODEL) } }
            var tab by remember { mutableStateOf(Tab.NOW) }
            var ideaOpen by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf(g.prefs.displayName) }
            val shotsAllowed = screenshotsAllowed
            val motion = systemAnimationsEnabled(this) && !g.prefs.reduceMotion
            CompositionLocalProvider(LocalMotionEnabled provides motion) {
                Box(Modifier.fillMaxSize().background(Cx.Bg)) {
                    HorizonBackground()
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        when (tab) {
                            Tab.NOW -> NowScreen(name, rows.values.flatten().take(3), loops = loops, pending = pending, onDone = vm::done, onLater = vm::later, onConfirm = vm::confirm, onDismiss = vm::dismiss, onArea = vm::cycleArea)
                            Tab.MEMORY -> MemoryScreen(rows, onRetry = { vm.retry(it) }, screenshotsAllowed = shotsAllowed, onAllowScreenshots = { askPhotos() })
                        }
                    }
                    GlassNavBar(tab, { tab = it }, { ideaOpen = true }, onRecord = { startActivity(android.content.Intent(this@MainActivity, com.kareem.cortex.prime.voice.RecordActivity::class.java)) }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp))
                    if (ideaOpen) IdeaSheet(onSave = { vm.saveIdea(it); ideaOpen = false }, onDismiss = { ideaOpen = false })
                    if (name == null) FirstRunNameSheet { g.prefs.displayName = it; name = it }
                }
            }
        }
    }
}
