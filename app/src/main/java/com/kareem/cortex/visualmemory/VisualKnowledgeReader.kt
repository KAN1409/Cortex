package com.kareem.cortex.visualmemory

import android.content.Context
import com.kareem.cortex.KnowledgeItem
import com.kareem.cortex.VaultDb
import org.json.JSONObject

data class VisualKnowledgeSnapshot(
    val knowledgeId: Long = 0,
    val ready: Boolean = false,
    val summary: String = "",
    val category: String = "",
    val tags: String = "",
    val entities: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val derivationDepth: Int = 0
)

object VisualKnowledgeReader {
    @JvmStatic
    fun read(context: Context, mediaId: Long): VisualKnowledgeSnapshot {
        val db = VaultDb(context.applicationContext)
        return try {
            val items = db.captureSearch("", 800)
            val match = items.firstOrNull { item ->
                if (item.source != "picbrain") false
                else runCatching {
                    JSONObject(item.metadataJson ?: "{}").optLong("media_id", -1L) == mediaId
                }.getOrDefault(false)
            } ?: return VisualKnowledgeSnapshot()
            val meta = runCatching { JSONObject(match.metadataJson ?: "{}") }.getOrNull()
            VisualKnowledgeSnapshot(
                knowledgeId = match.id,
                ready = match.status == "analyzed",
                summary = match.summary ?: "",
                category = match.category ?: "",
                tags = match.tags ?: "",
                entities = db.entities(match.id),
                actions = db.actions(match.id),
                derivationDepth = meta?.optInt("derivation_depth", 1) ?: 1
            )
        } finally {
            db.close()
        }
    }
}
