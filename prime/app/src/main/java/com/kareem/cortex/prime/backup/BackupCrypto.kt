package com.kareem.cortex.prime.backup

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BadPassphrase : Exception("That passphrase doesn't open this backup")
class NotABackup : Exception("That file isn't a Cortex backup")

/** AES-256-GCM, key from the owner's passphrase (PBKDF2-SHA256, 120k rounds). Format: "CXB1" | salt16 | iv12 | ciphertext. */
object BackupCrypto {
    private val MAGIC = "CXB1".toByteArray()
    private const val ROUNDS = 120_000

    private fun key(pass: CharArray, salt: ByteArray) =
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, ROUNDS, 256)).encoded, "AES")

    fun encrypt(input: InputStream, output: OutputStream, pass: CharArray) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes); val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv)) }
        output.write(MAGIC); output.write(salt); output.write(iv)
        val buf = ByteArray(1 shl 16)
        while (true) { val n = input.read(buf); if (n < 0) break; c.update(buf, 0, n)?.let(output::write) }
        output.write(c.doFinal()); output.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream, pass: CharArray) {
        val d = DataInputStream(input)
        val head = ByteArray(4); val salt = ByteArray(16); val iv = ByteArray(12)
        try { d.readFully(head); if (!head.contentEquals(MAGIC)) throw NotABackup(); d.readFully(salt); d.readFully(iv) } catch (e: java.io.EOFException) { throw NotABackup() }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv)) }
        // GCM only authenticates at the end, so decrypt into memory-safe chunks but write only after doFinal succeeds.
        val plain = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        while (true) { val n = d.read(buf); if (n < 0) break; c.update(buf, 0, n)?.let(plain::write) }
        try { plain.write(c.doFinal()) } catch (e: AEADBadTagException) { throw BadPassphrase() }
        plain.writeTo(output); output.flush()
    }
}
