package com.kareem.cortex.prime.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs

sealed interface RecordingResult {
    data class Saved(val file: File, val durationMs: Long) : RecordingResult
    data object TooShort : RecordingResult
}

/** 16 kHz mono PCM16 straight into a WAV file (the original is kept as recorded). */
class AudioRecorder(private val outDir: File) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var level = 0f
    private var bytes = 0L
    private lateinit var file: File

    fun amplitude(): Float = level

    @SuppressLint("MissingPermission") // caller requests RECORD_AUDIO first
    fun start() {
        outDir.mkdirs()
        file = File(outDir, "rec-${System.currentTimeMillis()}.wav")
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
        record = r; bytes = 0; running = true
        r.startRecording()
        worker = thread(name = "cortex-rec") {
            file.outputStream().buffered().use { out ->
                WavCodec.writeHeader(out, RATE, 1, 0)
                val buf = ShortArray(1600)
                while (running) {
                    val n = r.read(buf, 0, buf.size); if (n <= 0) continue
                    var peak = 0; for (i in 0 until n) peak = maxOf(peak, abs(buf[i].toInt()))
                    level = peak / 32768f
                    out.write(WavCodec.toBytes(buf.copyOf(n))); bytes += n * 2L
                }
            }
        }
    }

    fun stop(): RecordingResult {
        running = false
        worker?.join(2000)
        record?.run { runCatching { stop() }; release() }
        record = null
        val duration = WavCodec.durationMs(bytes, RATE)
        if (isTooShort(duration)) { file.delete(); return RecordingResult.TooShort }
        WavCodec.patchSizes(file, bytes.toInt())
        return RecordingResult.Saved(file, duration)
    }

    companion object { const val RATE = 16_000 }
}
