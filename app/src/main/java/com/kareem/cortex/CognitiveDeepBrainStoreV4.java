package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;

/** Persistent audit trail for user-triggered Deep Brain export/import. */
public final class CognitiveDeepBrainStoreV4 {
    private CognitiveDeepBrainStoreV4() {}

    public static void ensure(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveSchemaV4.ensure(db.getWritableDatabase());
        ensure(db.getWritableDatabase());
    }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_deep_brain_requests(" +
                "id TEXT PRIMARY KEY," +
                "question TEXT NOT NULL," +
                "context_json TEXT NOT NULL," +
                "share_text_hash TEXT NOT NULL," +
                "situation_ids_json TEXT NOT NULL," +
                "memory_ids_json TEXT NOT NULL," +
                "world_ids_json TEXT NOT NULL," +
                "fact_ids_json TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'CREATED'," +
                "created_at INTEGER NOT NULL," +
                "exported_at INTEGER DEFAULT 0," +
                "applied_at INTEGER DEFAULT 0," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_deep_brain_req_state ON v4_deep_brain_requests(state,created_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_deep_brain_responses(" +
                "id TEXT PRIMARY KEY," +
                "request_id TEXT NOT NULL UNIQUE," +
                "answer TEXT," +
                "raw_response TEXT NOT NULL," +
                "response_json TEXT NOT NULL," +
                "applied_summary_json TEXT," +
                "created_at INTEGER NOT NULL," +
                "applied_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_deep_brain_resp_req ON v4_deep_brain_responses(request_id,created_at DESC)");
    }

    public static String newRequestId() {
        return "brq_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static void saveRequest(VaultDb db, CognitiveDeepBrainPacketBuilderV4.Packet packet) {
        if (db == null || packet == null) throw new IllegalArgumentException("db and packet required");
        ensure(db);
        saveRequest(db.getWritableDatabase(), packet);
    }

    static void saveRequest(SQLiteDatabase sql, CognitiveDeepBrainPacketBuilderV4.Packet packet) {
        ensure(sql);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", packet.requestId);
        v.put("question", packet.question);
        v.put("context_json", packet.contextJson);
        v.put("share_text_hash", Fingerprint.text(packet.shareText));
        v.put("situation_ids_json", json(packet.situationIds));
        v.put("memory_ids_json", json(packet.memoryIds));
        v.put("world_ids_json", json(packet.worldIds));
        v.put("fact_ids_json", json(packet.factIds));
        v.put("state", "CREATED");
        v.put("created_at", now);
        v.put("exported_at", 0);
        v.put("applied_at", 0);
        v.put("updated_at", now);
        long row = sql.insertWithOnConflict("v4_deep_brain_requests", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (row < 0 && !requestExists(sql, packet.requestId)) throw new IllegalStateException("Could not persist Deep Brain request");
    }

    public static void markExported(VaultDb db, String requestId) {
        ensure(db);
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("state", "EXPORTED");
        v.put("exported_at", now);
        v.put("updated_at", now);
        db.getWritableDatabase().update("v4_deep_brain_requests", v, "id=? AND state<>'APPLIED'", new String[]{requestId});
    }

    static Request load(SQLiteDatabase sql, String requestId) {
        ensure(sql);
        Cursor c = sql.query("v4_deep_brain_requests",
                new String[]{"id","question","context_json","situation_ids_json","memory_ids_json","world_ids_json","fact_ids_json","state","created_at","exported_at","applied_at"},
                "id=?", new String[]{requestId}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) return null;
            return new Request(c.getString(0), c.getString(1), c.getString(2),
                    list(c.getString(3)), list(c.getString(4)), list(c.getString(5)), list(c.getString(6)),
                    c.getString(7), c.getLong(8), c.getLong(9), c.getLong(10));
        } finally { c.close(); }
    }

    public static Status latest(VaultDb db) {
        ensure(db);
        SQLiteDatabase sql = db.getReadableDatabase();
        Cursor c = sql.rawQuery("SELECT r.id,r.question,r.state,r.created_at,COALESCE(x.answer,'') " +
                "FROM v4_deep_brain_requests r LEFT JOIN v4_deep_brain_responses x ON x.request_id=r.id " +
                "ORDER BY r.created_at DESC LIMIT 1", null);
        try {
            if (!c.moveToFirst()) return null;
            return new Status(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4));
        } finally { c.close(); }
    }

    static boolean requestExists(SQLiteDatabase sql, String requestId) {
        Cursor c = sql.rawQuery("SELECT 1 FROM v4_deep_brain_requests WHERE id=? LIMIT 1", new String[]{requestId});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    static void saveResponse(SQLiteDatabase sql, String responseId, CognitiveDeepBrainProtocolV4.ParsedResponse response,
                             String appliedSummaryJson, long appliedAt) {
        ensure(sql);
        ContentValues v = new ContentValues();
        v.put("id", responseId);
        v.put("request_id", response.requestId);
        v.put("answer", response.answer);
        v.put("raw_response", response.raw);
        v.put("response_json", response.json.toString());
        v.put("applied_summary_json", appliedSummaryJson == null ? "{}" : appliedSummaryJson);
        v.put("created_at", System.currentTimeMillis());
        v.put("applied_at", appliedAt);
        sql.insertWithOnConflict("v4_deep_brain_responses", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static void markApplied(SQLiteDatabase sql, String requestId, long when) {
        ContentValues v = new ContentValues();
        v.put("state", "APPLIED");
        v.put("applied_at", when);
        v.put("updated_at", when);
        sql.update("v4_deep_brain_requests", v, "id=?", new String[]{requestId});
    }

    private static String json(List<String> ids) {
        JSONArray a = new JSONArray();
        if (ids != null) for (String id : ids) if (id != null && !id.trim().isEmpty()) a.put(id.trim());
        return a.toString();
    }

    private static List<String> list(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) {
                String x = a.optString(i, "").trim();
                if (!x.isEmpty()) out.add(x);
            }
        } catch (Throwable ignored) {}
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    public static final class Request {
        public final String id, question, contextJson, state;
        public final List<String> situationIds, memoryIds, worldIds, factIds;
        public final long createdAt, exportedAt, appliedAt;
        Request(String id, String question, String contextJson, List<String> situationIds, List<String> memoryIds,
                List<String> worldIds, List<String> factIds, String state, long createdAt, long exportedAt, long appliedAt) {
            this.id=id; this.question=question; this.contextJson=contextJson; this.situationIds=situationIds;
            this.memoryIds=memoryIds; this.worldIds=worldIds; this.factIds=factIds; this.state=state;
            this.createdAt=createdAt; this.exportedAt=exportedAt; this.appliedAt=appliedAt;
        }
    }

    public static final class Status {
        public final String requestId, question, state, answer;
        public final long createdAt;
        Status(String requestId, String question, String state, long createdAt, String answer) {
            this.requestId=requestId; this.question=question; this.state=state; this.createdAt=createdAt; this.answer=answer;
        }
    }
}
