package com.kareem.cortex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.cortex.AttentionNoisePolicy
import com.kareem.cortex.BrainRouter
import com.kareem.cortex.CognitiveStore
import com.kareem.cortex.CortexJudgedBriefProjection
import com.kareem.cortex.CortexPersonalPolicy
import com.kareem.cortex.KnowledgeItem
import com.kareem.cortex.LocalAskRouter
import com.kareem.cortex.NowQualityPolicy
import com.kareem.cortex.PrimeBriefStore
import com.kareem.cortex.VaultDb
import com.kareem.cortex.WorkVaultScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Thin presentation adapter over existing authoritative Cortex stores and judgment output. */
class CortexShellViewModel(application: Application) : AndroidViewModel(application) {
    enum class LoadState { LOADING, READY, EMPTY, PARTIAL, ERROR, PERMISSION_REQUIRED, OFFLINE }
    enum class DockStatus { IDLE, RUNNING, READY, DEGRADED, ERROR }

    data class AttentionItem(
        val id: Long,
        val kind: String,
        val title: String,
        val body: String,
        val updatedAt: Long
    )

    data class NowSummary(
        val state: LoadState = LoadState.LOADING,
        val needsYou: List<AttentionItem> = emptyList(),
        val inMotion: List<AttentionItem> = emptyList(),
        val worthKnowing: List<AttentionItem> = emptyList(),
        val recent: List<KnowledgeItem> = emptyList(),
        val reviewCount: Int = 0
    )

    data class WorkSummary(
        val state: LoadState = LoadState.LOADING,
        val projects: Long = 0,
        val files: Long = 0,
        val prices: Long = 0,
        val openFollowUps: Long = 0,
        val indexing: Long = 0
    )

    data class MemorySummary(
        val state: LoadState = LoadState.LOADING,
        val pending: Int = 0,
        val failed: Int = 0,
        val recentGroundedItems: Int = 0
    )

    data class CaptureSummary(
        val state: LoadState = LoadState.LOADING,
        val recentIntentionalCaptures: Int = 0,
        val pendingAnalysis: Int = 0,
        val failedAnalysis: Int = 0
    )

    data class DockState(
        val status: DockStatus = DockStatus.IDLE,
        val question: String = "",
        val answer: String = "",
        val mode: String = "combined",
        val provider: String = "",
        val sourceCount: Int = 0,
        val jobId: Long = 0,
        val progressLabel: String = "",
        val progressPercent: Int = 0,
        val error: String = ""
    )

