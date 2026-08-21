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
import java.io.FileOutputStream
import java.util.Locale

/**
 * Cortex local ASR tuned for Egyptian Arabic <-> English code-switching.
 *
 * v1.0.12 ships a Q4_0 GGML conversion of
 * Seif-Eldeen-Sameh/whisper-medium-arabic-codeswitched inside the APK.
 * There is no runtime model download and no paid/cloud API.
 */
class MultilingualWhisperTranscriber private constructor() {
    interface Callback {
        fun ok(result: TranscriptResult)
        fun fail(error: Exception)
    }

    companion object {
        private const val MODEL_NAME = "ggml-egyptian-codeswitch-medium-q4_0.bin"
        private const val ASSET_MODEL = "models/$MODEL_NAME"
        private const val MIN_MODEL_BYTES = 360_000_000L
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

                    WhisperRuntimeState.stage(app, "preparing model", "Egyptian Arabic + English Medium code-switch model")
                    val modelFile = ensureBundledModel(app)
                    WhisperRuntimeState.stage(app, "loading model", modelFile.name)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        WhisperRuntimeState.stage(app, "transcribing", "Egyptian Arabic + English • Medium • no translation")
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
                        out.engine = "whisper_cpp_egyptian_english_codeswitch_medium_q4_0"
                        out.version = "7"
                        var maxEnd = 0L
                        for (segment in whisper.segments) {
                            val s = segment.text.trim()
                            if (s.isEmpty()) continue
                            maxEnd = maxOf(maxEnd, segment.endMs)
                            out.segments.add(TranscriptResult.Segment(segment.startMs, segment.endMs, s, -1f))
                        }
                        out.durationMs = if (maxEnd > 0L) maxEnd else wavDurationMs(audio)
                        if (out.segments.isEmpty()) out.segments.add(TranscriptResult.Segment(0, out.durationMs, text, -1f))
                        WhisperRuntimeState.stage(app, "ready", "Egyptian-English Medium code-switch transcription completed")
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
        fun modelReady(context: Context): Boolean {
            return try {
                context.assets.open(ASSET_MODEL).use { true }
            } catch (_: Exception) {
                false
            }
        }

        private fun ensureBundledModel(context: Context): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create model directory")
            val model = File(dir, MODEL_NAME)
            if (model.exists() && model.length() >= MIN_MODEL_BYTES) return model
            if (model.exists()) model.delete()

            val total = try { context.assets.openFd(ASSET_MODEL).length } catch (_: Exception) { 0L }
            var written = 0L
            WhisperRuntimeState.copyProgress(context, 0L, total)
            context.assets.open(ASSET_MODEL).use { input ->
                FileOutputStream(model, false).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        written += n
                        WhisperRuntimeState.copyProgress(context, written, total)
                    }
                    output.fd.sync()
                }
            }
            if (model.length() < MIN_MODEL_BYTES) {
                model.delete()
                throw IllegalStateException("Bundled Egyptian-English Medium model copy incomplete (${written} bytes)")
            }
            if (total > 0L && model.length() != total) {
                model.delete()
                throw IllegalStateException("Bundled model size mismatch (${written}/${total} bytes)")
            }

            try {
                File(dir, "ggml-egyptian-codeswitch-small-q5_1.bin").delete()
                File(dir, "ggml-small.bin").delete()
                File(dir, "ggml-base.bin").delete()
            } catch (_: Exception) {}
            WhisperRuntimeState.copyProgress(context, model.length(), model.length())
            return model
        }

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
