package com.kareem.cortex.prime.voice

import java.io.File
import java.io.OutputStream

const val MIN_RECORDING_MS = 500L
fun isTooShort(durationMs: Long): Boolean = TODO()

object WavCodec {
    fun writeHeader(out: OutputStream, sampleRate: Int, channels: Int, dataBytes: Int): Unit = TODO()
    fun toBytes(pcm: ShortArray): ByteArray = TODO()
    fun readPcm16(file: File): ShortArray = TODO()
    fun sampleRate(file: File): Int = TODO()
    fun pcmToFloat(pcm: ShortArray): FloatArray = TODO()
    fun durationMs(dataBytes: Long, sampleRate: Int): Long = TODO()
}
