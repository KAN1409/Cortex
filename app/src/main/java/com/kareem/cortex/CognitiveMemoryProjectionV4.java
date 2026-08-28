package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic read model for the V4 Memory surface.
 *
 * <p>Reads only canonical V4 tables. It never falls back to legacy rows silently, so projection
 * equivalence can be measured before navigation is switched.</p>
 */
public final class CognitiveMemoryProjectionV4 {
    private CognitiveMemoryProjectionV4() {}

    public static final class Query {
        public String text = "";
        public long fromInclusive = 0L;
        public long toExclusive = 0L;
        public String sourcePackage = "";
        public CognitiveDomainV4.MemoryKind kind;
        public boolean pinnedOnly;
        public int limit = 80;

        public Query text(String value) { this.text = n(value); return this; }
        public Query from(long value) { this.fromInclusive = Math.max(0L, value); return this; }
        public Query to(long value) { this.toExclusive = Math.max(0L, value); return this; }
        public Query source(String value) { this.sourcePackage = n(value); return this; }
        public Query kind(CognitiveDomainV4.MemoryKind value) { this.kind = value; return this; }
        public Query pinnedOnly(boolean value) { this.pinnedOnly = value; return this; }
        public Query limit(int value) { this.limit = Math.max(1, Math.min(400, value)); return this; }
    }

    public static final class Row {
        public final String id;
        public final CognitiveDomainV4.MemoryKind kind;
        public final String title;
        public final String body;
        public final String sourcePackage;
        public final long startedAt;
        public final Long endedAt;
        public final double importance;
        public final boolean pinned;
        public final CognitiveDomainV4.RetentionClass retentionClass;
        public final String episodeId;
        public final int evidenceCount;

