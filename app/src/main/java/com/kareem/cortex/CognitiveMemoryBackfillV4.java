package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Bounded, idempotent bridge from the current Cortex stores into V4 Evidence/Episode/Memory.
 *
 * <p>Legacy rows remain untouched. v4_legacy_map is the migration cursor, so interrupted runs are
 * safe to repeat. Raw context becomes Evidence first; only durable knowledge_items become Memory.</p>
 */
public final class CognitiveMemoryBackfillV4 {
    public static final long EPISODIC_WINDOW_MS = 90L * 24L * 60L * 60L * 1000L;

    private CognitiveMemoryBackfillV4() {}

    public static final class Stats {
        public int evidenceMapped;
        public int episodesMapped;
        public int memoriesMapped;
        public int deferred;
        public int failed;

        public int totalMapped() {
            return evidenceMapped + episodesMapped + memoriesMapped;
        }
    }

    public static Stats runBatch(VaultDb db, int maxPerLayer) {
        if (db == null) throw new IllegalArgumentException("db required");
        int limit = Math.max(1, Math.min(500, maxPerLayer));
        CognitiveStoreV4.ensure(db);
        Stats stats = new Stats();
        long cutoff = System.currentTimeMillis() - EPISODIC_WINDOW_MS;
        migrateRawSignals(db, cutoff, limit, stats);
        migrateThreads(db, cutoff, limit, stats);
        migrateKnowledgeItems(db, cutoff, limit, stats);
        return stats;
    }

