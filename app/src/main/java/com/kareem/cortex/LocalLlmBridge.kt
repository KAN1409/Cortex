package com.kareem.cortex

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

/** Thin JVM bridge around the prebuilt llama.cpp Android AAR. */
object LocalLlmBridge {
    const val RUNTIME_VERSION = "llama-android 0.1.1 • llama.cpp b9878"

    data class SelfTestResult(
        val ok: Boolean,
        val text: String,
        val systemInfo: String,
        val tokensGenerated: Int,
        val tokensPerSecond: Float,
        val durationMs: Long,
        val error: String,
    )

    data class CompletionResult(
        val text: String,
        val tokensGenerated: Int,
        val tokensPerSecond: Float,
        val durationMs: Long,
        val modelLoadMs: Long,
        val generationMs: Long,
        val cacheHit: Boolean,
    )

    private val cacheLock = Any()
    private var cachedModel: dev.ffmpegkit.llama.LlamaModel? = null
    private var cachedModelPath: String = ""
    private var cachedAtMs: Long = 0L
    private var lastUsedAtMs: Long = 0L

    @JvmStatic
    fun runtimeInfo(): String = Llama.getSystemInfo()

    private fun productionConfig() = LlamaConfig(
        contextSize = LocalBrainConfig.CONTEXT_SIZE,
        threads = LocalBrainConfig.THREADS,
        gpuLayers = 0,
        temperature = LocalBrainConfig.TEMPERATURE,
        topP = LocalBrainConfig.TOP_P,
        topK = LocalBrainConfig.TOP_K,
    )

    @JvmStatic
    fun selfTest(modelPath: String): SelfTestResult = runBlocking {
        val config = LlamaConfig(
            contextSize = 1024,
            threads = LocalBrainConfig.THREADS,
            gpuLayers = 0,
            temperature = 0.0f,
            topP = 0.8f,
            topK = 20,
            seed = 7,
        )
        val started = System.currentTimeMillis()
        var model: dev.ffmpegkit.llama.LlamaModel? = null
        try {
            val info = Llama.getSystemInfo()
            model = Llama.loadModel(modelPath, config)
            val result = Llama.complete(
                model,
                prompt = "/no_think\nReply with exactly CORTEX_LOCAL_OK and nothing else.",
                systemPrompt = "You are a deterministic runtime health check. Output exactly CORTEX_LOCAL_OK. Do not explain.",
                maxTokens = 48,
            )
            val text = result.text.trim()
            val ok = text.contains("CORTEX_LOCAL_OK", ignoreCase = true)
            SelfTestResult(
                ok = ok,
                text = text,
                systemInfo = info,
                tokensGenerated = result.tokensGenerated,
                tokensPerSecond = result.tokensPerSecond,
                durationMs = System.currentTimeMillis() - started,
                error = if (ok) "" else "Unexpected self-test response",
            )
        } catch (t: Throwable) {
            SelfTestResult(
                ok = false,
                text = "",
                systemInfo = try { Llama.getSystemInfo() } catch (_: Throwable) { "" },
                tokensGenerated = 0,
                tokensPerSecond = 0f,
                durationMs = System.currentTimeMillis() - started,
                error = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: ""),
            )
        } finally {
            model?.let {
                try { Llama.releaseModel(it) } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Production completion path. One model handle is shared and every call is serialized. This is
     * the process-wide single inference queue boundary: no parallel Qwen contexts may be active.
     */
    @JvmStatic
    fun completeCached(modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int): CompletionResult = synchronized(cacheLock) {
        runBlocking {
            val config = productionConfig()
            val totalStarted = System.currentTimeMillis()
            var loadMs = 0L
            var hit = cachedModel != null && cachedModelPath == modelPath
            if (!hit) {
                cachedModel?.let { try { Llama.releaseModel(it) } catch (_: Throwable) {} }
                cachedModel = null
                cachedModelPath = ""
                val loadStarted = System.currentTimeMillis()
                cachedModel = Llama.loadModel(modelPath, config)
                loadMs = System.currentTimeMillis() - loadStarted
                cachedModelPath = modelPath
                cachedAtMs = System.currentTimeMillis()
                hit = false
            }
            val model = cachedModel ?: throw IllegalStateException("Local model cache is empty after load")
            lastUsedAtMs = System.currentTimeMillis()
            val generationStarted = System.currentTimeMillis()
            val result = Llama.complete(model, prompt, systemPrompt, min(LocalBrainConfig.MAX_OUTPUT_TOKENS, max(1, maxTokens)))
            val generationMs = System.currentTimeMillis() - generationStarted
            lastUsedAtMs = System.currentTimeMillis()
            CompletionResult(
                text = result.text.trim(),
                tokensGenerated = result.tokensGenerated,
                tokensPerSecond = result.tokensPerSecond,
                durationMs = System.currentTimeMillis() - totalStarted,
                modelLoadMs = loadMs,
                generationMs = generationMs,
                cacheHit = hit,
            )
        }
    }

    /** Compatibility path retained for diagnostics that explicitly need a cold one-shot run. */
    @JvmStatic
    fun completeOnce(modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int): CompletionResult = runBlocking {
        val config = productionConfig()
        val started = System.currentTimeMillis()
        var model: dev.ffmpegkit.llama.LlamaModel? = null
        var loadMs = 0L
        try {
            val loadStarted = System.currentTimeMillis()
            model = Llama.loadModel(modelPath, config)
            loadMs = System.currentTimeMillis() - loadStarted
            val generationStarted = System.currentTimeMillis()
            val result = Llama.complete(model, prompt, systemPrompt, min(LocalBrainConfig.MAX_OUTPUT_TOKENS, max(1, maxTokens)))
            val generationMs = System.currentTimeMillis() - generationStarted
            CompletionResult(result.text.trim(), result.tokensGenerated, result.tokensPerSecond, System.currentTimeMillis() - started, loadMs, generationMs, false)
        } finally {
            model?.let { try { Llama.releaseModel(it) } catch (_: Throwable) {} }
        }
    }

    @JvmStatic
    fun releaseCached() = synchronized(cacheLock) {
        cachedModel?.let { try { Llama.releaseModel(it) } catch (_: Throwable) {} }
        cachedModel = null
        cachedModelPath = ""
        cachedAtMs = 0L
        lastUsedAtMs = 0L
    }

    /** Release only after the requested warm-idle horizon; memory pressure decides when to call it. */
    @JvmStatic
    fun releaseCachedIfIdle(idleMs: Long): Boolean = synchronized(cacheLock) {
        if (cachedModel == null) return@synchronized false
        val anchor = max(cachedAtMs, lastUsedAtMs)
        if (anchor <= 0L || System.currentTimeMillis() - anchor < max(0L, idleMs)) return@synchronized false
        cachedModel?.let { try { Llama.releaseModel(it) } catch (_: Throwable) {} }
        cachedModel = null
        cachedModelPath = ""
        cachedAtMs = 0L
        lastUsedAtMs = 0L
        true
    }

    @JvmStatic
    fun cachedModelAgeMs(): Long = synchronized(cacheLock) {
        if (cachedModel == null || cachedAtMs <= 0L) 0L else max(0L, System.currentTimeMillis() - cachedAtMs)
    }

    @JvmStatic
    fun cachedIdleMs(): Long = synchronized(cacheLock) {
        if (cachedModel == null || lastUsedAtMs <= 0L) 0L else max(0L, System.currentTimeMillis() - lastUsedAtMs)
    }
}
