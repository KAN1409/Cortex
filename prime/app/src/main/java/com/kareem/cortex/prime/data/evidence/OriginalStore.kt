package com.kareem.cortex.prime.data.evidence

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

const val MAX_CAPTURE_BYTES: Long = 200L * 1024 * 1024

class TooLargeException : Exception("too large")

data class StoredFile(val file: File, val sha256: String, val sizeBytes: Long)

/** Streams originals to disk (never into memory), named by their SHA-256. */
class OriginalStore(val root: File) {
    private val originals = File(root, "originals")
    private val tmpDir = File(root, "tmp")

    fun write(open: () -> InputStream, maxBytes: Long = MAX_CAPTURE_BYTES): StoredFile {
        tmpDir.mkdirs()
        val tmp = File.createTempFile("capture", ".part", tmpDir)
        try {
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            open().use { input ->
                tmp.outputStream().buffered().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > maxBytes) throw TooLargeException()
                        md.update(buf, 0, n); out.write(buf, 0, n)
                    }
                }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it) }
            originals.mkdirs()
            val dest = File(originals, sha)
            if (dest.exists()) tmp.delete() else check(tmp.renameTo(dest)) { "could not store original" }
            return StoredFile(dest, sha, total)
        } catch (e: Throwable) {
            tmp.delete()
            if (tmpDir.list().isNullOrEmpty()) tmpDir.delete()
            throw e
        } finally {
            if (tmpDir.list().isNullOrEmpty()) tmpDir.delete()
        }
    }

    fun hashText(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
