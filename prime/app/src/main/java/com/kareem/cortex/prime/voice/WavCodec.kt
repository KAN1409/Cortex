package com.kareem.cortex.prime.voice

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val MIN_RECORDING_MS = 500L
fun isTooShort(durationMs: Long): Boolean = durationMs < MIN_RECORDING_MS

/** Minimal PCM16 WAV (RIFF) read/write; originals are kept as 16 kHz mono WAV. */
object WavCodec {
    fun writeHeader(out: OutputStream, sampleRate: Int, channels: Int, dataBytes: Int) {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate)
            .putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(dataBytes)
        out.write(b.array())
    }

    /** Rewrites the size fields once recording has finished. */
    fun patchSizes(file: File, dataBytes: Int) = RandomAccessFile(file, "rw").use { f ->
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        f.seek(4); f.write(b.putInt(0, 36 + dataBytes).array())
        f.seek(40); f.write(b.putInt(0, dataBytes).array())
    }

    fun toBytes(pcm: ShortArray): ByteArray {
        val b = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { b.putShort(it) }
        return b.array()
    }

    fun readPcm16(file: File): ShortArray {
        val bytes = file.readBytes()
        val sb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(sb.remaining()).also { sb.get(it) }
    }

    fun sampleRate(file: File): Int = RandomAccessFile(file, "r").use { f ->
        val b = ByteArray(4); f.seek(24); f.readFully(b); ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun pcmToFloat(pcm: ShortArray): FloatArray = FloatArray(pcm.size) { (pcm[it] / 32768f).coerceIn(-1f, 1f) }

    fun durationMs(dataBytes: Long, sampleRate: Int): Long = dataBytes * 1000L / (sampleRate * 2L)
}
