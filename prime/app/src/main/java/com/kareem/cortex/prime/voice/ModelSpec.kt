package com.kareem.cortex.prime.voice

import java.io.File
import java.security.MessageDigest

data class ModelSpec(val fileName: String, val url: String, val sizeBytes: Long, val sha256: String) {
    /** Size first (cheap), then SHA-256 streamed from disk. */
    fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != sizeBytes) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.equals(sha256, ignoreCase = true)
    }
}
