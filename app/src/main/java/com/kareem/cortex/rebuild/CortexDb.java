package com.kareem.cortex.rebuild;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fresh Cortex storage.
 *
 * Canonical rebuild rule:
 *   evidence is append-only perception;
 *   situations/world/memory are separate cognitive products built from grounded evidence.
 * Raw Relay payloads therefore never become a Now card merely because they arrived.
 */
public final class CortexDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "cortex_rebuild.db";
    private static final int DB_VERSION = 2;

    public CortexDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE evidence(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "external_id TEXT," +
                "kind TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "source TEXT NOT NULL," +
                "source_package TEXT NOT NULL DEFAULT ''," +
                "protocol TEXT NOT NULL DEFAULT ''," +
                "occurred_at INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "payload_json TEXT NOT NULL DEFAULT '{}'," +
                "quality_json TEXT NOT NULL DEFAULT '{}'," +
                "state TEXT NOT NULL DEFAULT 'observed')");
        db.execSQL("CREATE UNIQUE INDEX idx_evidence_external ON evidence(external_id) WHERE external_id IS NOT NULL");
        db.execSQL("CREATE INDEX idx_evidence_time ON evidence(occurred_at DESC,id DESC)");
        db.execSQL("CREATE INDEX idx_evidence_source ON evidence(source,source_package,occurred_at DESC)");

        db.execSQL("CREATE TABLE ingest_receipts(" +
                "event_id TEXT PRIMARY KEY," +
                "evidence_id INTEGER NOT NULL," +
                "connector_id TEXT NOT NULL," +
                "protocol TEXT NOT NULL," +
                "status TEXT NOT NULL," +
                "received_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_receipts_received ON ingest_receipts(received_at DESC)");

        db.execSQL("CREATE TABLE memories(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "evidence_id INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE situations(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "summary TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'active'," +
                "attention TEXT NOT NULL DEFAULT 'quiet'," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE situation_evidence(" +
                "situation_id INTEGER NOT NULL," +
                "evidence_id INTEGER NOT NULL," +
                "relation TEXT NOT NULL DEFAULT 'supports'," +
                "PRIMARY KEY(situation_id,evidence_id))");
        db.execSQL("CREATE TABLE world_entities(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entity_type TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "summary TEXT NOT NULL DEFAULT ''," +
                "state TEXT NOT NULL DEFAULT 'active'," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE world_entity_evidence(" +
                "entity_id INTEGER NOT NULL," +
                "evidence_id INTEGER NOT NULL," +
                "relation TEXT NOT NULL DEFAULT 'supports'," +
                "PRIMARY KEY(entity_id,evidence_id))");
        db.execSQL("CREATE INDEX idx_memories_created ON memories(created_at DESC)");
        db.execSQL("CREATE INDEX idx_situations_state ON situations(state,updated_at DESC)");
        db.execSQL("CREATE INDEX idx_world_state ON world_entities(state,updated_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE evidence ADD COLUMN external_id TEXT");
            db.execSQL("ALTER TABLE evidence ADD COLUMN source_package TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE evidence ADD COLUMN protocol TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE evidence ADD COLUMN payload_json TEXT NOT NULL DEFAULT '{}'");
            db.execSQL("ALTER TABLE evidence ADD COLUMN quality_json TEXT NOT NULL DEFAULT '{}'");
            db.execSQL("ALTER TABLE evidence ADD COLUMN state TEXT NOT NULL DEFAULT 'observed'");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_evidence_external ON evidence(external_id) WHERE external_id IS NOT NULL");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_evidence_time ON evidence(occurred_at DESC,id DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_evidence_source ON evidence(source,source_package,occurred_at DESC)");
            db.execSQL("CREATE TABLE IF NOT EXISTS ingest_receipts(" +
                    "event_id TEXT PRIMARY KEY," +
                    "evidence_id INTEGER NOT NULL," +
                    "connector_id TEXT NOT NULL," +
                    "protocol TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "received_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_receipts_received ON ingest_receipts(received_at DESC)");
            db.execSQL("CREATE TABLE IF NOT EXISTS situation_evidence(" +
                    "situation_id INTEGER NOT NULL,evidence_id INTEGER NOT NULL," +
                    "relation TEXT NOT NULL DEFAULT 'supports',PRIMARY KEY(situation_id,evidence_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS world_entity_evidence(" +
                    "entity_id INTEGER NOT NULL,evidence_id INTEGER NOT NULL," +
                    "relation TEXT NOT NULL DEFAULT 'supports',PRIMARY KEY(entity_id,evidence_id))");
        }
    }

    public long capture(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues evidence = new ContentValues();
            evidence.put("kind", "MANUAL_CAPTURE");
            evidence.put("body", clean);
            evidence.put("source", "user");
            evidence.put("source_package", "");
            evidence.put("protocol", "MANUAL_V1");
            evidence.put("occurred_at", now);
            evidence.put("created_at", now);
            evidence.put("payload_json", "{}");
            evidence.put("quality_json", "{\"direct_user_input\":1.0}");
            evidence.put("state", "observed");
            long evidenceId = db.insertOrThrow("evidence", null, evidence);

            ContentValues memory = new ContentValues();
            memory.put("evidence_id", evidenceId);
            memory.put("title", titleFor(clean));
            memory.put("body", clean);
            memory.put("created_at", now);
            long memoryId = db.insertOrThrow("memories", null, memory);
            db.setTransactionSuccessful();
            return memoryId;
        } finally {
            db.endTransaction();
        }
    }

    /** Idempotent durable intake. The exact payload is retained as evidence, never promoted by this method. */
    public IngestResult ingestRelay(RelayEnvelope envelope) {
        if (envelope == null || clean(envelope.eventId).isEmpty()) {
            throw new IllegalArgumentException("Relay event_id is required");
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long existing = evidenceIdForEvent(db, envelope.eventId);
            if (existing > 0) {
                db.setTransactionSuccessful();
                return new IngestResult(existing, true);
            }

            long receivedAt = System.currentTimeMillis();
            ContentValues evidence = new ContentValues();
            evidence.put("external_id", envelope.eventId);
            evidence.put("kind", clean(envelope.kind).isEmpty() ? "NOTIFICATION" : envelope.kind);
            evidence.put("body", clean(envelope.summary));
            evidence.put("source", "relay");
            evidence.put("source_package", clean(envelope.sourcePackage));
            evidence.put("protocol", clean(envelope.protocol));
            evidence.put("occurred_at", envelope.occurredAt > 0 ? envelope.occurredAt : receivedAt);
            evidence.put("created_at", receivedAt);
            evidence.put("payload_json", clean(envelope.payloadJson).isEmpty() ? "{}" : envelope.payloadJson);
            evidence.put("quality_json", clean(envelope.qualityJson).isEmpty() ? "{}" : envelope.qualityJson);
            evidence.put("state", "observed");

            long evidenceId;
            try {
                evidenceId = db.insertOrThrow("evidence", null, evidence);
            } catch (SQLiteConstraintException duplicate) {
                evidenceId = evidenceIdForEvent(db, envelope.eventId);
                if (evidenceId <= 0) throw duplicate;
                db.setTransactionSuccessful();
                return new IngestResult(evidenceId, true);
            }

            ContentValues receipt = new ContentValues();
            receipt.put("event_id", envelope.eventId);
            receipt.put("evidence_id", evidenceId);
            receipt.put("connector_id", envelope.connectorId);
            receipt.put("protocol", envelope.protocol);
            receipt.put("status", "ACCEPTED");
            receipt.put("received_at", receivedAt);
            db.insertOrThrow("ingest_receipts", null, receipt);
            db.setTransactionSuccessful();
            return new IngestResult(evidenceId, false);
        } finally {
            db.endTransaction();
        }
    }

    private long evidenceIdForEvent(SQLiteDatabase db, String eventId) {
        Cursor c = db.rawQuery("SELECT evidence_id FROM ingest_receipts WHERE event_id=? LIMIT 1", new String[]{eventId});
        try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        c = db.rawQuery("SELECT id FROM evidence WHERE external_id=? LIMIT 1", new String[]{eventId});
        try { return c.moveToFirst() ? c.getLong(0) : 0L; } finally { c.close(); }
    }

    public RelayStats relayStats() {
        SQLiteDatabase db = getReadableDatabase();
        long total = 0, last = 0;
        String source = "", protocol = "";
        Cursor c = db.rawQuery("SELECT COUNT(*),COALESCE(MAX(received_at),0) FROM ingest_receipts WHERE connector_id='second_brain'", null);
        try { if (c.moveToFirst()) { total = c.getLong(0); last = c.getLong(1); } } finally { c.close(); }
        if (last > 0) {
            c = db.rawQuery("SELECT e.source_package,r.protocol FROM ingest_receipts r JOIN evidence e ON e.id=r.evidence_id " +
                    "WHERE r.connector_id='second_brain' ORDER BY r.received_at DESC LIMIT 1", null);
            try { if (c.moveToFirst()) { source = c.getString(0); protocol = c.getString(1); } } finally { c.close(); }
        }
        long since = startOfLocalDay(System.currentTimeMillis());
        long today = 0;
        c = db.rawQuery("SELECT COUNT(*) FROM ingest_receipts WHERE connector_id='second_brain' AND received_at>=?", new String[]{String.valueOf(since)});
        try { if (c.moveToFirst()) today = c.getLong(0); } finally { c.close(); }
        return new RelayStats(total, today, last, source, protocol);
    }

    public List<Row> recentMemories(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("memories",
                new String[]{"id","title","body","created_at"},
                null,null,null,null,"created_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), "MEMORY"));
        } finally { c.close(); }
        return out;
    }

    public List<Row> activeSituations(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("situations",
                new String[]{"id","title","summary","updated_at","attention"},
                "state='active'",null,null,null,"updated_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        } finally { c.close(); }
        return out;
    }

    public List<Row> worldEntities(int limit) {
        ArrayList<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("world_entities",
                new String[]{"id","name","summary","updated_at","entity_type"},
                "state='active'",null,null,null,"updated_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        } finally { c.close(); }
        return out;
    }

    public List<Row> searchGrounded(String query, int limit) {
        String q = clean(query);
        if (q.isEmpty()) return new ArrayList<>();
        ArrayList<Row> out = new ArrayList<>();
        String like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%";

        Cursor c = getReadableDatabase().query("memories",
                new String[]{"id","title","body","created_at"},
                "title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\'",
                new String[]{like,like},null,null,"created_at DESC",String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext() && out.size() < limit) {
                out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), "MEMORY"));
            }
        } finally { c.close(); }

        if (out.size() < limit) {
            c = getReadableDatabase().query("evidence",
                    new String[]{"id","kind","body","occurred_at","source_package"},
                    "body LIKE ? ESCAPE '\\'",
                    new String[]{like},null,null,"occurred_at DESC",String.valueOf(limit - out.size()));
            try {
                while (c.moveToNext()) {
                    String pkg = c.getString(4);
                    String title = c.getString(1) + (pkg == null || pkg.isEmpty() ? "" : " · " + pkg);
                    out.add(new Row(c.getLong(0), title, c.getString(2), c.getLong(3), "EVIDENCE"));
                }
            } finally { c.close(); }
        }
        return out;
    }

    private static long startOfLocalDay(long now) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static String titleFor(String text) {
        String one = text.replace('\n',' ').replace('\r',' ').replaceAll("\\s+", " ").trim();
        if (one.length() <= 52) return one;
        return one.substring(0, 52).trim() + "…";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class RelayEnvelope {
        public final String eventId, connectorId, protocol, kind, sourcePackage, summary, payloadJson, qualityJson;
        public final long occurredAt;
        public RelayEnvelope(String eventId, String connectorId, String protocol, String kind,
                             String sourcePackage, long occurredAt, String summary,
                             String payloadJson, String qualityJson) {
            this.eventId=clean(eventId); this.connectorId=clean(connectorId); this.protocol=clean(protocol);
            this.kind=clean(kind); this.sourcePackage=clean(sourcePackage); this.occurredAt=occurredAt;
            this.summary=clean(summary); this.payloadJson=payloadJson==null?"{}":payloadJson;
            this.qualityJson=qualityJson==null?"{}":qualityJson;
        }
    }

    public static final class IngestResult {
        public final long evidenceId;
        public final boolean duplicate;
        IngestResult(long evidenceId, boolean duplicate) { this.evidenceId=evidenceId; this.duplicate=duplicate; }
    }

    public static final class RelayStats {
        public final long total, today, lastReceivedAt;
        public final String lastSourcePackage, lastProtocol;
        RelayStats(long total, long today, long lastReceivedAt, String lastSourcePackage, String lastProtocol) {
            this.total=total; this.today=today; this.lastReceivedAt=lastReceivedAt;
            this.lastSourcePackage=clean(lastSourcePackage); this.lastProtocol=clean(lastProtocol);
        }
    }

    public static final class Row {
        public final long id, updatedAt;
        public final String title, body, type;
        Row(long id, String title, String body, long updatedAt, String type) {
            this.id=id; this.title=title==null?"":title; this.body=body==null?"":body;
            this.updatedAt=updatedAt; this.type=type==null?"":type;
        }
    }
}