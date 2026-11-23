package com.kareem.cortex.prime.voice

data class EngineResult(val text: String, val language: String?)

interface SpeechEngine {
    suspend fun transcribe(pcm: FloatArray): EngineResult
}
