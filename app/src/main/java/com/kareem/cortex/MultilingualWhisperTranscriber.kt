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
 * Cortex local ASR for Egyptian Arabic <-> English code-switching.
 *
 * v1.0.14 pipeline:
 * 1) local energy VAD on the original Cortex WAV,
 * 2) decode each speech chunk with multilingual auto-language,
 * 3) when the auto hypothesis contains Latin/code-switch evidence, re-decode that
 *    exact acoustic chunk with an English prior,
 * 4) select/merge candidates without translating or Arabicizing raw ASR text,
 * 5) restore every chunk's absolute offset into the original recording timeline.
 */
class MultilingualWhisperTranscriber private constructor() {
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
                        throw UnsupportedOperationException("Local code-switch ASR requires arm64-v8a")
                    }
                    if (!audio.name.lowercase(Locale.US).endsWith(".wav")) {
                        throw UnsupportedOperationException("Local code-switch ASR expects Cortex WAV audio")
                    }
                    if (!LocalAsrModelStore.ready(app)) {
                        throw IllegalStateException("No local ASR model selected. Choose ggml-codeswitch-medium-q8_0.bin in Cortex first.")
                    }

                    WhisperRuntimeState.stage(app, "detecting speech", "Finding real speech intervals and preserving original timestamps")
                    chunks.addAll(WavSpeechChunker.split(audio, app.cacheDir))
                    if (chunks.isEmpty()) throw IllegalStateException("No speech detected in this recording")
                    val fullDuration = WavSpeechChunker.durationMs(audio)

                    val modelFile = LocalAsrModelStore.modelFile(app)
                    WhisperRuntimeState.stage(app, "loading model", modelFile.name)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        val out = TranscriptResult()
                        out.language = "ar-EG+en-codeswitch-auto"
                        out.engine = "whisper_cpp_local_codeswitch_medium_q8_0_vad_auto_en_rescue"
                        out.version = "9"
                        out.durationMs = fullDuration
                        val finalParts = ArrayList<String>()

                        for ((index, chunk) in chunks.withIndex()) {
                            val n = index + 1
                            WhisperRuntimeState.stage(
                                app,
                                "transcribing",
                                "Chunk $n/${chunks.size} • ${chunk.startMs}-${chunk.endMs} ms • multilingual auto"
                            )
                            val auto = Whisper.transcribe(model, chunk.file.absolutePath, config("auto"))
                            val autoText = auto.text.trim()
                            if (autoText.isEmpty()) continue

                            var chosenText = autoText
                            var timingSource = auto
                            if (CodeSwitchCandidateSelector.shouldEnglishRetry(autoText)) {
                                WhisperRuntimeState.stage(
                                    app,
                                    "english rescue",
                                    "Chunk $n/${chunks.size} • switch-aware English re-decode on the same acoustic interval"
                                )
                                try {
                                    val english = Whisper.transcribe(model, chunk.file.absolutePath, config("en"))
                                    val englishText = english.text.trim()
                                    val selected = CodeSwitchCandidateSelector.choose(autoText, englishText)
                                    chosenText = selected
                                    if (selected == englishText && englishText.isNotEmpty()) timingSource = english
                                } catch (_: Exception) {
                                    // Auto transcript remains valid; English rescue is optional.
                                    chosenText = autoText
                                }
                            }

                            chosenText = chosenText.replace(Regex("\\s+"), " ").trim()
                            if (chosenText.isEmpty()) continue
                            finalParts.add(chosenText)

                            var relStart = 0L
                            var relEnd = chunk.endMs - chunk.startMs
                            if (timingSource.segments.isNotEmpty()) {
                                relStart = timingSource.segments.first().startMs.coerceAtLeast(0L)
                                relEnd = timingSource.segments.last().endMs.coerceAtLeast(relStart)
                            }
                            val absStart = (chunk.startMs + relStart).coerceIn(chunk.startMs, chunk.endMs)
                            val absEnd = (chunk.startMs + relEnd).coerceIn(absStart, chunk.endMs)
                            out.segments.add(TranscriptResult.Segment(absStart, absEnd, chosenText, -1f))
                        }

                        out.text = CodeSwitchCandidateSelector.joinVerbatim(*finalParts.toTypedArray())
                        if (out.text.isEmpty()) throw IllegalStateException("Code-switch ASR returned an empty transcript")
                        WhisperRuntimeState.stage(app, "ready", "VAD + multilingual auto + English rescue completed")
                        callback.ok(out)
                    } finally {
                        Whisper.releaseModel(model)
                    }
                } catch (e: Exception) {
                    WhisperRuntimeState.error(app, e)
                    callback.fail(e)
                } finally {
                    for (c in chunks) try { c.file.delete() } catch (_: Exception) {}
                }
            }
        }

        private fun config(language: String): WhisperConfig = WhisperConfig(
            language = language,
            translate = false,
            threads = chooseThreads(),
            maxSegmentLength = 0,
            printTimestamps = true,
        )

        @JvmStatic
        fun modelReady(context: Context): Boolean = LocalAsrModelStore.ready(context.applicationContext)

        private fun chooseThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            return cores.coerceIn(2, 6)
        }
    }
}
