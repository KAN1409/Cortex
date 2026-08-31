package com.kareem.cortex.prime.capture.voice;

import android.content.Context;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Finalizes one recoverable WAV staging file into immutable evidence + immutable asset bytes. */
public final class VoiceEvidenceCommitter {
    private static final String ASSET_DIR = "evidence-assets/audio";

    private VoiceEvidenceCommitter() {}

    public static EvidenceRecord commit(Context context, File staging, long occurredAtEpochMs) throws IOException {
        WavPcm16.patchHeader(staging);
        String assetSha = sha256File(staging);
        File assetDirectory = new File(context.getFilesDir(), ASSET_DIR);
        if (!assetDirectory.exists() && !assetDirectory.mkdirs()) throw new IOException("Cannot create evidence asset directory");
        File asset = new File(assetDirectory, assetSha + ".wav");
        if (!asset.exists()) copyAtomically(staging, asset);

        long durationMs = WavPcm16.durationMs(staging);
        String evidenceId = "ev_voice_" + sha256Text(occurredAtEpochMs + "\n" + assetSha).substring(0, 24);
        String relativePath = ASSET_DIR + "/" + asset.getName();
        String payload = "{"
                + "\"mimeType\":\"audio/wav\","
                + "\"assetSha256\":\"" + assetSha + "\","
                + "\"assetPath\":\"" + relativePath + "\","
                + "\"durationMs\":" + durationMs + ","
                + "\"sampleRate\":" + WavPcm16.SAMPLE_RATE + ","
                + "\"channels\":" + WavPcm16.CHANNELS + ","
                + "\"bitsPerSample\":" + WavPcm16.BITS_PER_SAMPLE
                + "}";
        EvidenceRecord record = new EvidenceRecord(
                evidenceId,
                EvidenceSource.VOICE,
                occurredAtEpochMs,
                "",
                "asset://sha256/" + assetSha,
                payload
        );

        try (EvidenceSqliteStore store = new EvidenceSqliteStore(context)) {
            store.append(record);
        }
        if (!staging.delete() && staging.exists()) staging.deleteOnExit();
        return record;
    }

    public static void recoverPending(Context context) {
        for (File file : PendingVoiceFile.list(context)) {
            if (!WavPcm16.hasAudio(file)) continue;
            try {
                commit(context, file, PendingVoiceFile.occurredAtEpochMs(file));
            } catch (IOException | RuntimeException ignored) {
                // Leave staging untouched for the next recovery pass.
            }
        }
    }

    private static void copyAtomically(File source, File target) throws IOException {
        File part = new File(target.getParentFile(), target.getName() + ".part");
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(part)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
        if (!part.renameTo(target)) {
            if (target.exists()) {
                part.delete();
                return;
            }
            throw new IOException("Cannot finalize voice evidence asset");
        }
    }

    private static String sha256File(File file) throws IOException {
        MessageDigest digest = sha256();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String sha256Text(String value) {
        MessageDigest digest = sha256();
        return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
