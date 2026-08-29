package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Plumbing/audit store for local connector health and idempotent event delivery. Not a truth store. */
public final class CortexLocalBusStoreV1 {
    private CortexLocalBusStoreV1() {}

    public static void ensure(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        ensure(db.getWritableDatabase());
    }

    static void ensure(SQLiteDatabase sql) {
        sql.execSQL("CREATE TABLE IF NOT EXISTS connector_clients(" +
                "connector_id TEXT PRIMARY KEY," +
                "package_name TEXT NOT NULL," +
                "capabilities_json TEXT NOT NULL DEFAULT '[]'," +
                "source_priority INTEGER NOT NULL DEFAULT 0," +
                "last_seen_at INTEGER NOT NULL DEFAULT 0," +
                "last_event_at INTEGER NOT NULL DEFAULT 0," +
                "accepted_events INTEGER NOT NULL DEFAULT 0," +
                "rejected_events INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS connector_ingest_events(" +
                "event_id TEXT PRIMARY KEY," +
                "connector_id TEXT NOT NULL," +
                "connector_package TEXT NOT NULL," +
                "source_type TEXT NOT NULL," +
                "source_package TEXT NOT NULL," +
                "occurred_at INTEGER NOT NULL," +
                "received_at INTEGER NOT NULL," +
                "state TEXT NOT NULL," +
                "signal_id INTEGER NOT NULL DEFAULT 0," +
                "detail TEXT," +
                "updated_at INTEGER NOT NULL)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_connector_ingest_connector ON connector_ingest_events(connector_id,received_at DESC)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS idx_connector_ingest_signal ON connector_ingest_events(signal_id)");
    }

    public static void hello(VaultDb db, CortexConnectorRegistryV1.Identity id, String capabilitiesJson) {
        ensure(db); long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("connector_id", id.connectorId);
        v.put("package_name", id.packageName);
        v.put("capabilities_json", cleanJson(capabilitiesJson));
        v.put("source_priority", id.sourcePriority);
        v.put("last_seen_at", now);
        v.put("updated_at", now);
        SQLiteDatabase sql = db.getWritableDatabase();
        long row = sql.insertWithOnConflict("connector_clients", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (row < 0) {
            ContentValues u = new ContentValues();
            u.put("package_name", id.packageName);
            u.put("capabilities_json", cleanJson(capabilitiesJson));
            u.put("source_priority", id.sourcePriority);
            u.put("last_seen_at", now);
            u.put("updated_at", now);
            sql.update("connector_clients", u, "connector_id=?", new String[]{id.connectorId});
        }
    }

    /** Returns true if this connector event was already accepted earlier. */
    public static boolean alreadyAccepted(VaultDb db, String eventId) {
        ensure(db);
        Cursor c = db.getReadableDatabase().rawQuery("SELECT state FROM connector_ingest_events WHERE event_id=? LIMIT 1", new String[]{eventId});
        try { return c.moveToFirst() && "ACCEPTED".equals(c.getString(0)); }
        finally { c.close(); }
    }

    public static void recordReceived(VaultDb db, CortexConnectorRegistryV1.Identity id, CortexLocalBusProtocolV1.Event event) {
        ensure(db); long now = System.currentTimeMillis(); SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("event_id", event.eventId); v.put("connector_id", id.connectorId); v.put("connector_package", id.packageName);
        v.put("source_type", event.sourceType); v.put("source_package", event.sourcePackage); v.put("occurred_at", event.occurredAt);
        v.put("received_at", now); v.put("state", "RECEIVED"); v.put("signal_id", 0); v.put("updated_at", now);
        sql.insertWithOnConflict("connector_ingest_events", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        touchClient(sql, id, now, false, false);
    }

    public static void accepted(VaultDb db, CortexConnectorRegistryV1.Identity id, String eventId, long signalId) {
        ensure(db); long now = System.currentTimeMillis(); SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues(); v.put("state", "ACCEPTED"); v.put("signal_id", signalId); v.put("detail", ""); v.put("updated_at", now);
        sql.update("connector_ingest_events", v, "event_id=?", new String[]{eventId});
        touchClient(sql, id, now, true, false);
    }

    public static void rejected(VaultDb db, CortexConnectorRegistryV1.Identity id, String eventId, String detail) {
        ensure(db); long now = System.currentTimeMillis(); SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues(); v.put("state", "REJECTED"); v.put("detail", clip(detail, 500)); v.put("updated_at", now);
        sql.update("connector_ingest_events", v, "event_id=?", new String[]{eventId});
        touchClient(sql, id, now, false, true);
    }

    private static void touchClient(SQLiteDatabase sql, CortexConnectorRegistryV1.Identity id, long now, boolean accepted, boolean rejected) {
        ContentValues base = new ContentValues(); base.put("connector_id", id.connectorId); base.put("package_name", id.packageName); base.put("source_priority", id.sourcePriority); base.put("last_seen_at", now); base.put("last_event_at", now); base.put("updated_at", now);
        long row = sql.insertWithOnConflict("connector_clients", null, base, SQLiteDatabase.CONFLICT_IGNORE);
        if (row >= 0) {
            if (accepted || rejected) {
                ContentValues c = new ContentValues(); c.put(accepted ? "accepted_events" : "rejected_events", 1); sql.update("connector_clients", c, "connector_id=?", new String[]{id.connectorId});
            }
            return;
        }
        String counter = accepted ? "accepted_events" : (rejected ? "rejected_events" : "");
        sql.execSQL("UPDATE connector_clients SET last_seen_at=?,last_event_at=?,updated_at=?" + (counter.isEmpty() ? "" : "," + counter + "=" + counter + "+1") + " WHERE connector_id=?", new Object[]{now, now, now, id.connectorId});
    }

    private static String cleanJson(String raw) { String x = raw == null ? "[]" : raw.trim(); return x.isEmpty() ? "[]" : x; }
    private static String clip(String s, int n) { String x = s == null ? "" : s.trim(); return x.length() <= n ? x : x.substring(0, n); }
}
