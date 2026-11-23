package com.kareem.cortex.prime.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** whisper.cpp through JNI. One context, loaded lazily, reused across notes. */
class WhisperEngine(private val modelFile: File) : SpeechEngine {
    private var handle = 0L

    @Synchronized private fun ctx(): Long {
        if (handle == 0L) handle = nativeInit(modelFile.absolutePath)
        check(handle != 0L) { "voice model could not be loaded" }
        return handle
    }

    override suspend fun transcribe(pcm: FloatArray): EngineResult = withContext(Dispatchers.Default) {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val raw = nativeTranscribe(ctx(), pcm, threads)
        val lang = raw.substringBefore('\n').ifBlank { null }
        EngineResult(raw.substringAfter('\n', ""), lang)
    }

    @Synchronized fun close() { if (handle != 0L) { nativeFree(handle); handle = 0L } }

    private external fun nativeInit(path: String): Long
    private external fun nativeTranscribe(handle: Long, pcm: FloatArray, threads: Int): String
    private external fun nativeFree(handle: Long)

    companion object { init { System.loadLibrary("cortexwhisper") } }
}
