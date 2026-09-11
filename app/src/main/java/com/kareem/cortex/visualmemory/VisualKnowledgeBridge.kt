package com.kareem.cortex.visualmemory

import android.content.Context
import com.kareem.cortex.AnalysisResult
import com.kareem.cortex.Fingerprint
import com.kareem.cortex.LocalAnalyzer
import com.kareem.cortex.VaultDb
import com.kareem.cortex.visualmemory.data.db.MediaItemEntity
import org.json.JSONObject

object VisualKnowledgeBridge {
    @JvmStatic
    fun promote(context: Context, item: MediaItemEntity, ocrText: String): Long {
        if (!item.knowledgeEligible || item.selfReferenceScore >= 0.72f) return 0L
        val text = ocrText.trim()
        if (text.length < 3) return 0L

        val result: AnalysisResult = LocalAnalyzer.analyze(text, "text/plain")
        result.extractedText = text
        result.engine = "picbrain_visual_bridge"
        result.version = "1"

        val sourceUri = item.contentUri
        val evidenceFingerprint = Fingerprint.text(
            "picbrain-visual|" + item.mediaId + "|" + item.dateModifiedSeconds + "|" + normalize(text)
        )

        val metadata = JSONObject()
            .put("source_kind", "PICBRAIN_SCREENSHOT")
            .put("media_id", item.mediaId)
            .put("content_uri", sourceUri)
            .put("captured_at", item.dateTakenMillis ?: item.dateAddedSeconds * 1000L)
            .put("origin", item.origin)
            .put("self_reference_score", item.selfReferenceScore.toDouble())
            .put("derivation_depth", item.derivationDepth + 1)
            .put("knowledge_eligible", true)
            .put("provenance_reason", item.provenanceReason ?: "")
            .put("ocr_engine", item.ocrEngine ?: "")
            .put("evidence_fingerprint", evidenceFingerprint)
            .toString()

        val db = VaultDb(context.applicationContext)
        return try {
            val inserted = db.insert(
                "SCREENSHOT_MEMORY",
                "picbrain",
                if (result.title.isBlank()) item.displayName else result.title,
                text,
                result.category,
                result.tags,
                sourceUri,
                evidenceFingerprint,
                metadata
            )
            val knowledgeId = kotlin.math.abs(inserted)
            if (inserted > 0L) db.applyAnalysis(knowledgeId, result)
            knowledgeId
        } finally {
            db.close()
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim().take(2400)
}