        Row(String id, CognitiveDomainV4.MemoryKind kind, String title, String body,
            String sourcePackage, long startedAt, Long endedAt, double importance,
            boolean pinned, CognitiveDomainV4.RetentionClass retentionClass,
            String episodeId, int evidenceCount) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.body = body;
            this.sourcePackage = sourcePackage;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.importance = importance;
            this.pinned = pinned;
            this.retentionClass = retentionClass;
            this.episodeId = episodeId;
            this.evidenceCount = evidenceCount;
        }
    }

    public static final class EvidenceRow {
        public final String id;
        public final CognitiveDomainV4.EvidenceSourceType sourceType;
        public final String sourcePackage;
        public final long occurredAt;
        public final String originalText;
        public final String assetRef;
        public final CognitiveDomainV4.ProcessingState processingState;
        public final List<AnalysisRow> analyses;

        EvidenceRow(String id, CognitiveDomainV4.EvidenceSourceType sourceType,
                    String sourcePackage, long occurredAt, String originalText,
                    String assetRef, CognitiveDomainV4.ProcessingState processingState,
                    List<AnalysisRow> analyses) {
            this.id = id;
            this.sourceType = sourceType;
            this.sourcePackage = sourcePackage;
            this.occurredAt = occurredAt;
            this.originalText = originalText;
            this.assetRef = assetRef;
            this.processingState = processingState;
            this.analyses = Collections.unmodifiableList(new ArrayList<>(analyses));
        }
    }

    public static final class AnalysisRow {
        public final String id;
        public final String kind;
        public final String engine;
        public final String version;
        public final String outputText;
        public final String outputJson;
        public final long createdAt;

        AnalysisRow(String id, String kind, String engine, String version,
                    String outputText, String outputJson, long createdAt) {
            this.id = id;
            this.kind = kind;
            this.engine = engine;
            this.version = version;
            this.outputText = outputText;
            this.outputJson = outputJson;
            this.createdAt = createdAt;
        }
    }

    public static List<Row> recent(VaultDb db, int limit) {
        return search(db, new Query().limit(limit));
    }

    public static List<Row> search(VaultDb db, Query query) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return search(db.getReadableDatabase(), query == null ? new Query() : query);
    }

    static List<Row> search(SQLiteDatabase db, Query query) {
        if (db == null) throw new IllegalArgumentException("db required");
        Query q = query == null ? new Query() : query;
        ArrayList<String> where = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();
        where.add("m.state='ACTIVE'");
        where.add("(m.pinned=1 OR m.expires_at=0 OR m.expires_at>=?)");
        args.add(String.valueOf(System.currentTimeMillis()));

        if (q.fromInclusive > 0) {
            where.add("m.started_at>=?");
            args.add(String.valueOf(q.fromInclusive));
        }
        if (q.toExclusive > 0) {
            where.add("m.started_at<?");
            args.add(String.valueOf(q.toExclusive));
        }
        if (!n(q.sourcePackage).isEmpty()) {
            where.add("LOWER(COALESCE(m.source_package,''))=LOWER(?)");
            args.add(q.sourcePackage.trim());
        }
        if (q.kind != null) {
            where.add("m.kind=?");
            args.add(q.kind.name());
        }
        if (q.pinnedOnly) where.add("m.pinned=1");

        String text = n(q.text);
        if (!text.isEmpty()) {
            String like = "%" + escapeLike(text) + "%";
            where.add("(m.title LIKE ? ESCAPE '\\' OR m.body LIKE ? ESCAPE '\\' OR " +
                    "EXISTS (SELECT 1 FROM v4_memory_evidence me JOIN v4_evidence e ON e.id=me.evidence_id " +
                    "WHERE me.memory_id=m.id AND (e.original_text LIKE ? ESCAPE '\\' OR e.normalized_text LIKE ? ESCAPE '\\' OR " +
                    "EXISTS (SELECT 1 FROM v4_evidence_analysis a WHERE a.evidence_id=e.id " +
                    "AND (a.output_text LIKE ? ESCAPE '\\' OR a.output_json LIKE ? ESCAPE '\\')))))");
            for (int i = 0; i < 6; i++) args.add(like);
        }

        String sql = "SELECT m.id,m.kind,COALESCE(m.title,''),m.body,COALESCE(m.source_package,'')," +
                "m.started_at,m.ended_at,m.importance,m.pinned,m.retention_class,COALESCE(m.episode_id,'')," +
                "(SELECT COUNT(*) FROM v4_memory_evidence me WHERE me.memory_id=m.id) evidence_count " +
                "FROM v4_memories m WHERE " + join(where, " AND ") +
                " ORDER BY m.started_at DESC,m.id DESC LIMIT ?";
        args.add(String.valueOf(Math.max(1, Math.min(400, q.limit))));

        Cursor c = db.rawQuery(sql, args.toArray(new String[0]));
        ArrayList<Row> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                out.add(new Row(
                        c.getString(0),
                        memoryKind(c.getString(1)),
                        n(c.getString(2)),
                        n(c.getString(3)),
                        n(c.getString(4)),
                        c.getLong(5),
                        c.getLong(6) > 0 ? Long.valueOf(c.getLong(6)) : null,
                        clamp01(c.getDouble(7)),
                        c.getInt(8) != 0,
                        retention(c.getString(9)),
                        n(c.getString(10)),
                        c.getInt(11)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public static List<EvidenceRow> evidence(VaultDb db, String memoryId) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return evidence(db.getReadableDatabase(), memoryId);
    }

    static List<EvidenceRow> evidence(SQLiteDatabase db, String memoryId) {
        String id = n(memoryId);
        if (id.isEmpty()) return Collections.emptyList();
        String q = "SELECT e.id,e.source_type,COALESCE(e.source_package,''),e.occurred_at," +
                "COALESCE(e.original_text,''),COALESCE(e.asset_ref,''),e.processing_state " +
                "FROM v4_memory_evidence me JOIN v4_evidence e ON e.id=me.evidence_id " +
                "WHERE me.memory_id=? ORDER BY me.ordinal ASC,e.occurred_at ASC";
        Cursor c = db.rawQuery(q, new String[]{id});
        ArrayList<EvidenceRow> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                String evidenceId = c.getString(0);
                out.add(new EvidenceRow(
                        evidenceId,
                        evidenceType(c.getString(1)),
                        n(c.getString(2)),
                        c.getLong(3),
                        n(c.getString(4)),
                        n(c.getString(5)),
                        processing(c.getString(6)),
                        analyses(db, evidenceId)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    static List<AnalysisRow> analyses(SQLiteDatabase db, String evidenceId) {
        Cursor c = db.query(
                "v4_evidence_analysis",
                new String[]{"id", "analysis_kind", "engine", "version", "output_text", "output_json", "created_at"},
                "evidence_id=?",
                new String[]{evidenceId},
                null,
                null,
                "created_at ASC,id ASC");
        ArrayList<AnalysisRow> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                out.add(new AnalysisRow(
                        n(c.getString(0)), n(c.getString(1)), n(c.getString(2)), n(c.getString(3)),
                        n(c.getString(4)), n(c.getString(5)), c.getLong(6)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public static int activeCount(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM v4_memories WHERE state='ACTIVE' AND (pinned=1 OR expires_at=0 OR expires_at>=?)",
                new String[]{String.valueOf(System.currentTimeMillis())});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    private static CognitiveDomainV4.MemoryKind memoryKind(String raw) {
        try { return CognitiveDomainV4.MemoryKind.valueOf(n(raw).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return CognitiveDomainV4.MemoryKind.MOMENT; }
    }

    private static CognitiveDomainV4.EvidenceSourceType evidenceType(String raw) {
        try { return CognitiveDomainV4.EvidenceSourceType.valueOf(n(raw).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return CognitiveDomainV4.EvidenceSourceType.SYSTEM; }
    }

    private static CognitiveDomainV4.ProcessingState processing(String raw) {
        try { return CognitiveDomainV4.ProcessingState.valueOf(n(raw).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return CognitiveDomainV4.ProcessingState.CAPTURED; }
    }

    private static CognitiveDomainV4.RetentionClass retention(String raw) {
        try { return CognitiveDomainV4.RetentionClass.valueOf(n(raw).toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return CognitiveDomainV4.RetentionClass.EPISODIC_90_DAY; }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String join(List<String> xs, String separator) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (b.length() > 0) b.append(separator);
            b.append(x);
        }
        return b.toString();
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static String n(String s) { return s == null ? "" : s.trim(); }
}
