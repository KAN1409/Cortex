package com.kareem.cortex.prime.capture.voice;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Durable staging file. Process death never rewrites or silently discards captured PCM. */
public final class PendingVoiceFile {
    private static final String DIR = "voice-pending";
    private static final String PREFIX = "voice-";
    private static final String SUFFIX = ".wav";

    private PendingVoiceFile() {}

    public static File create(Context context, long startedAtEpochMs) throws IOException {
        File directory = directory(context);
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create voice staging directory");
        File file = new File(directory, PREFIX + startedAtEpochMs + SUFFIX);
        WavPcm16.initialize(file);
        return file;
    }

    public static File[] list(Context context) {
        File[] files = directory(context).listFiles(file -> file.isFile()
                && file.getName().startsWith(PREFIX)
                && file.getName().endsWith(SUFFIX));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        return files;
    }

    public static long occurredAtEpochMs(File file) {
        String name = file.getName();
        try {
            return Long.parseLong(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
        } catch (RuntimeException ignored) {
            return Math.max(0L, file.lastModified());
        }
    }

    private static File directory(Context context) {
        return new File(context.getFilesDir(), DIR);
    }
}
