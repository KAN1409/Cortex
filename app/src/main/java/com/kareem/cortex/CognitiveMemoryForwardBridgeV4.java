package com.kareem.cortex;

import org.json.JSONObject;

/**
 * Lightweight forward-write bridge for the V4 episodic evidence layer.
 *
 * <p>This is part of capture persistence, not migration or enrichment. It performs no model work,
 * no search and no large query. Legacy capture remains authoritative while every newly accepted
 * raw signal also receives a canonical V4 Evidence revision with the 90-day retention contract.</p>
 */
public final class CognitiveMemoryForwardBridgeV4 {
    private CognitiveMemoryForwardBridgeV4() {}

    public static String captureRawSignal(
            VaultDb db,
            long legacySignalId,
            MasterRelevanceFilter.Signal signal,
            String contentHash,
            long capturedAt) {
        if (db == null || signal == null || legacySignalId <= 0) return "";
        try {
            long occurredAt = signal.occurredAt > 0 ? signal.occurredAt : capturedAt;
            String original = !n(signal.body).isEmpty() ? n(signal.body) : n(signal.title);
            String normalized = CognitiveIdentityV4.normalizeText(original);
            String hash = n(contentHash);
            if (hash.isEmpty()) hash = Fingerprint.text(n(signal.title) + "\n" + n(signal.body));
            String externalId = externalId(signal.metadataJson);
            CognitiveDomainV4.EvidenceSourceType type = CognitiveMemoryBackfillV4.sourceTypeForRaw(signal.kind);
            String evidenceId = CognitiveIdentityV4.evidenceId(
                    type,
                    signal.source,
                    externalId,
                    hash,
                    normalized,
                    occurredAt);
            CognitiveDomainV4.Evidence evidence = new CognitiveDomainV4.Evidence(
                    evidenceId,
                    type,
                    occurredAt,
                    capturedAt > 0 ? capturedAt : occurredAt,
                    signal.source,
                    externalId,
                    original,
                    normalized,
                    hash,
                    null,
                    CognitiveDomainV4.Sensitivity.NORMAL,
                    CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY,
                    CognitiveDomainV4.ProcessingState.READY);
            long expiresAt = occurredAt + CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS;
            String actual = CognitiveStoreV4.putEvidence(
                    db,
                    evidence,
                    metadata(legacySignalId, signal.metadataJson),
                    expiresAt);
            CognitiveStoreV4.mapLegacy(
                    db,
                    "raw_signals",
                    String.valueOf(legacySignalId),
                    CognitiveDomainV4.CanonicalObjectType.EVIDENCE,
                    actual,
                    "FORWARD");
            return actual;
        } catch (Throwable e) {
            try {
                DiagnosticsLog.error(
                        db,
                        "CognitiveMemoryForwardBridgeV4",
                        "capture_raw_signal",
                        e,
                        "V4_EVIDENCE_FORWARD_WRITE",
                        0,
                        0,
                        legacySignalId,
                        0,
                        0,
                        null);
            } catch (Throwable ignored) {}
            return "";
        }
    }

    static String externalId(String metadataJson) {
        try {
            JSONObject o = new JSONObject(n(metadataJson));
            String value = first(
                    o.optString("notification_key", ""),
                    o.optString("external_id", ""),
                    o.optString("capture_id", ""));
            if (!value.isEmpty()) return value;
            JSONObject nested = o.optJSONObject("source_metadata");
            if (nested != null) {
                return first(
                        nested.optString("notification_key", ""),
                        nested.optString("external_id", ""),
                        nested.optString("capture_id", ""));
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String metadata(long legacySignalId, String sourceMetadata) {
        try {
            JSONObject o = new JSONObject();
            o.put("capture_bridge", "v4_forward_001");
            o.put("legacy_table", "raw_signals");
            o.put("legacy_id", legacySignalId);
            if (!n(sourceMetadata).isEmpty()) {
                try { o.put("legacy_metadata", new JSONObject(sourceMetadata)); }
                catch (Throwable ignored) { o.put("legacy_metadata_text", sourceMetadata); }
            }
            return o.toString();
        } catch (Throwable ignored) {
            return "{\"capture_bridge\":\"v4_forward_001\",\"legacy_id\":" + legacySignalId + "}";
        }
    }

    private static String first(String... values) {
        if (values != null) {
            for (String value : values) if (!n(value).isEmpty()) return n(value);
        }
        return "";
    }

    private static String n(String value) { return value == null ? "" : value.trim(); }
}
