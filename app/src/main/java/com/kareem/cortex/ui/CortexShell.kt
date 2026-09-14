package com.kareem.cortex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kareem.cortex.CortexDestinationRegistry
import com.kareem.cortex.KnowledgeItem
import com.kareem.cortex.ui.CortexShellViewModel.AttentionItem
import com.kareem.cortex.ui.CortexShellViewModel.CaptureSummary
import com.kareem.cortex.ui.CortexShellViewModel.DockState
import com.kareem.cortex.ui.CortexShellViewModel.DockStatus
import com.kareem.cortex.ui.CortexShellViewModel.LoadState
import com.kareem.cortex.ui.CortexShellViewModel.MemorySummary
import com.kareem.cortex.ui.CortexShellViewModel.NowSummary
import com.kareem.cortex.ui.CortexShellViewModel.ShellUiState
import com.kareem.cortex.ui.CortexShellViewModel.WorkSummary

private data class NavSpec(val id: String, val label: String, val mark: String)
private val primaryNav = listOf(
    NavSpec(CortexDestinationRegistry.NOW, "Now", "●"),
    NavSpec(CortexDestinationRegistry.WORK, "Work", "▰"),
    NavSpec(CortexDestinationRegistry.MEMORY, "Memory", "◌"),
    NavSpec(CortexDestinationRegistry.CAPTURE, "Capture", "+")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexShell(
    selectedDestination: String,
    uiState: ShellUiState,
    dockState: DockState,
    dockOpen: Boolean,
    onDestinationSelected: (String) -> Unit,
    onOpenDock: () -> Unit,
    onDismissDock: () -> Unit,
    onAsk: (String, String) -> Unit,
    onAction: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val selectedLabel = primaryNav.firstOrNull { it.id == selectedDestination }?.label ?: "Now"
    Scaffold(
        containerColor = CortexColors.Void,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CORTEX · PERSONAL INTELLIGENCE", style = MaterialTheme.typography.labelMedium, color = CortexColors.Intelligence)
                        Text(selectedLabel, style = MaterialTheme.typography.headlineLarge, color = CortexColors.TextPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction("settings.open") },
                        modifier = Modifier.heightIn(min = CortexSpace.Touch)
                    ) { Text("Settings", color = CortexColors.TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CortexColors.Void)
            )
        },
        bottomBar = {
            ShellBottomBar(
                selectedDestination = selectedDestination,
                dockState = dockState,
                onDestinationSelected = onDestinationSelected,
                onOpenDock = onOpenDock,
                onQuickVoice = { onAction("capture.voice") }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedDestination) {
                CortexDestinationRegistry.WORK -> WorkPane(uiState.work, onAction, onRefresh)
                CortexDestinationRegistry.MEMORY -> MemoryPane(uiState.memory, onAction, onRefresh)
                CortexDestinationRegistry.CAPTURE -> CapturePane(uiState.capture, onAction, onRefresh)
                else -> NowPane(uiState.now, onRefresh)
            }
        }
    }

    if (dockOpen) {
        CortexDockSheet(
            dockState = dockState,
            onDismiss = onDismissDock,
            onAsk = onAsk,
            onAction = { id -> onAction(id) }
        )
    }
}