    data class ShellUiState(
        val now: NowSummary = NowSummary(),
        val work: WorkSummary = WorkSummary(),
        val memory: MemorySummary = MemorySummary(),
        val capture: CaptureSummary = CaptureSummary(),
        val refreshedAt: Long = 0,
        val fatalMessage: String = ""
    )

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()
    private val _dockState = MutableStateFlow(DockState())
    val dockState: StateFlow<DockState> = _dockState.asStateFlow()
    private var askJob: Job? = null
    private var askGeneration = 0L

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = previous.copy(
                now = previous.now.copy(state = LoadState.LOADING),
                work = previous.work.copy(state = LoadState.LOADING),
                memory = previous.memory.copy(state = LoadState.LOADING),
                capture = previous.capture.copy(state = LoadState.LOADING),
                fatalMessage = ""
            )
            _uiState.value = withContext(Dispatchers.IO) { loadFromAuthoritativeStores() }
        }
    }

    fun ask(question: String, requestedMode: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val mode = when (requestedMode) {
            "your_data", "external", "combined" -> requestedMode
            else -> "combined"
        }
        val generation = ++askGeneration
        askJob?.cancel()
        _dockState.value = DockState(
            status = DockStatus.RUNNING,
            question = q,
            mode = mode,
            progressLabel = "Understanding request",
            progressPercent = 0
        )
        askJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var db: VaultDb? = null
                try {
                    db = VaultDb(getApplication())
                    BrainRouter.fast(
                        getApplication(), db, q, mode, 0L,
                        LocalAskRouter.Progress { _, label, percent ->
                            if (generation == askGeneration) {
                                _dockState.value = _dockState.value.copy(
                                    status = DockStatus.RUNNING,
                                    progressLabel = label ?: "Working",
                                    progressPercent = percent.coerceIn(0, 100)
                                )
                            }
                        }
                    )
                } finally {
                    try { db?.close() } catch (_: Throwable) {}
                }
            }
            if (generation != askGeneration) return@launch
            val degraded = result.provider == "failed" || result.provider.contains("fallback", ignoreCase = true)
            _dockState.value = DockState(
                status = if (degraded) DockStatus.DEGRADED else DockStatus.READY,
                question = q,
                answer = result.answer ?: "",
                mode = result.sourceMode ?: mode,
                provider = result.provider ?: "",
                sourceCount = result.grounded?.sources?.size ?: 0,
                jobId = result.jobId,
                progressLabel = if (degraded) "Completed with fallback" else "Answer ready",
                progressPercent = 100,
                error = result.error ?: ""
            )
        }.also { job ->
            job.invokeOnCompletion { error ->
                if (error != null && generation == askGeneration && error !is kotlinx.coroutines.CancellationException) {
                    _dockState.value = DockState(
                        status = DockStatus.ERROR,
                        question = q,
                        mode = mode,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun loadFromAuthoritativeStores(): ShellUiState {
        var db: VaultDb? = null
        return try {
            db = VaultDb(getApplication())
            CognitiveStore.ensure(db)
            val brief = CortexJudgedBriefProjection.load(getApplication(), db)
            val maxNow = CortexPersonalPolicy.maxNowItems(getApplication()).coerceAtLeast(1)
            val filteredActions = brief.actions.filter(::allowedAttentionItem)
            val filteredWaiting = brief.waiting.filter(::allowedAttentionItem)
            val filteredDecisions = brief.decisions.filter(::allowedAttentionItem)
            val needs = filteredActions.take(maxNow).map(::attention)
            val remaining = (maxNow - needs.size).coerceAtLeast(0)
            val motion = (filteredWaiting + filteredDecisions).take(remaining).map(::attention)
            val worth = brief.worthKnowing.filter(::allowedAttentionItem).take(4).map(::attention)
            val usefulRecent = if (needs.isEmpty() && motion.isEmpty() && worth.isEmpty()) brief.recent.take(4) else brief.recent.take(2)
            val nowState = if (needs.isEmpty() && motion.isEmpty() && worth.isEmpty() && usefulRecent.isEmpty()) LoadState.EMPTY else LoadState.READY

            val counts = WorkVaultScanner.counts(db)
            val workConnected = counts.projects > 0 || counts.files > 0 || counts.prices > 0 || counts.openFollowUps > 0
            val indexing = counts.newFiles + counts.modifiedFiles
            val pending = db.pendingCount()
            val failed = db.failedCount()
            val recentGrounded = brief.recent.size
            val captureItems = db.captureSearch("", 24)

            ShellUiState(
                now = NowSummary(
                    state = nowState,
                    needsYou = needs,
                    inMotion = motion,
                    worthKnowing = worth,
                    recent = usefulRecent,
                    reviewCount = brief.reviews.size
                ),
                work = WorkSummary(
                    state = if (workConnected) LoadState.READY else LoadState.EMPTY,
                    projects = counts.projects,
                    files = counts.files,
                    prices = counts.prices,
                    openFollowUps = counts.openFollowUps,
                    indexing = indexing
                ),
                memory = MemorySummary(
                    state = if (failed > 0) LoadState.PARTIAL else LoadState.READY,
                    pending = pending,
                    failed = failed,
                    recentGroundedItems = recentGrounded
                ),
                capture = CaptureSummary(
                    state = when {
                        failed > 0 -> LoadState.PARTIAL
                        captureItems.isEmpty() -> LoadState.EMPTY
                        else -> LoadState.READY
                    },
                    recentIntentionalCaptures = captureItems.size,
                    pendingAnalysis = pending,
                    failedAnalysis = failed
                ),
                refreshedAt = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            ShellUiState(
                now = NowSummary(state = LoadState.ERROR),
                work = WorkSummary(state = LoadState.ERROR),
                memory = MemorySummary(state = LoadState.ERROR),
                capture = CaptureSummary(state = LoadState.ERROR),
                refreshedAt = System.currentTimeMillis(),
                fatalMessage = t.message ?: t.javaClass.simpleName
            )
        } finally {
            try { db?.close() } catch (_: Throwable) {}
        }
    }

    private fun allowedAttentionItem(item: PrimeBriefStore.Item): Boolean {
        if (NowQualityPolicy.suppress(item.kind, item.source, item.title, item.body)) return false
        return !AttentionNoisePolicy.suppress(item.source, item.title, item.body, item.kind, "")
    }

    private fun attention(item: PrimeBriefStore.Item) = AttentionItem(
        id = item.id,
        kind = item.kind ?: "CONTEXT",
        title = item.title ?: "",
        body = item.body ?: "",
        updatedAt = item.updatedAt
    )
}
