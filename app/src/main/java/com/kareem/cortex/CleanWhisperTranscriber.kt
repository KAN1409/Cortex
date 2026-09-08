package com.kareem.cortex

import android.content.Context
import android.os.Build
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Unbiased Whisper benchmark profile.
 * No lexical prompt, no known-word corrections, no English rescue rules and no tail heuristics.
 * Uses the same selected GGML model and WAV chunker as the recovered pipeline so model/runtime
 * quality can be compared without sample-specific tuning.
 */
class CleanWhisperTranscriber private constructor() {
    interface Callback {
        fun ok(result: TranscriptResult)
        fun fail(error: Exception)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JvmStatic
        fun transcribe(context: Context, audio: File, callback: Callback) {
            val app = context.applicationContext
            scope.launch {
                val chunks = ArrayList<WavSpeechChunker.Chunk>()
                try {
                    if (!audio.exists()) throw IllegalArgumentException("Audio file not found")
                    if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
                        throw UnsupportedOperationException("Clean Whisper benchmark requires arm64-v8a")
                    }
                    if (!audio.name.lowercase(Locale.US).endsWith(".wav")) {
                        throw UnsupportedOperationException("Clean Whisper benchmark expects Cortex WAV audio")
                    }
                    if (!LocalAsrModelStore.ready(app)) {
                        throw IllegalStateException("No local Whisper model selected")
                    }

                    chunks.addAll(WavSpeechChunker.split(audio, app.cacheDir))
                    if (chunks.isEmpty()) throw IllegalStateException("No speech detected in this recording")
                    val modelFile = LocalAsrModelStore.modelFile(app)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        val out = TranscriptResult()
                        out.language = "auto"
                        out.engine = "whisper_cpp_clean_${LocalAsrModelStore.profileId(app)}"
                        out.version = "clean-1"
                        out.durationMs = WavSpeechChunker.durationMs(audio)
                        val parts = ArrayList<String>()

                        for (chunk in chunks) {
                            val decoded = Whisper.transcribe(model, chunk.file.absolutePath, cleanConfig())
                            val text = decoded.text?.trim().orEmpty()
                            if (text.isNotEmpty()) parts.add(text)
                            if (decoded.segments.isNotEmpty()) {
                                for (segment in decoded.segments) {
                                    val segmentText = segment.text?.trim().orEmpty()
                                    if (segmentText.isEmpty()) continue
                                    val absStart = (chunk.startMs + segment.startMs.coerceAtLeast(0L))
                                        .coerceIn(chunk.startMs, chunk.endMs)
                                    val absEnd = (chunk.startMs + segment.endMs.coerceAtLeast(segment.startMs))
                                        .coerceIn(absStart, chunk.endMs)
                                    out.segments.add(TranscriptResult.Segment(absStart, absEnd, segmentText, -1f))
                                }
                            }
                        }

                        out.text = parts.joinToString(" ").trim()
                        if (out.text.isEmpty()) throw IllegalStateException("Clean Whisper returned an empty transcript")
                        callback.ok(out)
                    } finally {
                        Whisper.releaseModel(model)
                    }
                } catch (e: Exception) {
                    callback.fail(e)
                } finally {
                    for (c in chunks) try { c.file.delete() } catch (_: Exception) {}
                }
            }
        }

        private fun cleanConfig(): WhisperConfig = WhisperConfig(
            language = "auto",
            translate = false,
            threads = chooseThreads(),
            maxSegmentLength = 0,
            printTimestamps = true,
            initialPrompt = "",
            suppressNonSpeechTokens = true,
        )

        @JvmStatic
        fun modelReady(context: Context): Boolean = LocalAsrModelStore.ready(context.applicationContext)

        private fun chooseThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            return cores.coerceIn(2, 6)
        }
    }
}
