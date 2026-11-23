package com.kareem.cortex.prime.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ModelManagerTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun verifyAcceptsRightFileAndRejectsWrong() {
        val bytes = ByteArray(4096) { it.toByte() }
        val f = tmp.newFile("m.bin").apply { writeBytes(bytes) }
        assertTrue(ModelSpec("m.bin", "u", 4096, sha(bytes)).verify(f))
        assertFalse(ModelSpec("m.bin", "u", 4096, sha(byteArrayOf(1))).verify(f))
        assertFalse(ModelSpec("m.bin", "u", 4095, sha(bytes)).verify(f))
    }
}
