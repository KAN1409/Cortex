package com.kareem.cortex.prime.capture.voice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Deterministic 16 kHz mono PCM16 WAV staging format. */
public final class WavPcm16 {
    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int HEADER_BYTES = 44;

    private WavPcm16() {}

    public static void initialize(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0L);
            raf.write(new byte[HEADER_BYTES]);
        }
    }

    public static boolean hasAudio(File file) {
        return file != null && file.isFile() && file.length() > HEADER_BYTES;
    }

    public static long durationMs(File file) {
        long pcmBytes = Math.max(0L, file.length() - HEADER_BYTES);
        return (pcmBytes * 1000L) / (SAMPLE_RATE * BYTES_PER_SAMPLE);
    }

    public static void patchHeader(File file) throws IOException {
        if (!hasAudio(file)) throw new IOException("WAV staging file has no PCM data");
        long dataSizeLong = file.length() - HEADER_BYTES;
        if (dataSizeLong > 0xffff_ffffL - 36L) throw new IOException("WAV is too large");
        int dataSize = (int) dataSizeLong;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0L);
            raf.writeBytes("RIFF");
            writeLeInt(raf, 36 + dataSize);
            raf.writeBytes("WAVE");
            raf.writeBytes("fmt ");
            writeLeInt(raf, 16);
            writeLeShort(raf, 1);
            writeLeShort(raf, CHANNELS);
            writeLeInt(raf, SAMPLE_RATE);
            writeLeInt(raf, SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS);
            writeLeShort(raf, BYTES_PER_SAMPLE * CHANNELS);
            writeLeShort(raf, BITS_PER_SAMPLE);
            raf.writeBytes("data");
            writeLeInt(raf, dataSize);
        }
    }

    private static void writeLeInt(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xff);
        raf.write((value >>> 8) & 0xff);
        raf.write((value >>> 16) & 0xff);
        raf.write((value >>> 24) & 0xff);
    }

    private static void writeLeShort(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xff);
        raf.write((value >>> 8) & 0xff);
    }
}
