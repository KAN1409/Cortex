package com.kareem.cortex

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Optional on-device Gemini Nano bridge.
 *
 * Contract:
 * - no API key and no user-billed cloud provider;
 * - runtime status is queried before inference;
 * - failures are explicit and always safe to fall back from;
 * - model download is user/device managed through AICore/ML Kit;
 * - no irreversible Android action is executed from model text.
 */
object NanoLocalBrain {
    private const val INFERENCE_TIMEOUT_MS = 45_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class State(
        val status: String,
        val model: String = "",
        val detail: String = ""
    ) {
        val available: Boolean get() = status == "available"
        val downloadable: Boolean get() = status == "downloadable"
        val downloading: Boolean get() = status == "downloading"
        fun label(): String = when (status) {
            "available" -> "Gemini Nano ready" + if (model.isBlank()) "" else " · $model"
            "downloadable" -> "Gemini Nano available · model download required"
            "downloading" -> "Gemini Nano model downloading"
            "unavailable" -> "Gemini Nano unavailable on this runtime"
            else -> "Gemini Nano status error" + if (detail.isBlank()) "" else " · $detail"
        }
    }

    data class Result(
        val ok: Boolean,
        val text: String = "",
        val provider: String = "gemini-nano",
        val model: String = "",
        val error: String = "",
        val durationMs: Long = 0L
    )

    interface PrepareCallback { fun done(state: State) }

    @JvmStatic
    fun statusBlocking(context: Context): State = runBlocking {
        status(context.applicationContext)
    }

    @JvmStatic
    fun generateBlocking(context: Context, prompt: String): Result = runBlocking {
        generate(context.applicationContext, prompt)
    }

    @JvmStatic
    fun prepare(context: Context, callback: PrepareCallback?) {
        val app = context.applicationContext
        scope.launch {
            val state = try {
                val model = Generation.getClient(generationConfig { })
                try {
                    when (model.checkStatus()) {
                        FeatureStatus.AVAILABLE -> stateForAvailable(model)
                        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                            model.download().collect { status ->
                                if (status is DownloadStatus.DownloadFailed) throw status.e
                            }
                            stateForAvailable(model)
                        }
                        else -> State("unavailable")
                    }
                } finally {
                    model.close()
                }
            } catch (t: Throwable) {
                State("error", detail = safe(t))
            }
            withContext(Dispatchers.Main) { callback?.done(state) }
        }
    }

    private suspend fun status(context: Context): State {
        val model = Generation.getClient(generationConfig { })
        return try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> stateForAvailable(model)
                FeatureStatus.DOWNLOADABLE -> State("downloadable")
                FeatureStatus.DOWNLOADING -> State("downloading")
                else -> State("unavailable")
            }
        } catch (t: Throwable) {
            // ML Kit/AICore has historically thrown FEATURE_NOT_FOUND instead of returning
            // UNAVAILABLE on some device/config combinations. Never allow that to crash Cortex.
            State("error", detail = safe(t))
        } finally {
            model.close()
        }
    }

    private suspend fun generate(context: Context, prompt: String): Result {
        val clean = prompt.trim()
        if (clean.isEmpty()) return Result(false, error = "Empty prompt")
        val started = android.os.SystemClock.elapsedRealtime()
        val model = Generation.getClient(generationConfig { })
        return try {
            val status = model.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                return Result(false, error = when (status) {
                    FeatureStatus.DOWNLOADABLE -> "Gemini Nano model is downloadable but not installed"
                    FeatureStatus.DOWNLOADING -> "Gemini Nano model is still downloading"
                    else -> "Gemini Nano is unavailable on this runtime"
                })
            }
            val base = try { model.getBaseModelName() } catch (_: Throwable) { "" }
            val response = withTimeout(INFERENCE_TIMEOUT_MS) { model.generateContent(clean) }
            val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
            if (text.isEmpty()) Result(false, model = base, error = "Gemini Nano returned no text")
            else Result(true, text = text, model = base, durationMs = android.os.SystemClock.elapsedRealtime() - started)
        } catch (t: Throwable) {
            Result(false, error = safe(t), durationMs = android.os.SystemClock.elapsedRealtime() - started)
        } finally {
            model.close()
        }
    }

    private suspend fun stateForAvailable(model: com.google.mlkit.genai.prompt.GenerativeModel): State {
        val base = try { model.getBaseModelName() } catch (_: Throwable) { "" }
        return State("available", base)
    }

    private fun safe(t: Throwable?): String {
        if (t == null) return "unknown error"
        val raw = t.message?.trim().orEmpty()
        val text = if (raw.isEmpty()) t.javaClass.simpleName else "${t.javaClass.simpleName}: $raw"
        return if (text.length <= 260) text else text.substring(0, 260)
    }
}
