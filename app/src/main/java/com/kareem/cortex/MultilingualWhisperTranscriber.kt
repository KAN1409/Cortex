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
import java.net.HttpURLConnection
import java.net.URL

/**
 * Primary Cortex voice ASR for Arabic/English code-switching.
 * One multilingual Whisper hypothesis is allowed to emit Arabic and Latin tokens
 * in the same sentence; no utterance-level language lock and no translation.
 */
class MultilingualWhisperTranscriber private constructor() {
    interface Callback {
        fun ok(result: TranscriptResult)
        fun fail(error: Exception)
    }

    companion object {
        private const val MODEL_NAME = "ggml-base.bin"
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        private const val MIN_MODEL_BYTES = 100_000_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var downloading = false

        @JvmStatic
        fun transcribe(context: Context, audio: File, callback: Callback) {
            val app = context.applicationContext
            scope.launch {
                try {
                    if (!audio.exists()) throw IllegalArgumentException("Audio file not found")
                    if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
                        throw UnsupportedOperationException("Whisper build requires arm64-v8a")
                    }
                    if (!audio.name.lowercase().endsWith(".wav")) {
                        throw UnsupportedOperationException("Whisper primary path currently expects Cortex WAV audio")
                    }

                    val modelFile = ensureModel(app)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        val config = WhisperConfig(
                            language = "auto",
                            translate = false,
                            threads = chooseThreads(),
                            maxSegmentLength = 0,
                            printTimestamps = true,
                        )
                        val whisper = Whisper.transcribe(model, audio.absolutePath, config)
                        val text = whisper.text.trim()
                        if (text.isEmpty()) throw IllegalStateException("Whisper returned an empty transcript")

                        val out = TranscriptResult()
                        out.text = text
                        out.language = "auto-multilingual"
                        out.engine = "whisper_cpp_base_multilingual_auto"
                        out.version = "2"
                        var maxEnd = 0L
                        for (segment in whisper.segments) {
                            val s = segment.text.trim()
                            if (s.isEmpty()) continue
                            maxEnd = maxOf(maxEnd, segment.endMs)
                            out.segments.add(TranscriptResult.Segment(segment.startMs, segment.endMs, s, -1f))
                        }
                        out.durationMs = if (maxEnd > 0L) maxEnd else wavDurationMs(audio)
                        if (out.segments.isEmpty()) {
                            out.segments.add(TranscriptResult.Segment(0, out.durationMs, text, -1f))
                        }
                        callback.ok(out)
                    } finally {
                        Whisper.releaseModel(model)
                    }
                } catch (e: Exception) {
                    callback.fail(e)
                }
            }
        }

        @JvmStatic
        fun modelReady(context: Context): Boolean {
            val f = File(File(context.filesDir, "models"), MODEL_NAME)
            return f.exists() && f.length() >= MIN_MODEL_BYTES
        }

        @Synchronized
        private fun markDownloadStart(): Boolean {
            if (downloading) return false
            downloading = true
            return true
        }

        @Synchronized
        private fun markDownloadEnd() {
            downloading = false
        }

        private fun ensureModel(context: Context): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create model directory")
            val model = File(dir, MODEL_NAME)
            if (model.exists() && model.length() >= MIN_MODEL_BYTES) return model

            // Only one downloader per process. Other callers wait for the same file.
            if (!markDownloadStart()) {
                val deadline = System.currentTimeMillis() + 10 * 60_000L
                while (System.currentTimeMillis() < deadline) {
                    if (model.exists() && model.length() >= MIN_MODEL_BYTES) return model
                    Thread.sleep(500)
                }
                throw IllegalStateException("Timed out waiting for multilingual model download")
            }

            val tmp = File(dir, "$MODEL_NAME.download")
            try {
                if (tmp.exists()) tmp.delete()
                download(MODEL_URL, tmp)
                if (tmp.length() < MIN_MODEL_BYTES) {
                    throw IllegalStateException("Downloaded Whisper model is incomplete (${tmp.length()} bytes)")
                }
                if (model.exists()) model.delete()
                if (!tmp.renameTo(model)) {
                    tmp.inputStream().use { input -> model.outputStream().use { output -> input.copyTo(output) } }
                    tmp.delete()
                }
                return model
            } finally {
                markDownloadEnd()
            }
        }

        private fun download(url: String, out: File) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("User-Agent", "Cortex/1.0.7 Android")
                connection.setRequestProperty("Accept", "application/octet-stream")
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Model download HTTP $code")
                connection.inputStream.use { input ->
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var n: Int
                        while (input.read(buffer).also { n = it } > 0) output.write(buffer, 0, n)
                        output.fd.sync()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }

        private fun chooseThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            return cores.coerceIn(2, 6)
        }

        private fun wavDurationMs(file: File): Long {
            // Cortex recordings are 16 kHz, mono, PCM16: 32,000 payload bytes/sec.
            val payload = (file.length() - 44L).coerceAtLeast(0L)
            return payload * 1000L / 32_000L
        }
    }
}
