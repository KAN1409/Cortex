package com.kareem.cortex.prime.data.evidence

import java.io.File
import java.io.InputStream

const val MAX_CAPTURE_BYTES: Long = 200L * 1024 * 1024

class TooLargeException : Exception("too large")

data class StoredFile(val file: File, val sha256: String, val sizeBytes: Long)

class OriginalStore(val root: File) {
    fun write(open: () -> InputStream, maxBytes: Long = MAX_CAPTURE_BYTES): StoredFile = TODO()
}
