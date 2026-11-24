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

    private var pendingPass: CharArray? = null
    private var backupMessage by mutableStateOf<String?>(null)
    private val createBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pendingPass ?: return@registerForActivityResult; pendingPass = null
        if (uri != null) lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            backupMessage = runCatching { com.kareem.cortex.prime.backup.BackupManager(this@MainActivity, graph.db).export(uri, p); "Backup saved." }.getOrElse { "Backup failed: ${it.message}" }
            p.fill(' ')
        }
    }
    private val openBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val p = pendingPass ?: return@registerForActivityResult; pendingPass = null
        if (uri != null) lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = runCatching { com.kareem.cortex.prime.backup.BackupManager(this@MainActivity, graph.db).restore(uri, p) }
            p.fill(' ')
            if (r.isSuccess) { // restart into the restored data
                startActivity(packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Runtime.getRuntime().exit(0)
            } else backupMessage = r.exceptionOrNull()?.message ?: "Restore failed"
        }
    }

    private var tts: android.speech.tts.TextToSpeech? = null
    private fun speakBrief(loops: List<com.kareem.cortex.prime.data.db.LoopWithSource>) {
        val text = if (loops.isEmpty()) "Nothing you're likely to forget right now." else
            "You have ${loops.size} thing${if (loops.size == 1) "" else "s"} you might forget. " + loops.take(5).joinToString(". ") { it.loop.action + ", " + com.kareem.cortex.prime.ui.now.dueLabel(it.loop.dueAt, it.loop.duePrecision) }
        val t = tts
        if (t != null) t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "brief")
        else tts = android.speech.tts.TextToSpeech(this) { ok -> if (ok == android.speech.tts.TextToSpeech.SUCCESS) tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "brief") }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    private fun modelLabel(m: com.kareem.cortex.prime.voice.ModelStatus) = when (m) {
        com.kareem.cortex.prime.voice.ModelStatus.Ready -> "ready"
        com.kareem.cortex.prime.voice.ModelStatus.Missing -> "not downloaded"
        is com.kareem.cortex.prime.voice.ModelStatus.Downloading -> "downloading ${(m.progress * 100).toInt()}%"
        is com.kareem.cortex.prime.voice.ModelStatus.Failed -> m.reason
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
            var settingsOpen by remember { mutableStateOf(false) }
            var motionOverride by remember { mutableStateOf(g.prefs.reduceMotion) }
            var name by remember { mutableStateOf(g.prefs.displayName) }
            val shotsAllowed = screenshotsAllowed
            val motion = systemAnimationsEnabled(this) && !motionOverride
            CompositionLocalProvider(LocalMotionEnabled provides motion) {
                Box(Modifier.fillMaxSize().background(Cx.Bg)) {
                    HorizonBackground()
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        when (tab) {
                            Tab.NOW -> NowScreen(name, rows.values.flatten().take(3), loops = loops, pending = pending, onDone = vm::done, onLater = vm::later, onConfirm = vm::confirm, onDismiss = vm::dismiss, onArea = vm::cycleArea, onSettings = { settingsOpen = true }, onBrief = { speakBrief(loops) })
                            Tab.MEMORY -> MemoryScreen(rows, onRetry = { vm.retry(it) }, screenshotsAllowed = shotsAllowed, onAllowScreenshots = { askPhotos() })
                            Tab.IDEAS -> { val clusters by vm.clusters.collectAsStateWithLifecycle(); val docs by vm.docs.collectAsStateWithLifecycle(); val byId = docs.associateBy { it.id }
                                com.kareem.cortex.prime.ui.ideas.IdeasScreen(clusters) { id -> byId[id]?.text?.let { t -> com.kareem.cortex.prime.ui.memory.rowTitle(t) } ?: "" } }
                            Tab.ASK -> com.kareem.cortex.prime.ui.ask.AskScreen(vm::ask)
                        }
                    }
                    GlassNavBar(tab, { tab = it }, { ideaOpen = true }, onRecord = { startActivity(android.content.Intent(this@MainActivity, com.kareem.cortex.prime.voice.RecordActivity::class.java)) }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp))
                    if (ideaOpen) IdeaSheet(onSave = { vm.saveIdea(it); ideaOpen = false }, onDismiss = { ideaOpen = false })
                    if (settingsOpen) Box(Modifier.fillMaxSize().background(Cx.Bg).statusBarsPadding()) {
                        val model by g.models.status.collectAsStateWithLifecycle()
                        com.kareem.cortex.prime.ui.settings.SettingsScreen(
                            com.kareem.cortex.prime.ui.settings.SettingsState(name.orEmpty(), motionOverride, modelLabel(model), shotsAllowed, g.reminders.exactAllowed(), backupMessage),
                            onClose = { settingsOpen = false }, onName = { g.prefs.displayName = it; name = it },
                            onReduceMotion = { g.prefs.reduceMotion = it; motionOverride = it }, onDownloadModel = { g.models.start() },
                            onScreenshots = { askPhotos() },
                            onBackup = { pendingPass = it; createBackup.launch("cortex-${java.time.LocalDate.now()}.cortexbackup") },
                            onRestore = { pendingPass = it; openBackup.launch(arrayOf("*/*")) },
                        )
                    }
                    if (name == null) FirstRunNameSheet { g.prefs.displayName = it; name = it }
                }
            }
        }
    }
}
