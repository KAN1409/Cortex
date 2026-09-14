package com.kareem.cortex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.cortex.KnowledgeItem
import com.kareem.cortex.PrimeBriefStore
import com.kareem.cortex.VaultDb
import com.kareem.cortex.WorkVaultScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin presentation adapter. It reads existing authoritative Cortex stores and never re-judges evidence.
 */
class CortexShellViewModel(application: Application) : AndroidViewModel(application) {
    enum class LoadState { LOADING, READY, EMPTY, PARTIAL, ERROR, PERMISSION_REQUIRED, OFFLINE }

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

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                now = _uiState.value.now.copy(state = LoadState.LOADING),
                work = _uiState.value.work.copy(state = LoadState.LOADING),
                memory = _uiState.value.memory.copy(state = LoadState.LOADING),
                capture = _uiState.value.capture.copy(state = LoadState.LOADING),
                fatalMessage = ""
            )
            val loaded = withContext(Dispatchers.IO) { loadFromAuthoritativeStores() }
            _uiState.value = loaded
        }
    }

    private fun loadFromAuthoritativeStores(): ShellUiState {
        var db: VaultDb? = null
        return try {
            db = VaultDb(getApplication())
            val brief = PrimeBriefStore.load(db)
            val needs = ArrayList<AttentionItem>()
            brief.actions.take(4).mapTo(needs, ::attention)
            brief.decisions.take(2).mapTo(needs, ::attention)
            val motion = brief.waiting.take(4).map(::attention)
            val worth = brief.worthKnowing.take(4).map(::attention)
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

    private fun attention(item: PrimeBriefStore.Item) = AttentionItem(
        id = item.id,
        kind = item.kind,
        title = item.title,
        body = item.body,
        updatedAt = item.updatedAt
    )
}