@Composable
private fun ShellBottomBar(
    selectedDestination: String,
    dockState: DockState,
    onDestinationSelected: (String) -> Unit,
    onOpenDock: () -> Unit,
    onQuickVoice: () -> Unit
) {
    Column(Modifier.background(CortexColors.Graphite0)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = CortexColors.Graphite2,
            shape = RoundedCornerShape(CortexRadius.Card)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onOpenDock)
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = "Open Cortex Dock" }
                ) {
                    Text(
                        if (dockState.status == DockStatus.RUNNING) dockState.progressLabel.ifBlank { "Cortex is working…" } else "Ask or capture anything",
                        style = MaterialTheme.typography.titleMedium,
                        color = CortexColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (dockState.status) {
                            DockStatus.READY -> "Answer ready · tap to reopen"
                            DockStatus.DEGRADED -> "Answer ready with fallback"
                            DockStatus.ERROR -> "Last request needs attention"
                            DockStatus.RUNNING -> "${dockState.progressPercent}%"
                            else -> "Text · voice · photo · file · screen"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CortexColors.TextQuiet,
                        maxLines = 1
                    )
                }
                TextButton(
                    onClick = onQuickVoice,
                    modifier = Modifier.size(CortexSpace.Touch).semantics { contentDescription = "Voice capture" }
                ) { Text("●", color = CortexColors.Capture) }
                TextButton(
                    onClick = onOpenDock,
                    modifier = Modifier.size(CortexSpace.Touch).semantics { contentDescription = "Open Cortex Dock" }
                ) { Text("＋", color = CortexColors.Intelligence) }
            }
        }
        NavigationBar(containerColor = CortexColors.Graphite0) {
            primaryNav.forEach { item ->
                val selected = item.id == selectedDestination
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) onDestinationSelected(item.id) },
                    icon = { Text(item.mark, color = if (selected) CortexColors.Intelligence else CortexColors.TextQuiet) },
                    label = { Text(item.label) },
                    modifier = Modifier.semantics {
                        this.selected = selected
                        contentDescription = item.label
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CortexColors.Intelligence,
                        selectedTextColor = CortexColors.Intelligence,
                        indicatorColor = CortexColors.Graphite2,
                        unselectedIconColor = CortexColors.TextQuiet,
                        unselectedTextColor = CortexColors.TextQuiet
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CortexDockSheet(
    dockState: DockState,
    onDismiss: () -> Unit,
    onAsk: (String, String) -> Unit,
    onAction: (String) -> Unit
) {
    var prompt by rememberSaveable { mutableStateOf(dockState.question) }
    var mode by rememberSaveable { mutableStateOf(dockState.mode.ifBlank { "combined" }) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CortexColors.Graphite1,
        contentColor = CortexColors.TextPrimary
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp).padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Cortex Dock", style = MaterialTheme.typography.headlineMedium)
            Text("One place to ask, capture, attach, and hand context to Cortex.", style = MaterialTheme.typography.bodyMedium, color = CortexColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Your data", "your_data", mode) { mode = it }
                ModeChip("Combined", "combined", mode) { mode = it }
                ModeChip("External", "external", mode) { mode = it }
            }
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                placeholder = { Text("Ask Cortex…") },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (prompt.isNotBlank() && dockState.status != DockStatus.RUNNING) onAsk(prompt, mode) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CortexColors.Graphite2,
                    unfocusedContainerColor = CortexColors.Graphite2,
                    disabledContainerColor = CortexColors.Graphite2,
                    focusedIndicatorColor = CortexColors.Intelligence,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = { onAsk(prompt, mode) },
                enabled = prompt.isNotBlank() && dockState.status != DockStatus.RUNNING,
                modifier = Modifier.fillMaxWidth().heightIn(min = CortexSpace.Touch),
                colors = ButtonDefaults.buttonColors(containerColor = CortexColors.Intelligence, contentColor = CortexColors.Void)
            ) { Text(if (dockState.status == DockStatus.RUNNING) "Working…" else "Ask Cortex") }

            if (dockState.status == DockStatus.RUNNING) {
                LinearProgressIndicator(
                    progress = { dockState.progressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = CortexColors.Intelligence,
                    trackColor = CortexColors.Graphite3
                )
                Text(dockState.progressLabel, style = MaterialTheme.typography.bodySmall, color = CortexColors.TextSecondary)
            }
            if (dockState.status == DockStatus.READY || dockState.status == DockStatus.DEGRADED) {
                Surface(color = CortexColors.Graphite2, shape = RoundedCornerShape(CortexRadius.Card)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Answer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (dockState.status == DockStatus.DEGRADED) "DEGRADED" else "GROUNDED",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (dockState.status == DockStatus.DEGRADED) CortexColors.Pending else CortexColors.Verified
                            )
                        }
                        Text(dockState.answer, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${dockState.sourceCount} source${if (dockState.sourceCount == 1) "" else "s"} · ${dockState.provider.ifBlank { dockState.mode }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CortexColors.TextQuiet
                        )
                    }
                }
            } else if (dockState.status == DockStatus.ERROR) {
                Text("Cortex stopped safely: ${dockState.error}", color = CortexColors.Failure, style = MaterialTheme.typography.bodyMedium)
            }

            Text("Capture", style = MaterialTheme.typography.labelLarge, color = CortexColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickDockAction("Voice", Modifier.weight(1f)) { onAction("capture.voice"); onDismiss() }
                QuickDockAction("Text", Modifier.weight(1f)) { onAction("capture.text"); onDismiss() }
                QuickDockAction("Photo", Modifier.weight(1f)) { onAction("capture.image"); onDismiss() }
                QuickDockAction("File", Modifier.weight(1f)) { onAction("capture.file"); onDismiss() }
            }
            TextButton(
                onClick = { onAction("capture.screen.setup"); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = CortexSpace.Touch)
            ) { Text("Understand this screen", color = CortexColors.TextSecondary) }
        }
    }
}

