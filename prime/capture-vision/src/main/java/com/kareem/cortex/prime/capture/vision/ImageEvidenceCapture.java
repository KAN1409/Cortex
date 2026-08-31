package com.kareem.cortex.prime.capture.vision;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * Vision intake boundary. The source image is committed before OCR is allowed to run.
 */
public final class ImageEvidenceCapture {
    public static final class Outcome {
        public final String evidenceId;
        public final String sha256;
        public final long byteSize;
        public final int width;
        public final int height;

        Outcome(String evidenceId, String sha256, long byteSize, int width, int height) {
            this.evidenceId = evidenceId;
            this.sha256 = sha256;
            this.byteSize = byteSize;
            this.width = width;
            this.height = height;
        }
    }

    private ImageEvidenceCapture() {}

    public static Outcome captureSharedImage(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("Missing shared image");

        ContentAddressedImageStore.StoredImage stored = ContentAddressedImageStore.importUri(context, uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(stored.file.getAbsolutePath(), bounds);

        String mimeType = context.getContentResolver().getType(uri);
        String displayName = resolveDisplayName(context, uri);
        long capturedAt = System.currentTimeMillis();
        String evidenceId = "ev_image_" + UUID.randomUUID().toString().replace("-", "");
        String relativeAssetPath = "prime-assets/images/" + stored.sha256 + ".bin";

        JSONObject payload = new JSONObject();
        try {
            payload.put("schema", "CORTEX_PRIME_IMAGE_EVIDENCE_V1");
            payload.put("sha256", stored.sha256);
            payload.put("asset_path", relativeAssetPath);
            payload.put("byte_size", stored.byteSize);
            payload.put("mime_type", mimeType == null ? JSONObject.NULL : mimeType);
            payload.put("display_name", displayName == null ? JSONObject.NULL : displayName);
            payload.put("width", bounds.outWidth);
            payload.put("height", bounds.outHeight);
            payload.put("source_uri_scheme", uri.getScheme() == null ? JSONObject.NULL : uri.getScheme());
            payload.put("capture_kind", "shared_image");
            payload.put("analysis_status", "PENDING_DERIVED");
        } catch (JSONException jsonFailure) {
            throw new IOException("Unable to encode image evidence metadata", jsonFailure);
        }

        EvidenceRecord record = new EvidenceRecord(
                evidenceId,
                EvidenceSource.IMAGE,
                capturedAt,
                "",
                "asset:image:" + stored.sha256,
                payload.toString()
        );

        try (EvidenceSqliteStore evidenceStore = new EvidenceSqliteStore(context)) {
            if (!evidenceStore.append(record)) {
                throw new IOException("Unable to persist image evidence");
            }
        }

        // Perception is strictly downstream of immutable source commit.
        ImagePerceptionProcessor.analyze(context.getApplicationContext(), record, stored.file);

        return new Outcome(evidenceId, stored.sha256, stored.byteSize, bounds.outWidth, bounds.outHeight);
    }

    private static String resolveDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {
            // Provenance must not depend on optional provider metadata.
        }
        return null;
    }
}
