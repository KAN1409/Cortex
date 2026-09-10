package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * Makes recent completed universal semantic evidence searchable by the existing Brain retrieval
 * path. Raw UE rows remain the source of truth; this is a rebuildable read projection only.
 */
public final class SemanticMemoryBridge {
    public static final String VERSION = "semantic_memory_bridge_002";
    private static final long WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;
    private SemanticMemoryBridge() {}

    private static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_brain_memory_bridge(" +
                "semantic_event_id INTEGER PRIMARY KEY," +
                "knowledge_item_id INTEGER NOT NULL," +
                "projection_version TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
    }

    public static int sync(VaultDb vault, int maxRows) {
        if (vault == null) return 0;
        SQLiteDatabase db = vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        ensure(db);
        int limit = Math.max(1, Math.min(400, maxRows));
        long since = System.currentTimeMillis() - WINDOW_MS;
        Cursor c = db.rawQuery(
                "SELECT e.id,COALESCE(e.semantic_type,''),COALESCE(e.subject,''),COALESCE(e.summary,'')," +
                "COALESCE(e.confidence,0),COALESCE(e.occurred_at,0),COALESCE(r.source_key,'') " +
                "FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE e.semantic_state='complete' AND e.superseded_by=0 AND e.occurred_at>=? " +
                "AND COALESCE(e.semantic_type,'')<>'technical_state' AND COALESCE(r.event_type,'')<>'removed' " +
                "AND NOT EXISTS(SELECT 1 FROM ue_brain_memory_bridge b WHERE b.semantic_event_id=e.id) " +
                "ORDER BY e.occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(since), String.valueOf(limit)});
        int synced = 0;
        try {
            while (c.moveToNext()) {
                long eventId = c.getLong(0);
                String type = nz(c.getString(1));
                String subject = nz(c.getString(2));
                String summary = nz(c.getString(3));
                double confidence = c.getDouble(4);
                long occurredAt = c.getLong(5);
                String source = nz(c.getString(6));
                if (confidence < 0.70 || (subject.isEmpty() && summary.isEmpty())) {
                    markSkipped(db,eventId);
                    continue;
                }

                String fingerprint = Fingerprint.text("ue-brain-memory|" + eventId);
                Cursor old = db.rawQuery("SELECT id FROM knowledge_items WHERE fingerprint=? LIMIT 1", new String[]{fingerprint});
                long itemId = old.moveToFirst() ? old.getLong(0) : 0;
                old.close();

                if (itemId <= 0) {
                    JSONObject meta = new JSONObject();
                    try {
                        meta.put("semantic_event_id", eventId);
                        meta.put("semantic_type", type);
                        meta.put("source_key", source);
                        meta.put("confidence", confidence);
                        meta.put("occurred_at", occurredAt);
                        meta.put("projection", VERSION);
                    } catch (Exception ignored) {}
                    String title = CanonicalPresentation.cleanTitle("memory", type,
                            subject.isEmpty() ? source : subject, subject);
                    String body = CanonicalPresentation.cleanBody(summary.isEmpty() ? subject : summary);
                    long inserted = vault.insert("SEMANTIC_EVENT", "semantic_bridge", title, body,
                            "Live context", "semantic," + type, "", fingerprint, meta.toString());
                    itemId = inserted < 0 ? -inserted : inserted;
                }
                if (itemId > 0) {
                    mark(db,eventId,itemId);
                    synced++;
                }
            }
        } finally {
            c.close();
        }
        return synced;
    }

    public static boolean hasBacklog(VaultDb vault) {
        if (vault == null) return false;
        SQLiteDatabase db = vault.getReadableDatabase();
        ensure(db);
        long since = System.currentTimeMillis() - WINDOW_MS;
        Cursor c = db.rawQuery(
                "SELECT 1 FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE e.semantic_state='complete' AND e.superseded_by=0 AND e.occurred_at>=? " +
                "AND COALESCE(e.semantic_type,'')<>'technical_state' AND COALESCE(r.event_type,'')<>'removed' " +
                "AND NOT EXISTS(SELECT 1 FROM ue_brain_memory_bridge b WHERE b.semantic_event_id=e.id) LIMIT 1",
                new String[]{String.valueOf(since)});
        boolean yes = c.moveToFirst();
        c.close();
        return yes;
    }

    private static void mark(SQLiteDatabase db,long eventId,long itemId){
        ContentValues v=new ContentValues();v.put("semantic_event_id",eventId);v.put("knowledge_item_id",itemId);v.put("projection_version",VERSION);v.put("created_at",System.currentTimeMillis());
        db.insertWithOnConflict("ue_brain_memory_bridge",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static void markSkipped(SQLiteDatabase db,long eventId){mark(db,eventId,0);}
    private static String nz(String s){return s==null?"":s.trim();}
}
