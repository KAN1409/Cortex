package com.kareem.cortex.prime.capture.voice;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WavPcm16Test {
    @Test
    public void patchesValid16kMonoPcm16HeaderAndDuration() throws Exception {
        File file = File.createTempFile("prime-voice-", ".wav");
        try {
            WavPcm16.initialize(file);
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(WavPcm16.HEADER_BYTES);
                raf.write(new byte[WavPcm16.SAMPLE_RATE * WavPcm16.BYTES_PER_SAMPLE]);
            }
            WavPcm16.patchHeader(file);
            byte[] header = new byte[44];
            try (FileInputStream in = new FileInputStream(file)) {
                assertEquals(44, in.read(header));
            }
            assertEquals("RIFF", new String(header, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WAVE", new String(header, 8, 4, StandardCharsets.US_ASCII));
            assertEquals("fmt ", new String(header, 12, 4, StandardCharsets.US_ASCII));
            assertEquals("data", new String(header, 36, 4, StandardCharsets.US_ASCII));
            assertEquals(1000L, WavPcm16.durationMs(file));
            assertTrue(WavPcm16.hasAudio(file));
        } finally {
            file.delete();
        }
    }
}
