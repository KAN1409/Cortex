package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * Makes recent completed universal semantic evidence searchable by the existing Brain retrieval
 * path. Raw UE rows remain the source of truth; this is a rebuildable read projection only.
 */
public final class SemanticMemoryBridge {
    public static final String VERSION = "semantic_memory_bridge_001";
    private SemanticMemoryBridge() {}

    public static int sync(VaultDb vault, int maxRows) {
        if (vault == null) return 0;
        SQLiteDatabase db = vault.getWritableDatabase();
        UniversalEventStore.ensure(db);
        int limit = Math.max(1, Math.min(400, maxRows));
        long since = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L;
        Cursor c = db.rawQuery(
                "SELECT e.id,COALESCE(e.semantic_type,''),COALESCE(e.subject,''),COALESCE(e.summary,'')," +
                "COALESCE(e.confidence,0),COALESCE(e.occurred_at,0),COALESCE(r.source_key,''),COALESCE(r.event_type,'') " +
                "FROM ue_semantic_events e JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "WHERE e.semantic_state='complete' AND e.superseded_by=0 AND e.occurred_at>=? " +
                "AND COALESCE(e.semantic_type,'')<>'technical_state' AND COALESCE(r.event_type,'')<>'removed' " +
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
                if (confidence < 0.70 || (subject.isEmpty() && summary.isEmpty())) continue;

                String fingerprint = Fingerprint.text("ue-brain-memory|" + eventId);
                Cursor old = db.rawQuery("SELECT id FROM knowledge_items WHERE fingerprint=? LIMIT 1", new String[]{fingerprint});
                boolean exists = old.moveToFirst();
                old.close();
                if (exists) continue;

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
                long id = vault.insert("SEMANTIC_EVENT", "semantic_bridge", title, body,
                        "Live context", "semantic," + type, "", fingerprint, meta.toString());
                if (id != 0) synced++;
            }
        } finally {
            c.close();
        }
        return synced;
    }
}
