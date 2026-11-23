package com.kareem.cortex.prime.vision

data class OcrOutput(val text: String, val language: String?)

interface OcrEngine {
    /** [path] is a content:// URI or an absolute file path. */
    suspend fun read(path: String): OcrOutput
}
