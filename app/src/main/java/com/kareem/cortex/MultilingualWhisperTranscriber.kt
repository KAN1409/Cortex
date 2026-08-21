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
 * v1.0.12 uses a user-imported whisper.cpp GGML model from local storage.
 * No runtime model download and no paid/cloud API are required.
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

                    val modelFile = LocalAsrModelStore.modelFile(app)
                    WhisperRuntimeState.stage(app, "loading model", modelFile.name)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        WhisperRuntimeState.stage(app, "transcribing", "Egyptian Arabic + English • Medium q8_0 • no translation")
                        val config = WhisperConfig(
                            language = "ar",
                            translate = false,
                            threads = chooseThreads(),
                            maxSegmentLength = 0,
                            printTimestamps = true,
                        )
                        val whisper = Whisper.transcribe(model, audio.absolutePath, config)
                        val text = whisper.text.trim()
                        if (text.isEmpty()) throw IllegalStateException("Code-switch ASR returned an empty transcript")

                        val out = TranscriptResult()
                        out.text = text
                        out.language = "ar-EG+en-codeswitch"
                        out.engine = "whisper_cpp_local_codeswitch_medium_q8_0"
                        out.version = "8"
                        var maxEnd = 0L
                        for (segment in whisper.segments) {
                            val s = segment.text.trim()
                            if (s.isEmpty()) continue
                            maxEnd = maxOf(maxEnd, segment.endMs)
                            out.segments.add(TranscriptResult.Segment(segment.startMs, segment.endMs, s, -1f))
                        }
                        out.durationMs = if (maxEnd > 0L) maxEnd else wavDurationMs(audio)
                        if (out.segments.isEmpty()) out.segments.add(TranscriptResult.Segment(0, out.durationMs, text, -1f))
                        WhisperRuntimeState.stage(app, "ready", "Local Egyptian-English code-switch transcription completed")
                        callback.ok(out)
                    } finally {
                        Whisper.releaseModel(model)
                    }
                } catch (e: Exception) {
                    WhisperRuntimeState.error(app, e)
                    callback.fail(e)
                }
            }
        }

        @JvmStatic
        fun modelReady(context: Context): Boolean = LocalAsrModelStore.ready(context.applicationContext)

        private fun chooseThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            return cores.coerceIn(2, 6)
        }

        private fun wavDurationMs(file: File): Long {
            val payload = (file.length() - 44L).coerceAtLeast(0L)
            return payload * 1000L / 32_000L
        }
    }
}
