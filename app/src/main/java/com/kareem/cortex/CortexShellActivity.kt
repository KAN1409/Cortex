package com.kareem.cortex

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kareem.cortex.ui.CortexShell
import com.kareem.cortex.ui.CortexShellViewModel
import com.kareem.cortex.ui.CortexTheme

/** Canonical v146 product shell. Legacy Activities are detail/workflow implementations, not tabs. */
class CortexShellActivity : ComponentActivity() {
    private lateinit var shellViewModel: CortexShellViewModel
    private var selectedDestination by mutableStateOf(CortexDestinationRegistry.NOW)
    private var dockOpen by mutableStateOf(false)
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        CortexUi.applyWindow(this)
        CortexDestinationRegistry.validateOrThrow()
        CortexActionRegistry.validateOrThrow()
        shellViewModel = ViewModelProvider(this)[CortexShellViewModel::class.java]
        consumeIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    dockOpen -> dockOpen = false
                    selectedDestination != CortexDestinationRegistry.NOW -> navigateTo(CortexDestinationRegistry.NOW)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        setContent {
            val uiState by shellViewModel.uiState.collectAsStateWithLifecycle()
            val dockState by shellViewModel.dockState.collectAsStateWithLifecycle()
            CortexTheme {
                CortexShell(
                    selectedDestination = selectedDestination,
                    uiState = uiState,
                    dockState = dockState,
                    dockOpen = dockOpen,
                    onDestinationSelected = ::navigateTo,
                    onOpenDock = ::openDock,
                    onDismissDock = { dockOpen = false },
                    onAsk = { prompt, mode ->
                        CortexActionRegistry.require("cortex.ask")
                        shellViewModel.ask(prompt, mode)
                    },
                    onAction = ::performCanonicalAction,
                    onRefresh = shellViewModel::refresh
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) shellViewModel.refresh() else resumedOnce = true
    }

    fun navigateTo(destinationId: String) {
        val destination = try { CortexDestinationRegistry.require(destinationId) } catch (_: Throwable) { return }
        if (destination.category != CortexDestinationRegistry.Category.PRIMARY) return
        selectedDestination = destination.destinationId
    }

    fun openDock() {
        dockOpen = true
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        val destinationId = intent.getStringExtra(CortexNavigation.EXTRA_DESTINATION_ID)
        if (!destinationId.isNullOrBlank()) navigateTo(destinationId)
        if (intent.getBooleanExtra(CortexNavigation.EXTRA_OPEN_DOCK, false)) dockOpen = true
        intent.removeExtra(CortexNavigation.EXTRA_DESTINATION_ID)
        intent.removeExtra(CortexNavigation.EXTRA_OPEN_DOCK)
    }

    private fun performCanonicalAction(actionId: String) {
        try {
            CortexActionRegistry.require(actionId)
            when (actionId) {
                "settings.open" -> startActivity(Intent(this, SettingsActivity::class.java))
                "capture.voice" -> openCapture("voice")
                "capture.text" -> openCapture("text")
                "capture.image" -> openCapture("photo")
                "capture.file" -> openCapture("file")
                "capture.screen.setup", "permissions.accessibility.open" -> openAccessibilitySettings()
                "capture.library.voice.open" -> startActivity(Intent(this, VoiceLibraryActivity::class.java))
                "capture.library.visual.open" -> startActivity(Intent(this, VisualMemoryActivity::class.java))
                "capture.library.files.open" -> openWorkBrowse(WorkVaultBrowseActivity.MODE_FILES)
                "work.archive.open" -> startActivity(Intent(this, WorkVaultActivity::class.java))
                "work.ask" -> startActivity(Intent(this, WorkVaultAskActivity::class.java))
                "work.projects.open" -> openWorkBrowse(WorkVaultBrowseActivity.MODE_PROJECTS)
                "work.followup.open" -> startActivity(Intent(this, WorkFollowUpActivity::class.java))
                "work.prices.open" -> openWorkBrowse(WorkVaultBrowseActivity.MODE_PRICES)
                "work.files.open" -> openWorkBrowse(WorkVaultBrowseActivity.MODE_FILES)
                "work.build_chatgpt" -> startActivity(Intent(this, WorkChatGptBuildActivity::class.java))
                "memory.knowledge.open" -> startActivity(Intent(this, KnowledgeExplorerActivity::class.java))
                "memory.refresh" -> shellViewModel.refresh()
                else -> {
                    Log.w("CortexShell", "No shell executor for canonical action $actionId")
                    Toast.makeText(this, "This action is not available from this surface.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (t: Throwable) {
            Log.e("CortexShell", "Canonical action failed: $actionId", t)
            Toast.makeText(this, "Cortex kept this action safe instead of guessing.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCapture(mode: String) {
        startActivity(Intent(this, ProposalCaptureActivity::class.java).putExtra("mode", mode))
    }

    private fun openWorkBrowse(mode: String) {
        startActivity(
            Intent(this, WorkVaultBrowseActivity::class.java)
                .putExtra(WorkVaultBrowseActivity.EXTRA_MODE, mode)
        )
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (t: Throwable) {
            Log.e("CortexShell", "Accessibility settings unavailable", t)
            Toast.makeText(this, "Android accessibility settings could not be opened.", Toast.LENGTH_LONG).show()
        }
    }
}
