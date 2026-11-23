package com.kareem.cortex.prime.voice

import java.io.File

data class ModelSpec(val fileName: String, val url: String, val sizeBytes: Long, val sha256: String) {
    fun verify(file: File): Boolean = TODO()
}
