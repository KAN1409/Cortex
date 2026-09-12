package com.kareem.cortex.visualmemory

import android.content.Context
import com.kareem.cortex.AnalysisResult
import com.kareem.cortex.CanonicalEvidenceIngestor
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
        result.version = "2"

        val sourceUri = item.contentUri
        val evidenceFingerprint = Fingerprint.text(
            "picbrain-visual|" + item.mediaId + "|" + item.dateModifiedSeconds + "|" + normalize(text)
        )

        val capturedAt = item.dateTakenMillis ?: item.dateAddedSeconds * 1000L
        val metadata = JSONObject()
            .put("source_kind", "PICBRAIN_SCREENSHOT")
            .put("media_id", item.mediaId)
            .put("content_uri", sourceUri)
            .put("captured_at", capturedAt)
            .put("origin", item.origin)
            .put("self_reference_score", item.selfReferenceScore.toDouble())
            .put("derivation_depth", item.derivationDepth + 1)
            .put("knowledge_eligible", true)
            .put("provenance_reason", item.provenanceReason ?: "")
            .put("ocr_engine", item.ocrEngine ?: "")
            .put("evidence_fingerprint", evidenceFingerprint)

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
                metadata.toString()
            )
            val knowledgeId = kotlin.math.abs(inserted)
            if (knowledgeId <= 0L) {
                0L
            } else {
                // OCR/visual capture is evidence. Never persist LocalAnalyzer's imperative-looking
                // phrases as legacy actions; a later grounded world-state path owns action judgment.
                result.actions.clear()
                if (inserted > 0L) db.applyAnalysis(knowledgeId, result)

                metadata.put("knowledge_item_id", knowledgeId)
                CanonicalEvidenceIngestor.ingestVisual(
                    db,
                    "picbrain",
                    "media:${item.mediaId}:${item.dateModifiedSeconds}",
                    if (result.title.isBlank()) item.displayName else result.title,
                    text,
                    capturedAt,
                    metadata
                )
                knowledgeId
            }
        } finally {
            db.close()
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim().take(2400)
}
