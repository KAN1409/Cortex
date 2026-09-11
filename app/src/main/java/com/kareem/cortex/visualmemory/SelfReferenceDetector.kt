package com.kareem.cortex.visualmemory

import com.kareem.cortex.visualmemory.data.db.MediaItemEntity
import java.util.Locale

object SelfReferenceDetector {
    private val strongMarkers = listOf(
        "visual memory",
        "ocr status",
        "semantic status",
        "open original image",
        "cortex is processing",
        "needs you",
        "waiting for someone",
        "what deserves your attention right now",
        "capture anything"
    )

    private val navMarkers = listOf("now", "brief", "capture", "brain")
    private val productMarkers = listOf("cortex", "semantic indexed", "transcription")

    @JvmStatic
    fun evaluate(item: MediaItemEntity, rawText: String): CaptureMatch {
        if (item.origin == "CORTEX_SELF_CAPTURE" || item.selfReferenceScore >= 0.95f) {
            return CaptureMatch(
                origin = "CORTEX_SELF_CAPTURE",
                selfReferenceScore = 1.0f,
                derivationDepth = maxOf(1, item.derivationDepth),
                knowledgeEligible = false,
                reason = item.provenanceReason ?: "Matched Android screenshot callback while Cortex was foreground"
            )
        }

        val normalized = rawText
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return CaptureMatch.external()

        var score = 0f
        val strongCount = strongMarkers.count { normalized.contains(it) }
        score += minOf(0.72f, strongCount * 0.24f)

        val navCount = navMarkers.count { Regex("(^|\\W)" + Regex.escape(it) + "($|\\W)").containsMatchIn(normalized) }
        if (navCount >= 3) score += 0.22f
        if (navCount == 4) score += 0.08f

        val productCount = productMarkers.count { normalized.contains(it) }
        score += minOf(0.24f, productCount * 0.12f)

        val looksLikeVisualMemoryDetail =
            normalized.contains("ocr status") &&
            normalized.contains("semantic status") &&
            normalized.contains("transcription")
        if (looksLikeVisualMemoryDetail) score = maxOf(score, 0.96f)

        score = score.coerceIn(0f, 1f)
        return if (score >= BLOCK_THRESHOLD) {
            CaptureMatch(
                origin = "CORTEX_RENDERED",
                selfReferenceScore = score,
                derivationDepth = maxOf(1, item.derivationDepth),
                knowledgeEligible = false,
                reason = "Cortex UI fingerprint detected in screenshot OCR"
            )
        } else {
            CaptureMatch(
                origin = item.origin,
                selfReferenceScore = maxOf(item.selfReferenceScore, score),
                derivationDepth = item.derivationDepth,
                knowledgeEligible = item.knowledgeEligible,
                reason = item.provenanceReason
            )
        }
    }

    private const val BLOCK_THRESHOLD = 0.72f
}
