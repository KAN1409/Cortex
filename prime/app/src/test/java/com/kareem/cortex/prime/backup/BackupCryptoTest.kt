package com.kareem.cortex.prime.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {
    private val data = "cortex memories · ذكريات".toByteArray() + ByteArray(100_000) { (it % 7).toByte() }

    @Test fun roundTrip() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(ByteArrayInputStream(data), out, "correct horse".toCharArray())
        val back = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(out.toByteArray()), back, "correct horse".toCharArray())
        assertArrayEquals(data, back.toByteArray())
    }

    @Test fun wrongPassphraseIsRejected() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(ByteArrayInputStream(data), out, "right".toCharArray())
        val r = runCatching { BackupCrypto.decrypt(ByteArrayInputStream(out.toByteArray()), ByteArrayOutputStream(), "wrong".toCharArray()) }
        assertTrue(r.exceptionOrNull() is BadPassphrase)
    }

    @Test fun notABackupIsRejected() {
        val r = runCatching { BackupCrypto.decrypt(ByteArrayInputStream("hello".toByteArray()), ByteArrayOutputStream(), "x".toCharArray()) }
        assertTrue(r.exceptionOrNull() is NotABackup)
    }
}