    private static void migrateRawSignals(VaultDb db, long cutoff, int limit, Stats stats) {
        SQLiteDatabase sql = db.getReadableDatabase();
        String q = "SELECT r.id,r.kind,r.source,r.title,r.body,r.metadata_json,r.content_hash," +
                "r.occurred_at,r.created_at,r.retention_until,r.state " +
                "FROM raw_signals r " +
                "WHERE r.occurred_at>=? AND (r.retention_until=0 OR r.retention_until>=?) " +
                "AND NOT EXISTS (SELECT 1 FROM v4_legacy_map m WHERE m.legacy_table='raw_signals' " +
                "AND m.legacy_id=CAST(r.id AS TEXT) AND m.object_type='EVIDENCE') " +
                "ORDER BY r.id ASC LIMIT ?";
        Cursor c = sql.rawQuery(q, new String[]{String.valueOf(cutoff), String.valueOf(System.currentTimeMillis()), String.valueOf(limit)});
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
                    long legacyRetention = c.getLong(9);
                    String original = !body.isEmpty() ? body : title;
                    String normalized = CognitiveIdentityV4.normalizeText(original);
                    if (!looksLikeHash(hash)) hash = Fingerprint.text(title + "\n" + body);
                    CognitiveDomainV4.EvidenceSourceType sourceType = sourceTypeForRaw(kind);
                    String externalId = externalId(metadata);
                    String evidenceId = CognitiveIdentityV4.evidenceId(sourceType, source, externalId, hash, normalized, occurredAt);
                    CognitiveDomainV4.Evidence evidence = new CognitiveDomainV4.Evidence(
                            evidenceId,
                            sourceType,
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
                    long expiresAt = legacyRetention > 0
                            ? legacyRetention
                            : Math.max(occurredAt, capturedAt) + EPISODIC_WINDOW_MS;
                    String actualId = CognitiveStoreV4.putEvidence(db, evidence, legacyMetadata("raw_signals", legacyId, metadata), expiresAt);
                    CognitiveStoreV4.mapLegacy(db, "raw_signals", String.valueOf(legacyId), CognitiveDomainV4.CanonicalObjectType.EVIDENCE, actualId, "MAPPED");
                    stats.evidenceMapped++;
                } catch (Throwable ignored) {
                    stats.failed++;
                }
            }
        } finally {
            c.close();
        }
    }

    private static void migrateThreads(VaultDb db, long cutoff, int limit, Stats stats) {
        SQLiteDatabase sql = db.getReadableDatabase();
        String q = "SELECT t.id,t.kind,t.source,t.external_key,t.state,t.started_at,t.last_event_at " +
                "FROM signal_threads t WHERE t.last_event_at>=? " +
                "AND NOT EXISTS (SELECT 1 FROM v4_legacy_map m WHERE m.legacy_table='signal_threads' " +
                "AND m.legacy_id=CAST(t.id AS TEXT) AND m.object_type='EPISODE') " +
                "ORDER BY t.id ASC LIMIT ?";
        Cursor c = sql.rawQuery(q, new String[]{String.valueOf(cutoff), String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                try {
                    long threadId = c.getLong(0);
                    String kind = n(c.getString(1));
                    String source = n(c.getString(2));
                    String externalKey = n(c.getString(3));
                    String state = n(c.getString(4));
                    long startedAt = c.getLong(5);
                    long lastEventAt = c.getLong(6);
                    List<String> evidenceIds = evidenceIdsForThread(db, threadId);
                    if (evidenceIds.isEmpty()) {
                        stats.deferred++;
                        continue;
                    }
                    CognitiveDomainV4.EpisodeKind episodeKind = episodeKind(kind);
                    CognitiveDomainV4.EpisodeState episodeState = "open".equalsIgnoreCase(state)
                            ? CognitiveDomainV4.EpisodeState.OPEN
                            : CognitiveDomainV4.EpisodeState.CLOSED;
                    String episodeId = CognitiveIdentityV4.objectId("ep", "legacy-signal-thread|" + threadId);
                    CognitiveDomainV4.Episode episode = new CognitiveDomainV4.Episode(
                            episodeId,
                            episodeKind,
                            startedAt,
                            episodeState == CognitiveDomainV4.EpisodeState.OPEN ? null : Long.valueOf(lastEventAt),
                            source,
                            evidenceIds,
                            Collections.<String>emptyList(),
                            episodeState);
                    String actualId = CognitiveStoreV4.putEpisode(db, episode, externalKey);
                    CognitiveStoreV4.mapLegacy(db, "signal_threads", String.valueOf(threadId), CognitiveDomainV4.CanonicalObjectType.EPISODE, actualId, "MAPPED");
                    stats.episodesMapped++;
                } catch (Throwable ignored) {
                    stats.failed++;
                }
            }
        } finally {
            c.close();
        }
    }

    private static void migrateKnowledgeItems(VaultDb db, long cutoff, int limit, Stats stats) {
        SQLiteDatabase sql = db.getReadableDatabase();
        String q = "SELECT k.id,k.type,k.source,k.title,k.raw_text,k.extracted_text,k.summary," +
                "k.attachment_path,k.status,k.fingerprint,k.metadata_json,k.created_at,k.updated_at " +
                "FROM knowledge_items k WHERE k.created_at>=? " +
                "AND NOT EXISTS (SELECT 1 FROM v4_legacy_map m WHERE m.legacy_table='knowledge_items' " +
                "AND m.legacy_id=CAST(k.id AS TEXT) AND m.object_type='MEMORY') " +
                "ORDER BY k.id ASC LIMIT ?";
        Cursor c = sql.rawQuery(q, new String[]{String.valueOf(cutoff), String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                try {
                    long itemId = c.getLong(0);
                    String type = n(c.getString(1));
                    String source = n(c.getString(2));
                    String title = n(c.getString(3));
                    String raw = n(c.getString(4));
                    String extracted = n(c.getString(5));
                    String summary = n(c.getString(6));
                    String attachment = n(c.getString(7));
                    String status = n(c.getString(8));
                    String fingerprint = n(c.getString(9));
                    String metadata = n(c.getString(10));
                    long createdAt = c.getLong(11);
                    long updatedAt = c.getLong(12);

                    long rawSignalId = metaLong(metadata, "raw_signal_id");
                    String evidenceId;
                    if (rawSignalId > 0) {
                        evidenceId = mappedObject(db, "raw_signals", String.valueOf(rawSignalId), "EVIDENCE");
                        if (evidenceId.isEmpty()) {
                            stats.deferred++;
                            continue;
                        }
                    } else {
                        evidenceId = createKnowledgeEvidence(db, itemId, type, source, title, raw, attachment, status, fingerprint, metadata, createdAt, updatedAt);
                    }

                    if (!extracted.isEmpty()) {
                        CognitiveStoreV4.appendEvidenceAnalysis(db, evidenceId, analysisKind(type), "legacy-knowledge-item", "v1", extracted, null);
                    }
                    if (!summary.isEmpty()) {
                        CognitiveStoreV4.appendEvidenceAnalysis(db, evidenceId, "SUMMARY", "legacy-knowledge-item", "v1", summary, null);
                    }
                    migrateLegacyAnalyses(db, itemId, evidenceId);

                    String episodeId = "";
                    long threadId = metaLong(metadata, "thread_id");
                    if (threadId > 0) episodeId = mappedObject(db, "signal_threads", String.valueOf(threadId), "EPISODE");

                    CognitiveDomainV4.MemoryKind memoryKind = memoryKind(type);
                    String body = firstNonEmpty(raw, extracted, summary, title);
                    boolean pinned = metaBoolean(metadata, "pinned");
                    CognitiveDomainV4.RetentionClass retention = pinned
                            ? CognitiveDomainV4.RetentionClass.PINNED
                            : CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY;
                    double importance = importance(metadata);
                    String memoryId = CognitiveIdentityV4.objectId("mem", "legacy-knowledge-item|" + itemId);
                    CognitiveDomainV4.Memory memory = new CognitiveDomainV4.Memory(
                            memoryId,
                            memoryKind,
                            title,
                            body,
                            createdAt,
                            null,
                            Collections.singletonList(evidenceId),
                            episodeId.isEmpty() ? null : episodeId,
                            source,
                            Collections.<String>emptyList(),
                            importance,
                            pinned,
                            retention);
                    long expiresAt = pinned ? 0L : createdAt + EPISODIC_WINDOW_MS;
                    String actualMemoryId = CognitiveStoreV4.putMemory(db, memory, "legacy-knowledge-item:" + itemId, expiresAt);
                    if (!episodeId.isEmpty()) {
                        CognitiveStoreV4.addProvenance(db,
                                CognitiveDomainV4.CanonicalObjectType.MEMORY,
                                actualMemoryId,
                                CognitiveDomainV4.CanonicalObjectType.EPISODE,
                                episodeId,
                                "part_of",
                                1.0);
                    }
                    CognitiveStoreV4.mapLegacy(db, "knowledge_items", String.valueOf(itemId), CognitiveDomainV4.CanonicalObjectType.MEMORY, actualMemoryId, "MAPPED");
                    stats.memoriesMapped++;
                } catch (Throwable ignored) {
                    stats.failed++;
                }
            }
        } finally {
            c.close();
        }
    }

    private static String createKnowledgeEvidence(
            VaultDb db,
            long itemId,
            String type,
            String source,
            String title,
            String raw,
            String attachment,
            String status,
            String fingerprint,
            String metadata,
            long createdAt,
            long updatedAt) {
        CognitiveDomainV4.EvidenceSourceType sourceType = sourceTypeForKnowledge(type);
        String original = firstNonEmpty(raw, title);
        String normalized = CognitiveIdentityV4.normalizeText(original);
        String hash = looksLikeHash(fingerprint)
                ? fingerprint.toLowerCase(Locale.ROOT)
                : Fingerprint.text(type + "\n" + source + "\n" + original + "\n" + attachment);
        String externalId = "legacy-knowledge-item:" + itemId;
        String evidenceId = CognitiveIdentityV4.evidenceId(sourceType, source, externalId, hash, normalized, createdAt);
        CognitiveDomainV4.Evidence evidence = new CognitiveDomainV4.Evidence(
                evidenceId,
                sourceType,
                createdAt,
                updatedAt > 0 ? updatedAt : createdAt,
                source,
                externalId,
                original,
                normalized,
                hash,
                attachment.isEmpty() ? null : attachment,
                CognitiveDomainV4.Sensitivity.NORMAL,
                CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY,
                processingState(status));
        String actualId = CognitiveStoreV4.putEvidence(db, evidence, legacyMetadata("knowledge_items", itemId, metadata), createdAt + EPISODIC_WINDOW_MS);
        CognitiveStoreV4.mapLegacy(db, "knowledge_items", String.valueOf(itemId), CognitiveDomainV4.CanonicalObjectType.EVIDENCE, actualId, "MAPPED");
        return actualId;
    }

    private static void migrateLegacyAnalyses(VaultDb db, long itemId, String evidenceId) {
        Cursor c = db.getReadableDatabase().query(
                "analyses",
                new String[]{"engine", "version", "output_json"},
                "item_id=?",
                new String[]{String.valueOf(itemId)},
                null,
                null,
                "id ASC",
                "8");
        try {
            while (c.moveToNext()) {
                String engine = n(c.getString(0));
                String version = n(c.getString(1));
                String output = n(c.getString(2));
                if (output.isEmpty()) continue;
                CognitiveStoreV4.appendEvidenceAnalysis(
                        db,
                        evidenceId,
                        "LEGACY_ANALYSIS",
                        engine.isEmpty() ? "legacy" : engine,
                        version.isEmpty() ? "unknown" : version,
                        null,
                        output);
            }
        } finally {
            c.close();
        }
    }

    private static List<String> evidenceIdsForThread(VaultDb db, long threadId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String q = "SELECT m.object_id FROM raw_signals r JOIN v4_legacy_map m " +
                "ON m.legacy_table='raw_signals' AND m.legacy_id=CAST(r.id AS TEXT) AND m.object_type='EVIDENCE' " +
                "WHERE r.thread_id=? ORDER BY r.occurred_at ASC,r.id ASC";
        Cursor c = db.getReadableDatabase().rawQuery(q, new String[]{String.valueOf(threadId)});
        try {
            while (c.moveToNext()) {
                String id = n(c.getString(0));
                if (!id.isEmpty()) ids.add(id);
            }
        } finally {
            c.close();
        }
        return new ArrayList<>(ids);
    }

    static CognitiveDomainV4.EvidenceSourceType sourceTypeForRaw(String kind) {
        String x = low(kind);
        if (x.contains("notification")) return CognitiveDomainV4.EvidenceSourceType.NOTIFICATION;
        if (x.contains("screen") || x.contains("accessibility")) return CognitiveDomainV4.EvidenceSourceType.SCREEN;
        if (x.contains("app_activity") || x.contains("usage")) return CognitiveDomainV4.EvidenceSourceType.APP_ACTIVITY;
        return CognitiveDomainV4.EvidenceSourceType.SYSTEM;
    }

    static CognitiveDomainV4.EvidenceSourceType sourceTypeForKnowledge(String type) {
        String x = low(type);
        if (x.contains("notification")) return CognitiveDomainV4.EvidenceSourceType.NOTIFICATION;
        if (x.contains("screen")) return CognitiveDomainV4.EvidenceSourceType.SCREEN;
        if (x.contains("voice") || x.contains("audio")) return CognitiveDomainV4.EvidenceSourceType.VOICE;
        if (x.contains("image") || x.contains("screenshot") || x.contains("photo")) return CognitiveDomainV4.EvidenceSourceType.IMAGE;
        if (x.contains("share")) return CognitiveDomainV4.EvidenceSourceType.SHARE;
        if (x.contains("note") || x.contains("text")) return CognitiveDomainV4.EvidenceSourceType.NOTE;
        if (x.contains("link") || x.contains("url") || x.contains("web")) return CognitiveDomainV4.EvidenceSourceType.LINK;
        if (x.contains("file") || x.contains("document") || x.contains("pdf")) return CognitiveDomainV4.EvidenceSourceType.FILE;
        return CognitiveDomainV4.EvidenceSourceType.NOTE;
    }

    static CognitiveDomainV4.MemoryKind memoryKind(String type) {
        String x = low(type);
        if (x.contains("notification") || x.contains("message")) return CognitiveDomainV4.MemoryKind.CONVERSATION;
        if (x.contains("screen")) return CognitiveDomainV4.MemoryKind.SCREEN_CONTEXT;
        if (x.contains("voice") || x.contains("audio")) return CognitiveDomainV4.MemoryKind.VOICE;
        if (x.contains("image") || x.contains("screenshot") || x.contains("photo")) return CognitiveDomainV4.MemoryKind.IMAGE;
        if (x.contains("file") || x.contains("document") || x.contains("pdf")) return CognitiveDomainV4.MemoryKind.DOCUMENT;
        if (x.contains("link") || x.contains("url") || x.contains("web")) return CognitiveDomainV4.MemoryKind.LINK;
        if (x.contains("session") || x.contains("usage")) return CognitiveDomainV4.MemoryKind.APP_SESSION;
        if (x.contains("note") || x.contains("text")) return CognitiveDomainV4.MemoryKind.NOTE;
        return CognitiveDomainV4.MemoryKind.MOMENT;
    }

    static CognitiveDomainV4.EpisodeKind episodeKind(String legacyKind) {
        String x = low(legacyKind);
        if (x.contains("communication") || x.contains("email") || x.contains("message")) return CognitiveDomainV4.EpisodeKind.CONVERSATION;
        if (x.contains("screen") || x.contains("usage") || x.contains("app")) return CognitiveDomainV4.EpisodeKind.APP_SESSION;
        if (x.contains("document")) return CognitiveDomainV4.EpisodeKind.DOCUMENT_WORK;
        return CognitiveDomainV4.EpisodeKind.GENERIC;
    }

    private static CognitiveDomainV4.ProcessingState processingState(String status) {
        String x = low(status);
        if (x.contains("fail")) return CognitiveDomainV4.ProcessingState.FAILED;
        if (x.contains("analyz") || x.contains("process")) return CognitiveDomainV4.ProcessingState.ENRICHING;
        return CognitiveDomainV4.ProcessingState.READY;
    }

    private static String analysisKind(String type) {
        String x = low(type);
        if (x.contains("voice") || x.contains("audio")) return "TRANSCRIPT";
        if (x.contains("image") || x.contains("screenshot") || x.contains("photo")) return "OCR";
        return "EXTRACTION";
    }

    private static String mappedObject(VaultDb db, String legacyTable, String legacyId, String objectType) {
        Cursor c = db.getReadableDatabase().query(
                "v4_legacy_map",
                new String[]{"object_id"},
                "legacy_table=? AND legacy_id=? AND object_type=?",
                new String[]{legacyTable, legacyId, objectType},
                null,
                null,
                null,
                "1");
        String id = c.moveToFirst() ? n(c.getString(0)) : "";
        c.close();
        return id;
    }

    private static String externalId(String metadata) {
        try {
            JSONObject o = new JSONObject(n(metadata));
            String x = firstNonEmpty(
                    o.optString("notification_key", ""),
                    o.optString("external_id", ""),
                    o.optString("capture_id", ""));
            if (!x.isEmpty()) return x;
            JSONObject source = o.optJSONObject("source_metadata");
            if (source != null) {
                return firstNonEmpty(
                        source.optString("notification_key", ""),
                        source.optString("external_id", ""),
                        source.optString("capture_id", ""));
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static long metaLong(String metadata, String key) {
        try { return new JSONObject(n(metadata)).optLong(key, 0L); }
        catch (Exception ignored) { return 0L; }
    }

    private static boolean metaBoolean(String metadata, String key) {
        try { return new JSONObject(n(metadata)).optBoolean(key, false); }
        catch (Exception ignored) { return false; }
    }

    private static double importance(String metadata) {
        try {
            double v = new JSONObject(n(metadata)).optDouble("importance", 50.0);
            if (v > 1.0) v /= 100.0;
            return Math.max(0.0, Math.min(1.0, v));
        } catch (Exception ignored) {
            return 0.5;
        }
    }

    private static String legacyMetadata(String table, long id, String sourceMetadata) {
        try {
            JSONObject o = new JSONObject();
            o.put("migrated_from", table);
            o.put("legacy_id", id);
            if (!n(sourceMetadata).isEmpty()) o.put("legacy_metadata", new JSONObject(sourceMetadata));
            return o.toString();
        } catch (Exception ignored) {
            return "{\"migrated_from\":\"" + table + "\",\"legacy_id\":" + id + "}";
        }
    }

    private static boolean looksLikeHash(String x) {
        return x != null && x.trim().toLowerCase(Locale.ROOT).matches("[0-9a-f]{16,128}");
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String low(String s) { return n(s).toLowerCase(Locale.ROOT); }
    private static String n(String s) { return s == null ? "" : s.trim(); }
}
