package com.kareem.cortex.prime.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class WavCodecTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun headerRoundTrip() {
        val pcm = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        val f = tmp.newFile("a.wav")
        f.outputStream().use { WavCodec.writeHeader(it, 16_000, 1, pcm.size * 2); it.write(WavCodec.toBytes(pcm)) }
        assertEquals(44 + pcm.size * 2L, f.length())
        assertArrayEquals(pcm, WavCodec.readPcm16(f))
        assertEquals(16_000, WavCodec.sampleRate(f))
    }

    @Test fun floatsAreInRange() {
        val fl = WavCodec.pcmToFloat(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0))
        assertTrue(fl.all { it in -1f..1f }); assertEquals(0f, fl[2])
    }

    @Test fun durationFromBytes() = assertEquals(1500L, WavCodec.durationMs(dataBytes = 48_000, sampleRate = 16_000))

    @Test fun tooShortRule() {
        assertTrue(isTooShort(499)); assertTrue(!isTooShort(500))
    }
}
