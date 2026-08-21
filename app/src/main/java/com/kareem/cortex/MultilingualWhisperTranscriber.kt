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
 * v1.0.16:
 * - VAD supplies onset/end, but short voice notes are decoded as one long context window.
 * - Primary pass is multilingual auto with translation disabled.
 * - English rescue is localized to the acoustic position of an existing Latin span.
 * - Rescue replaces only that Latin span; Arabic before/after is immutable.
 * - A final overlapping tail pass protects lower-energy Arabic/English endings.
 */
class MultilingualWhisperTranscriber private constructor() {
    interface Callback {
        fun ok(result: TranscriptResult)
        fun fail(error: Exception)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val RESCUE_CONTEXT_MS = 800L
        private const val TAIL_CONTEXT_MS = 3500L

        @JvmStatic
        fun transcribe(context: Context, audio: File, callback: Callback) {
            val app = context.applicationContext
            scope.launch {
                val chunks = ArrayList<WavSpeechChunker.Chunk>()
                val tempFiles = ArrayList<File>()
                try {
                    if (!audio.exists()) throw IllegalArgumentException("Audio file not found")
                    if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
                        throw UnsupportedOperationException("Local code-switch ASR requires arm64-v8a")
                    }
                    if (!audio.name.lowercase(Locale.US).endsWith(".wav")) {
                        throw UnsupportedOperationException("Local code-switch ASR expects Cortex WAV audio")
                    }
                    if (!LocalAsrModelStore.ready(app)) {
                        throw IllegalStateException("No local ASR model selected. Choose a supported Whisper Small/Medium Q8_0 GGML model in Cortex first.")
                    }

                    WhisperRuntimeState.stage(app, "detecting speech", "Finding onset/end; short notes stay in one multilingual context window")
                    chunks.addAll(WavSpeechChunker.split(audio, app.cacheDir))
                    if (chunks.isEmpty()) throw IllegalStateException("No speech detected in this recording")
                    val fullDuration = WavSpeechChunker.durationMs(audio)

                    val modelFile = LocalAsrModelStore.modelFile(app)
                    WhisperRuntimeState.stage(app, "loading model", modelFile.name)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        val out = TranscriptResult()
                        out.language = "ar-EG+en-codeswitch-auto"
                        out.engine = "whisper_cpp_local_codeswitch_${LocalAsrModelStore.profileId(app)}_short_window_auto_span_rescue_tail_retry"
                        out.version = "11"
                        out.durationMs = fullDuration
                        val recordingParts = ArrayList<String>()

                        for ((chunkIndex, chunk) in chunks.withIndex()) {
                            val chunkNumber = chunkIndex + 1
                            val chunkDuration = chunk.endMs - chunk.startMs
                            WhisperRuntimeState.stage(
                                app,
                                "primary decode",
                                "Window $chunkNumber/${chunks.size} • ${chunk.startMs}-${chunk.endMs} ms • multilingual auto"
                            )
                            val primary = Whisper.transcribe(model, chunk.file.absolutePath, config("auto"))
                            val primaryText = primary.text.trim()
                            if (primaryText.isEmpty()) continue

                            val correctedSegments = ArrayList<String>()
                            if (primary.segments.isNotEmpty()) {
                                for (segment in primary.segments) {
                                    var selected = segment.text.trim()
                                    if (selected.isEmpty()) continue
                                    if (CodeSwitchCandidateSelector.shouldEnglishRetry(selected)) {
                                        val fractions = CodeSwitchCandidateSelector.englishSpanFractions(selected)
                                        if (fractions[0] >= 0.0 && fractions[1] > fractions[0]) {
                                            val segStart = segment.startMs.coerceAtLeast(0L)
                                            val segEnd = segment.endMs.coerceAtLeast(segStart + 1L)
                                            val segDuration = (segEnd - segStart).coerceAtLeast(1L)
                                            val spanStart = segStart + (segDuration * fractions[0]).toLong()
                                            val spanEnd = segStart + (segDuration * fractions[1]).toLong()
                                            val rescueStart = (spanStart - RESCUE_CONTEXT_MS).coerceAtLeast(0L)
                                            val rescueEnd = (spanEnd + RESCUE_CONTEXT_MS).coerceAtMost(chunkDuration)
                                            if (rescueEnd > rescueStart + 120L) {
                                                WhisperRuntimeState.stage(
                                                    app,
                                                    "english rescue",
                                                    "Window $chunkNumber/${chunks.size} • ${rescueStart}-${rescueEnd} ms • span-local English re-decode"
                                                )
                                                var rescueFile: File? = null
                                                try {
                                                    val rescueChunk = WavSpeechChunker.slice(chunk.file, app.cacheDir, rescueStart, rescueEnd)
                                                    rescueFile = rescueChunk.file
                                                    tempFiles.add(rescueFile)
                                                    val english = Whisper.transcribe(model, rescueFile.absolutePath, config("en"))
                                                    selected = CodeSwitchCandidateSelector.mergeEnglishSpan(selected, english.text.trim())
                                                } catch (_: Exception) {
                                                    // Primary segment remains untouched if rescue fails.
                                                }
                                            }
                                        }
                                    }
                                    correctedSegments.add(selected)
                                    val absStart = (chunk.startMs + segment.startMs).coerceIn(chunk.startMs, chunk.endMs)
                                    val absEnd = (chunk.startMs + segment.endMs).coerceIn(absStart, chunk.endMs)
                                    out.segments.add(TranscriptResult.Segment(absStart, absEnd, selected, -1f))
                                }
                            }

                            var chunkText = if (correctedSegments.isNotEmpty()) {
                                CodeSwitchCandidateSelector.joinVerbatim(*correctedSegments.toTypedArray())
                            } else {
                                primaryText.replace(Regex("\\s+"), " ").trim()
                            }

                            // Short-note invariant: always re-check the final ~3.5 s with overlap.
                            // This prevents a low-energy Arabic closing phrase from disappearing
                            // after an English segment or endpointing decision.
                            if (chunkDuration > 2500L) {
                                val tailStart = (chunkDuration - TAIL_CONTEXT_MS).coerceAtLeast(0L)
                                WhisperRuntimeState.stage(
                                    app,
                                    "tail retry",
                                    "Window $chunkNumber/${chunks.size} • ${tailStart}-${chunkDuration} ms • multilingual ending check"
                                )
                                var tailFile: File? = null
                                try {
                                    val tailChunk = WavSpeechChunker.slice(chunk.file, app.cacheDir, tailStart, chunkDuration)
                                    tailFile = tailChunk.file
                                    tempFiles.add(tailFile)
                                    val tail = Whisper.transcribe(model, tailFile.absolutePath, config("auto"))
                                    val tailText = tail.text.trim()
                                    val novel = CodeSwitchCandidateSelector.novelTail(chunkText, tailText)
                                    if (novel.isNotEmpty()) {
                                        chunkText = CodeSwitchCandidateSelector.mergeTail(chunkText, tailText)
                                        var relStart = tailStart
                                        var relEnd = chunkDuration
                                        if (tail.segments.isNotEmpty()) {
                                            relStart = tailStart + tail.segments.first().startMs.coerceAtLeast(0L)
                                            relEnd = tailStart + tail.segments.last().endMs.coerceAtLeast(tail.segments.first().startMs)
                                        }
                                        val absStart = (chunk.startMs + relStart).coerceIn(chunk.startMs, chunk.endMs)
                                        val absEnd = (chunk.startMs + relEnd).coerceIn(absStart, chunk.endMs)
                                        out.segments.add(TranscriptResult.Segment(absStart, absEnd, novel, -1f))
                                    }
                                } catch (_: Exception) {
                                    // A complete primary decode remains usable if the defensive tail pass fails.
                                }
                            }

                            if (chunkText.isNotEmpty()) recordingParts.add(chunkText)
                        }

                        out.text = CodeSwitchCandidateSelector.joinVerbatim(*recordingParts.toTypedArray())
                        if (out.text.isEmpty()) throw IllegalStateException("Code-switch ASR returned an empty transcript")
                        WhisperRuntimeState.stage(app, "ready", "Single-context auto decode + span rescue + tail retry completed")
                        callback.ok(out)
                    } finally {
                        Whisper.releaseModel(model)
                    }
                } catch (e: Exception) {
                    WhisperRuntimeState.error(app, e)
                    callback.fail(e)
                } finally {
                    for (c in chunks) try { c.file.delete() } catch (_: Exception) {}
                    for (f in tempFiles) try { f.delete() } catch (_: Exception) {}
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
