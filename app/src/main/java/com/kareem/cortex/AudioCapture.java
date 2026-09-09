package com.kareem.cortex;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class AudioCapture {
    private static final int RATE = 16000;

    private AudioRecord record;
    private Thread thread;
    private volatile boolean running;
    private RandomAccessFile out;
    private File file;
    private long pcmBytes;

    public boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public File start(Context ctx) throws Exception {
        if (ctx == null) throw new IllegalArgumentException("ctx == null");
        if (running) throw new IllegalStateException("Already recording");
        if (!hasPermission(ctx)) throw new SecurityException("RECORD_AUDIO permission not granted");

        int min = AudioRecord.getMinBufferSize(
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min == AudioRecord.ERROR || min == AudioRecord.ERROR_BAD_VALUE) {
            throw new IOException("Unsupported microphone configuration");
        }
        int buffer = Math.max(min, 8192);

        AudioRecord candidate = null;
        try {
            candidate = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer);
            if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IOException("Microphone initialization failed");
            }

            File dir = new File(ctx.getFilesDir(), "audio");
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Unable to create audio directory");
            }
            File candidateFile = new File(dir, "voice_" + System.currentTimeMillis() + ".wav");
            RandomAccessFile candidateOut = new RandomAccessFile(candidateFile, "rw");
            try {
                writeHeader(candidateOut, 0);
                candidate.startRecording();
            } catch (Throwable t) {
                try { candidateOut.close(); } catch (Throwable ignored) {}
                if (candidateFile.exists()) candidateFile.delete();
                throw t;
            }

            record = candidate;
            out = candidateOut;
            file = candidateFile;
            pcmBytes = 0;
            running = true;

            final AudioRecord activeRecord = record;
            final RandomAccessFile activeOut = out;
            thread = new Thread(() -> {
                byte[] b = new byte[buffer];
                try {
                    while (running) {
                        int n = activeRecord.read(b, 0, b.length);
                        if (n > 0) {
                            activeOut.write(b, 0, n);
                            pcmBytes += n;
                        } else if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE) {
                            running = false;
                        }
                    }
                } catch (Throwable ignored) {
                    running = false;
                }
            }, "CortexVoiceRecorder");
            thread.start();
            return file;
        } catch (SecurityException e) {
            if (candidate != null) try { candidate.release(); } catch (Throwable ignored) {}
            record = null;
            throw e;
        } catch (Throwable t) {
            if (candidate != null) try { candidate.release(); } catch (Throwable ignored) {}
            record = null;
            throw t;
        }
    }

    public File stop() throws Exception {
        if (!running && record == null) return file;
        running = false;

        AudioRecord active = record;
        if (active != null) {
            try { active.stop(); } catch (Throwable ignored) {}
        }
        if (thread != null) {
            try {
                thread.join(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (active != null) {
            try { active.release(); } catch (Throwable ignored) {}
        }
        record = null;

        if (out != null) {
            try {
                out.seek(0);
                writeHeader(out, pcmBytes);
            } finally {
                out.close();
                out = null;
            }
        }
        return file;
    }

    public boolean isRunning() {
        return running;
    }

    private static void writeHeader(RandomAccessFile f, long data) throws IOException {
        int channels = 1, bits = 16;
        long byteRate = RATE * channels * bits / 8L;
        f.writeBytes("RIFF");
        le32(f, 36 + data);
        f.writeBytes("WAVEfmt ");
        le32(f, 16);
        le16(f, 1);
        le16(f, channels);
        le32(f, RATE);
        le32(f, byteRate);
        le16(f, channels * bits / 8L);
        le16(f, bits);
        f.writeBytes("data");
        le32(f, data);
    }

    private static void le16(RandomAccessFile f, long v) throws IOException {
        f.write((int) (v & 255));
        f.write((int) ((v >> 8) & 255));
    }

    private static void le32(RandomAccessFile f, long v) throws IOException {
        f.write((int) (v & 255));
        f.write((int) ((v >> 8) & 255));
        f.write((int) ((v >> 16) & 255));
        f.write((int) ((v >> 24) & 255));
    }
}
