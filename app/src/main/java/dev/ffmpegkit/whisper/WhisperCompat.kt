package dev.ffmpegkit.whisper

import android.content.Context

/**
 * Recovery-build compatibility surface.
 * The v70 lineage currently quarantines native local Whisper through CapabilitySupervisor.
 * This keeps the source tree buildable when the private prompt AAR is unavailable in CI.
 * If ASR_NATIVE is re-enabled, restore the real AAR before enabling this candidate.
 */
data class WhisperConfig(
    val language: String = "auto",
    val translate: Boolean = false,
    val threads: Int = 4,
    val maxSegmentLength: Int = 0,
    val printTimestamps: Boolean = true,
    val initialPrompt: String = "",
    val suppressNonSpeechTokens: Boolean = true
)

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String?
)

data class WhisperResult(
    val text: String?,
    val segments: List<WhisperSegment> = emptyList()
)

object Whisper {
    @JvmStatic fun loadModel(context: Context, modelPath: String): Long {
        throw IllegalStateException("Local Whisper native AAR is unavailable in this recovery build")
    }

    @JvmStatic fun transcribe(model: Long, audioPath: String, config: WhisperConfig): WhisperResult {
        throw IllegalStateException("Local Whisper native AAR is unavailable in this recovery build")
    }

    @JvmStatic fun releaseModel(model: Long) = Unit
}
