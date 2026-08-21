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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Primary Cortex voice ASR for Arabic/English code-switching.
 * Uses the Whisper SMALL multilingual model so one hypothesis can emit
 * Arabic and Latin tokens in the same utterance without forced translation.
 */
class MultilingualWhisperTranscriber private constructor() {
    interface Callback {
        fun ok(result: TranscriptResult)
        fun fail(error: Exception)
    }

    companion object {
        private const val MODEL_NAME = "ggml-small.bin"
        private const val OLD_MODEL_NAME = "ggml-base.bin"
        private const val EXPECTED_SHA1 = "55356645c2b361a969dfd0ef2c5a50d530afd8d5"
        private const val MIN_MODEL_BYTES = 480_000_000L
        private val MODEL_URLS = arrayOf(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin?download=true",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        )
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var downloading = false
        @Volatile private var validatedFile: String? = null
        @Volatile private var validatedLength: Long = -1L
        @Volatile private var validatedModified: Long = -1L

        @JvmStatic
        fun transcribe(context: Context, audio: File, callback: Callback) {
            val app = context.applicationContext
            scope.launch {
                try {
                    if (!audio.exists()) throw IllegalArgumentException("Audio file not found")
                    if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
                        throw UnsupportedOperationException("Whisper build requires arm64-v8a")
                    }
                    if (!audio.name.lowercase(Locale.US).endsWith(".wav")) {
                        throw UnsupportedOperationException("Whisper primary path expects Cortex WAV audio")
                    }

                    WhisperRuntimeState.stage(app, "checking model", "Validating Whisper small multilingual model")
                    val modelFile = ensureModel(app)
                    WhisperRuntimeState.stage(app, "loading model", modelFile.name)
                    val model = Whisper.loadModel(app, modelFile.absolutePath)
                    try {
                        WhisperRuntimeState.stage(app, "transcribing", "small multilingual • language=auto • translate=false")
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
                        out.engine = "whisper_cpp_small_multilingual_auto"
                        out.version = "4"
                        var maxEnd = 0L
                        for (segment in whisper.segments) {
                            val s = segment.text.trim()
                            if (s.isEmpty()) continue
                            maxEnd = maxOf(maxEnd, segment.endMs)
                            out.segments.add(TranscriptResult.Segment(segment.startMs, segment.endMs, s, -1f))
                        }
                        out.durationMs = if (maxEnd > 0L) maxEnd else wavDurationMs(audio)
                        if (out.segments.isEmpty()) out.segments.add(TranscriptResult.Segment(0, out.durationMs, text, -1f))
                        WhisperRuntimeState.stage(app, "ready", "Whisper small multilingual transcription completed")
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
            val f = File(File(context.filesDir, "models"), MODEL_NAME)
            return f.exists() && f.length() >= MIN_MODEL_BYTES
        }

        @Synchronized private fun markDownloadStart(): Boolean {
            if (downloading) return false
            downloading = true
            return true
        }
        @Synchronized private fun markDownloadEnd() { downloading = false }

        private fun ensureModel(context: Context): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create model directory")
            val model = File(dir, MODEL_NAME)

            if (model.exists()) {
                if (isValidated(model) || (model.length() >= MIN_MODEL_BYTES && verifyModel(context, model))) {
                    cleanupOldBase(dir)
                    return model
                }
                WhisperRuntimeState.stage(context, "invalid model", "Existing small model failed SHA-1 validation; downloading a clean copy")
                model.delete()
                clearValidation()
            }

            if (!markDownloadStart()) {
                val deadline = System.currentTimeMillis() + 30 * 60_000L
                while (System.currentTimeMillis() < deadline) {
                    if (model.exists() && model.length() >= MIN_MODEL_BYTES && verifyModel(context, model)) {
                        cleanupOldBase(dir)
                        return model
                    }
                    Thread.sleep(700)
                }
                throw IllegalStateException("Timed out waiting for Whisper small multilingual model download")
            }

            val tmp = File(dir, "$MODEL_NAME.download")
            try {
                var last: Exception? = null
                for ((index, url) in MODEL_URLS.withIndex()) {
                    try {
                        if (index > 0 && tmp.exists()) tmp.delete()
                        downloadResumable(context, url, tmp)
                        WhisperRuntimeState.stage(context, "verifying model", String.format(Locale.US, "%.1f MB downloaded", tmp.length()/1048576.0))
                        if (tmp.length() < MIN_MODEL_BYTES) throw IllegalStateException("Whisper small model download incomplete (${tmp.length()} bytes)")
                        if (!sha1(tmp).equals(EXPECTED_SHA1, ignoreCase = true)) {
                            throw IllegalStateException("Whisper small model checksum mismatch")
                        }
                        if (model.exists()) model.delete()
                        if (!tmp.renameTo(model)) {
                            tmp.inputStream().use { input -> model.outputStream().use { output -> input.copyTo(output) } }
                            tmp.delete()
                        }
                        rememberValidation(model)
                        cleanupOldBase(dir)
                        WhisperRuntimeState.stage(context, "model ready", String.format(Locale.US, "%.1f MB • small multilingual • SHA-1 verified", model.length()/1048576.0))
                        return model
                    } catch (e: Exception) {
                        last = e
                        WhisperRuntimeState.stage(context, "download retry", e.javaClass.simpleName+": "+(e.message ?: "null"))
                    }
                }
                throw IllegalStateException("Could not obtain a valid Whisper small multilingual model", last)
            } finally {
                markDownloadEnd()
            }
        }

        private fun verifyModel(context: Context, f: File): Boolean {
            return try {
                WhisperRuntimeState.stage(context, "verifying model", String.format(Locale.US, "%.1f MB", f.length()/1048576.0))
                val ok = sha1(f).equals(EXPECTED_SHA1, ignoreCase = true)
                if (ok) rememberValidation(f)
                ok
            } catch (_: Exception) { false }
        }

        private fun cleanupOldBase(dir: File) {
            try {
                val old = File(dir, OLD_MODEL_NAME)
                if (old.exists()) old.delete()
                val oldTmp = File(dir, "$OLD_MODEL_NAME.download")
                if (oldTmp.exists()) oldTmp.delete()
            } catch (_: Exception) {}
        }

        private fun isValidated(f: File): Boolean =
            validatedFile == f.absolutePath && validatedLength == f.length() && validatedModified == f.lastModified()
        private fun rememberValidation(f: File){validatedFile=f.absolutePath;validatedLength=f.length();validatedModified=f.lastModified()}
        private fun clearValidation(){validatedFile=null;validatedLength=-1;validatedModified=-1}

        private fun downloadResumable(context: Context, initialUrl: String, out: File) {
            var current = URL(initialUrl)
            var redirects = 0
            while (true) {
                val existing = if (out.exists()) out.length() else 0L
                var c: HttpURLConnection? = null
                try {
                    c = current.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 30_000
                    c.readTimeout = 120_000
                    c.setRequestProperty("User-Agent", "Cortex/1.0.10 Android")
                    c.setRequestProperty("Accept", "application/octet-stream")
                    if (existing > 0) c.setRequestProperty("Range", "bytes=$existing-")
                    c.connect()
                    val code = c.responseCode
                    if (code in intArrayOf(301,302,303,307,308)) {
                        val location = c.getHeaderField("Location") ?: throw IllegalStateException("Model redirect missing Location")
                        if (++redirects > 8) throw IllegalStateException("Too many model download redirects")
                        current = URL(current, location)
                        continue
                    }
                    if (code == 416 && out.exists()) return
                    if (code != 200 && code != 206) throw IllegalStateException("Model download HTTP $code")
                    val append = code == 206 && existing > 0
                    if (!append && out.exists()) out.delete()
                    var written = if (append) existing else 0L
                    var nextReport = written + 8L*1024L*1024L
                    c.inputStream.use { input ->
                        FileOutputStream(out, append).use { output ->
                            val buffer = ByteArray(1024*1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n <= 0) break
                                output.write(buffer,0,n)
                                written += n
                                if (written >= nextReport) {
                                    WhisperRuntimeState.stage(context, "downloading model", String.format(Locale.US, "%.1f MB downloaded", written/1048576.0))
                                    nextReport = written + 8L*1024L*1024L
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    return
                } finally { c?.disconnect() }
            }
        }

        private fun sha1(file: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { input ->
                val buf = ByteArray(1024*1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf,0,n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
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