@Composable
private fun ModeChip(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        modifier = Modifier.heightIn(min = CortexSpace.Touch)
    )
}

@Composable
private fun QuickDockAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = CortexSpace.Touch)) {
        Text(label, color = CortexColors.TextPrimary)
    }
}

@Composable
private fun NowPane(summary: NowSummary, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            HeroLine(
                eyebrow = "ATTENTION · JUDGED",
                title = when {
                    summary.state == LoadState.LOADING -> "Building your current picture"
                    summary.needsYou.isEmpty() && summary.inMotion.isEmpty() -> "Nothing has earned interruption"
                    summary.needsYou.size + summary.inMotion.size == 1 -> "One thing matters now"
                    else -> "${summary.needsYou.size + summary.inMotion.size} things matter now"
                },
                body = "Cortex surfaces judged context only. Silence is intentional when nothing crosses your attention policy.",
                state = summary.state,
                onRefresh = onRefresh
            )
        }
        attentionSection("Needs You", summary.needsYou, CortexColors.Intelligence)
        attentionSection("In Motion", summary.inMotion, CortexColors.Pending)
        attentionSection("Worth Knowing", summary.worthKnowing, CortexColors.Verified)
        if (summary.recent.isNotEmpty()) {
            item { SectionHeader("Recent", summary.recent.size) }
            items(summary.recent, key = { it.id }) { item -> RecentMemoryRow(item) }
        }
        if (summary.reviewCount > 0) {
            item {
                StatusNote("${summary.reviewCount} ambiguous item${if (summary.reviewCount == 1) "" else "s"} remain outside canonical knowledge until reviewed.", CortexColors.Pending)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.attentionSection(
    title: String,
    entries: List<AttentionItem>,
    accent: Color
) {
    if (entries.isEmpty()) return
    item { SectionHeader(title, entries.size) }
    items(entries, key = { "$title-${it.id}" }) { item -> AttentionRow(item, accent) }
}

@Composable
private fun AttentionRow(item: AttentionItem, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { item.kind.lowercase().replaceFirstChar { it.uppercase() } }, style = MaterialTheme.typography.titleMedium)
            if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyMedium, color = CortexColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.kind.uppercase(), style = MaterialTheme.typography.labelMedium, color = CortexColors.TextQuiet, modifier = Modifier.padding(top = 5.dp))
        }
    }
    HorizontalDivider(color = CortexColors.Hairline.copy(alpha = 0.55f))
}

@Composable
private fun RecentMemoryRow(item: KnowledgeItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text((item.title ?: "Memory").ifBlank { "Memory" }, style = MaterialTheme.typography.titleMedium)
        val preview = listOf(item.summary, item.extractedText, item.rawText).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodyMedium, color = CortexColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = CortexColors.Hairline.copy(alpha = 0.55f))
}

@Composable
private fun WorkPane(summary: WorkSummary, onAction: (String) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            HeroLine(
                eyebrow = "PROFESSIONAL INTELLIGENCE",
                title = if (summary.state == LoadState.EMPTY) "Connect your work archive" else "Your work context is connected",
                body = "Projects, follow-up, prices, files and source evidence stay grounded in Work Vault.",
                state = summary.state,
                onRefresh = onRefresh
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("${summary.projects}", "Projects", Modifier.weight(1f))
                Metric("${summary.openFollowUps}", "Open", Modifier.weight(1f))
                Metric("${summary.prices}", "Prices", Modifier.weight(1f))
            }
            if (summary.indexing > 0) StatusNote("${summary.indexing} file change${if (summary.indexing == 1L) "" else "s"} are indexing.", CortexColors.Pending)
            SectionHeader("Workspace", null)
        }
        item { ActionRow("Ask your work archive", "Grounded across projects, vendors, PR / PO, prices and follow-up", "work.ask", onAction) }
        item { ActionRow("Projects", "${summary.projects} connected", "work.projects.open", onAction) }
        item { ActionRow("Follow-up", "${summary.openFollowUps} open", "work.followup.open", onAction) }
        item { ActionRow("Price intelligence", "${summary.prices} records", "work.prices.open", onAction) }
        item { ActionRow("Files & evidence", "${summary.files} indexed", "work.files.open", onAction) }
        item { ActionRow("Create from archive", "Grounded office files", "work.archive.open", onAction) }
        item { ActionRow("Build with ChatGPT", "Reference-led documents", "work.build_chatgpt", onAction) }
    }
}

