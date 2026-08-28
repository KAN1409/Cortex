package com.kareem.cortex;

import android.database.Cursor;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Recovers still-present raw signals whose old short retention window already elapsed.
 *
 * <p>This only copies rows that physically still exist and fall inside the V4 90-day episodic
 * window. It cannot reconstruct rows already deleted by older Cortex versions.</p>
 */
public final class CognitiveMemoryHistoricalRescueV4 {
    private CognitiveMemoryHistoricalRescueV4() {}

    public static int runBatch(VaultDb db, int limit) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        int max = Math.max(1, Math.min(500, limit));
        long now = System.currentTimeMillis();
        long cutoff = now - CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS;
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT r.id,r.kind,r.source,r.title,r.body,r.metadata_json,r.content_hash,r.occurred_at,r.created_at " +
                        "FROM raw_signals r WHERE r.occurred_at>=? AND r.retention_until>0 AND r.retention_until<? " +
                        "AND NOT EXISTS (SELECT 1 FROM v4_legacy_map m WHERE m.legacy_table='raw_signals' " +
                        "AND m.legacy_id=CAST(r.id AS TEXT) AND m.object_type='EVIDENCE') " +
                        "ORDER BY r.id ASC LIMIT ?",
                new String[]{String.valueOf(cutoff), String.valueOf(now), String.valueOf(max)});
        int mapped = 0;
        try {
            while (c.moveToNext()) {
                try {
                    long legacyId = c.getLong(0);
                    String kind = n(c.getString(1));
                    String source = n(c.getString(2));
                    String title = n(c.getString(3));
                    String body = n(c.getString(4));
                    String metadata = n(c.getString(5));
                    String hash = n(c.getString(6));
                    long occurredAt = c.getLong(7);
                    long capturedAt = c.getLong(8);
                    String original = !body.isEmpty() ? body : title;
                    String normalized = CognitiveIdentityV4.normalizeText(original);
                    if (!looksLikeHash(hash)) hash = Fingerprint.text(title + "\n" + body);
                    String externalId = CognitiveMemoryForwardBridgeV4.externalId(metadata);
                    CognitiveDomainV4.EvidenceSourceType type = CognitiveMemoryBackfillV4.sourceTypeForRaw(kind);
                    String evidenceId = CognitiveIdentityV4.evidenceId(
                            type, source, externalId, hash, normalized, occurredAt);
                    CognitiveDomainV4.Evidence evidence = new CognitiveDomainV4.Evidence(
                            evidenceId,
                            type,
                            occurredAt > 0 ? occurredAt : capturedAt,
                            capturedAt > 0 ? capturedAt : occurredAt,
                            source,
                            externalId,
                            original,
                            normalized,
                            hash,
                            null,
                            CognitiveDomainV4.Sensitivity.NORMAL,
                            CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY,
                            CognitiveDomainV4.ProcessingState.READY);
                    long expiry = Math.max(occurredAt, capturedAt) + CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS;
                    String actual = CognitiveStoreV4.putEvidence(
                            db,
                            evidence,
                            rescueMetadata(legacyId, metadata),
                            expiry);
                    CognitiveStoreV4.mapLegacy(
                            db,
                            "raw_signals",
                            String.valueOf(legacyId),
                            CognitiveDomainV4.CanonicalObjectType.EVIDENCE,
                            actual,
                            "RESCUED");
                    mapped++;
                } catch (Throwable ignored) {}
            }
        } finally {
            c.close();
        }
        return mapped;
    }

    private static String rescueMetadata(long legacyId, String metadata) {
        try {
            JSONObject o = new JSONObject();
            o.put("migration", "v4_historical_rescue_001");
            o.put("legacy_table", "raw_signals");
            o.put("legacy_id", legacyId);
            if (!metadata.isEmpty()) {
                try { o.put("legacy_metadata", new JSONObject(metadata)); }
                catch (Throwable ignored) { o.put("legacy_metadata_text", metadata); }
            }
            return o.toString();
        } catch (Throwable ignored) {
            return "{\"migration\":\"v4_historical_rescue_001\",\"legacy_id\":" + legacyId + "}";
        }
    }

    private static boolean looksLikeHash(String value) {
        return n(value).toLowerCase(Locale.ROOT).matches("[0-9a-f]{16,128}");
    }

    private static String n(String value) { return value == null ? "" : value.trim(); }
}
