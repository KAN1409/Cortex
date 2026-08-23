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
    )

    @JvmStatic
    fun runtimeInfo(): String = Llama.getSystemInfo()

    @JvmStatic
    fun selfTest(modelPath: String): SelfTestResult = runBlocking {
        val threads = min(4, max(2, Runtime.getRuntime().availableProcessors() - 2))
        val config = LlamaConfig(
            contextSize = 1024,
            threads = threads,
            gpuLayers = 0,
            temperature = 0.0f,
            topP = 0.9f,
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
                prompt = "Reply with exactly CORTEX_LOCAL_OK and nothing else. /no_think",
                systemPrompt = "You are a deterministic runtime health check. Output exactly CORTEX_LOCAL_OK. Do not explain. /no_think",
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

    @JvmStatic
    fun completeOnce(modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int): CompletionResult = runBlocking {
        val threads = min(4, max(2, Runtime.getRuntime().availableProcessors() - 2))
        val config = LlamaConfig(contextSize = 3072, threads = threads, gpuLayers = 0, temperature = 0.25f, topP = 0.9f, topK = 40)
        val started = System.currentTimeMillis()
        var model: dev.ffmpegkit.llama.LlamaModel? = null
        try {
            model = Llama.loadModel(modelPath, config)
            val result = Llama.complete(model, prompt, systemPrompt, maxTokens)
            CompletionResult(result.text.trim(), result.tokensGenerated, result.tokensPerSecond, System.currentTimeMillis() - started)
        } finally {
            model?.let { try { Llama.releaseModel(it) } catch (_: Throwable) {} }
        }
    }
}
