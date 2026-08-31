package com.kareem.cortex.prime.capture.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derived perception stage for immutable image evidence.
 * Raw image bytes and the original IMAGE record are never rewritten.
 */
public final class ImagePerceptionProcessor {
    private ImagePerceptionProcessor() {}

    public static void analyze(Context context, EvidenceRecord parent, File asset) {
        if (context == null || parent == null || asset == null || !asset.isFile()) return;
        Context app = context.getApplicationContext();
        Bitmap bitmap = BitmapFactory.decodeFile(asset.getAbsolutePath());
        if (bitmap == null) {
            appendStatus(app, parent, "FAILED", "IMAGE_DECODE_FAILED");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(result -> {
                    bitmap.recycle();
                    appendOcr(app, parent, result);
                })
                .addOnFailureListener(failure -> {
                    bitmap.recycle();
                    appendStatus(app, parent, "RETRYABLE", failure.getClass().getSimpleName());
                });
    }

    /** Backfills pre-0.5 IMAGE evidence without mutating the original records. */
    public static void recoverUnprocessed(Context context) {
        Context app = context.getApplicationContext();
        List<EvidenceRecord> recent;
        try (EvidenceSqliteStore store = new EvidenceSqliteStore(app)) {
            recent = store.recent(500);
        } catch (RuntimeException failure) {
            return;
        }
        Set<String> completedParents = new HashSet<>();
        List<EvidenceRecord> images = new ArrayList<>();
        for (EvidenceRecord record : recent) {
            String parentId = parentId(record.rawPayloadJson);
            if (!parentId.isEmpty() && record.source == EvidenceSource.OCR) completedParents.add(parentId);
            if (record.source == EvidenceSource.IMAGE) images.add(record);
        }
        for (EvidenceRecord image : images) {
            if (completedParents.contains(image.id)) continue;
            String sha = imageSha(image);
            if (sha.isEmpty()) continue;
            File asset = new File(app.getFilesDir(), "prime-assets/images/" + sha + ".bin");
            if (asset.isFile()) {
                analyze(app, image, asset);
                break; // bounded background work per process start.
            }
        }
    }

    private static void appendOcr(Context context, EvidenceRecord parent, Text result) {
        String text = result == null ? "" : result.getText().trim();
        if (text.isEmpty()) {
            appendStatus(context, parent, "COMPLETE_NO_TEXT", "NO_VISIBLE_TEXT_DETECTED");
            return;
        }
        String id = "ev_ocr_" + sha256(parent.id + "\n" + text).substring(0, 24);
        JSONObject payload = new JSONObject();
        try {
            payload.put("schema", "CORTEX_PRIME_DERIVED_OCR_V1");
            payload.put("status", "COMPLETE");
            payload.put("parent_evidence_id", parent.id);
            payload.put("processor", "MLKIT_TEXT_RECOGNITION_LATIN_V2");
            payload.put("derived", true);
            payload.put("immutable_parent", true);
        } catch (Exception ignored) {}
        append(context, new EvidenceRecord(
                id,
                EvidenceSource.OCR,
                System.currentTimeMillis(),
                text,
                "derived-from:" + parent.id,
                payload.toString()
        ));
    }

    private static void appendStatus(Context context, EvidenceRecord parent, String status, String reason) {
        String normalized = status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT);
        String id = "ev_ocr_status_" + sha256(parent.id + "\n" + normalized + "\n" + reason).substring(0, 20);
        JSONObject payload = new JSONObject();
        try {
            payload.put("schema", "CORTEX_PRIME_DERIVED_OCR_STATUS_V1");
            payload.put("status", normalized);
            payload.put("reason", reason == null ? "" : reason);
            payload.put("parent_evidence_id", parent.id);
            payload.put("derived", true);
            payload.put("immutable_parent", true);
        } catch (Exception ignored) {}
        append(context, new EvidenceRecord(
                id,
                EvidenceSource.OCR,
                System.currentTimeMillis(),
                "OCR " + normalized.replace('_', ' ').toLowerCase(Locale.ROOT),
                "derived-from:" + parent.id,
                payload.toString()
        ));
    }

    private static void append(Context context, EvidenceRecord record) {
        try (EvidenceSqliteStore store = new EvidenceSqliteStore(context)) {
            store.append(record);
        } catch (RuntimeException ignored) {
            // Parent evidence remains authoritative even if perception persistence fails.
        }
    }

    private static String parentId(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            return new JSONObject(payload).optString("parent_evidence_id", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String imageSha(EvidenceRecord record) {
        if (record == null) return "";
        try {
            String value = new JSONObject(record.rawPayloadJson).optString("sha256", "").toLowerCase(Locale.ROOT);
            if (value.matches("[0-9a-f]{64}")) return value;
        } catch (Exception ignored) {}
        String ref = record.sourceRef == null ? "" : record.sourceRef;
        String prefix = "asset:image:";
        if (ref.startsWith(prefix)) {
            String value = ref.substring(prefix.length()).toLowerCase(Locale.ROOT);
            if (value.matches("[0-9a-f]{64}")) return value;
        }
        return "";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