@Composable
private fun MemoryPane(summary: MemorySummary, onAction: (String) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            HeroLine(
                eyebrow = "GROUNDED MEMORY",
                title = "What Cortex can retrieve with evidence",
                body = "Memory is broader than screenshots: grounded captures, visual evidence and connected knowledge live here without rewriting originals.",
                state = summary.state,
                onRefresh = onRefresh
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("${summary.recentGroundedItems}", "Grounded", Modifier.weight(1f))
                Metric("${summary.pending}", "Pending", Modifier.weight(1f))
                Metric("${summary.failed}", "Repair", Modifier.weight(1f))
            }
            SectionHeader("Explore", null)
        }
        item { ActionRow("Knowledge", "Browse grounded Cortex memory", "memory.knowledge.open", onAction) }
        item { ActionRow("Visual evidence", "Screenshots, OCR and semantic retrieval", "capture.library.visual.open", onAction) }
        item { ActionRow("Voice evidence", "Intentional voice captures", "capture.library.voice.open", onAction) }
        item { ActionRow("Refresh memory", "Reload current grounded state", "memory.refresh", onAction) }
    }
}

@Composable
private fun CapturePane(summary: CaptureSummary, onAction: (String) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            HeroLine(
                eyebrow = "INTENTIONAL INPUT",
                title = "Bring evidence into Cortex",
                body = "Text, voice, photos, files, shares and screen context converge into the same evidence-first pipeline.",
                state = summary.state,
                onRefresh = onRefresh
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("${summary.recentIntentionalCaptures}", "Recent", Modifier.weight(1f))
                Metric("${summary.pendingAnalysis}", "Analyzing", Modifier.weight(1f))
                Metric("${summary.failedAnalysis}", "Repair", Modifier.weight(1f))
            }
            SectionHeader("Capture", null)
        }
        item { ActionRow("Voice", "Talk naturally", "capture.voice", onAction) }
        item { ActionRow("Text", "Write or paste", "capture.text", onAction) }
        item { ActionRow("Photo", "Camera or image", "capture.image", onAction) }
        item { ActionRow("File", "Documents and audio", "capture.file", onAction) }
        item { SectionHeader("Everywhere", null) }
        item { ActionRow("Understand this screen", "Configure Android screen understanding", "capture.screen.setup", onAction) }
    }
}

@Composable
private fun HeroLine(eyebrow: String, title: String, body: String, state: LoadState, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = stateColor(state))
        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = CortexColors.TextSecondary, modifier = Modifier.padding(top = 7.dp))
        if (state == LoadState.ERROR || state == LoadState.PARTIAL) {
            TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp).heightIn(min = CortexSpace.Touch)) {
                Text(if (state == LoadState.ERROR) "Retry" else "Refresh", color = CortexColors.Intelligence)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int?) {
    Row(Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = CortexColors.TextSecondary, modifier = Modifier.weight(1f))
        if (count != null) Text("$count", style = MaterialTheme.typography.labelMedium, color = CortexColors.TextQuiet)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, actionId: String, onAction: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button) { onAction(actionId) }
            .semantics { contentDescription = title; role = Role.Button }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(CortexColors.IntelligenceDim, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CortexColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = CortexColors.TextQuiet)
    }
    HorizontalDivider(color = CortexColors.Hairline.copy(alpha = 0.55f))
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = CortexColors.TextPrimary)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = CortexColors.TextQuiet)
    }
}

@Composable
private fun StatusNote(text: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = CortexColors.TextSecondary, modifier = Modifier.weight(1f))
    }
}

private fun stateColor(state: LoadState): Color = when (state) {
    LoadState.READY -> CortexColors.Verified
    LoadState.EMPTY -> CortexColors.TextQuiet
    LoadState.PARTIAL -> CortexColors.Pending
    LoadState.ERROR -> CortexColors.Failure
    LoadState.LOADING -> CortexColors.Intelligence
    LoadState.PERMISSION_REQUIRED -> CortexColors.Pending
    LoadState.OFFLINE -> CortexColors.Pending
}
