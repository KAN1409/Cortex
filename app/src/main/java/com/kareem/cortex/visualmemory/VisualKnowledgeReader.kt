package com.kareem.cortex.visualmemory

import android.content.Context
import android.database.Cursor
import com.kareem.cortex.KnowledgeV2Schema
import com.kareem.cortex.KnowledgeV2Store
import com.kareem.cortex.VaultDb

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
            val sql = db.readableDatabase
            KnowledgeV2Schema.ensure(sql)

            val evidence = sql.rawQuery(
                "SELECT id,derivation_depth FROM kv2_evidence WHERE source_media_id=? ORDER BY updated_at DESC LIMIT 1",
                arrayOf(mediaId.toString())
            )
            if (!evidence.moveToFirst()) {
                evidence.close()
                return VisualKnowledgeSnapshot()
            }
            val evidenceId = evidence.getLong(0)
            val depth = evidence.getInt(1)
            evidence.close()

            val stateCursor = sql.rawQuery(
                "SELECT state FROM kv2_processing WHERE evidence_id=? AND stage=? AND pipeline_version=? LIMIT 1",
                arrayOf(
                    evidenceId.toString(),
                    KnowledgeV2Store.STAGE_EXTRACTION,
                    KnowledgeV2Schema.PIPELINE_VERSION.toString()
                )
            )
            val state = if (stateCursor.moveToFirst()) stateCursor.getString(0).orEmpty() else ""
            stateCursor.close()

            val understanding = sql.rawQuery(
                "SELECT title,summary,category,tags FROM kv2_understanding WHERE evidence_id=? LIMIT 1",
                arrayOf(evidenceId.toString())
            )
            var summary = ""
            var category = ""
            var tags = ""
            if (understanding.moveToFirst()) {
                summary = understanding.getString(1).orEmpty()
                category = understanding.getString(2).orEmpty()
                tags = understanding.getString(3).orEmpty()
            }
            understanding.close()

            val entities = mutableListOf<String>()
            val entityCursor = sql.rawQuery(
                "SELECT mention_kind,mention_text,resolution_confidence FROM kv2_entity_mentions WHERE evidence_id=? ORDER BY id ASC",
                arrayOf(evidenceId.toString())
            )
            while (entityCursor.moveToNext()) {
                val kind = entityCursor.getString(0).orEmpty()
                val text = entityCursor.getString(1).orEmpty()
                if (text.isNotBlank()) entities += kind + ": " + text
            }
            entityCursor.close()

            val actions = mutableListOf<String>()
            val eventCursor = sql.rawQuery(
                "SELECT ev.title,ev.body FROM kv2_events ev JOIN kv2_event_evidence ee ON ee.event_id=ev.id WHERE ee.evidence_id=? ORDER BY ev.id ASC",
                arrayOf(evidenceId.toString())
            )
            while (eventCursor.moveToNext()) {
                val title = eventCursor.getString(0).orEmpty()
                val body = eventCursor.getString(1).orEmpty()
                if (title.isNotBlank()) actions += if (body.isBlank() || body == title) title else body
            }
            eventCursor.close()

            VisualKnowledgeSnapshot(
                knowledgeId = evidenceId,
                ready = state == "DONE",
                summary = summary,
                category = category,
                tags = tags,
                entities = entities,
                actions = actions,
                derivationDepth = depth + 1
            )
        } finally {
            db.close()
        }
    }
}
