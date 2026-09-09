package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-destructive compatibility repair for persisted databases created by older Cortex builds.
 * Only missing additive columns are introduced; no table is dropped, rebuilt or truncated.
 */
public final class DatabaseCompatibilityRepair {
    private static final String DB_NAME = "cortex.db";
    private DatabaseCompatibilityRepair() {}

    public static void repairExistingDatabase(Context context) {
        if (context == null) return;
        File file = context.getDatabasePath(DB_NAME);
        if (file == null || !file.exists()) return;
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            repair(db);
        } catch (Throwable ignored) {
            // Never make process bootstrap fail because a compatibility repair failed.
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    static void repair(SQLiteDatabase db) {
        if (db == null) return;
        db.beginTransaction();
        try {
            if (tableExists(db, "ue_streams")) {
                ensureColumn(db, "ue_streams", "source_type", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_streams", "external_key", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_streams", "source_key", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_streams", "last_seen_at", "INTEGER DEFAULT 0");
                db.execSQL("UPDATE ue_streams SET source_key=external_key WHERE (source_key IS NULL OR TRIM(source_key)='') AND external_key IS NOT NULL");
            }
            if (tableExists(db, "ue_raw_observations")) {
                ensureColumn(db, "ue_raw_observations", "source_type", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "source_key", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "event_type", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "platform_hint", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "technical_type", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "title", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "body", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_raw_observations", "occurred_at", "INTEGER DEFAULT 0");
            }
            if (tableExists(db, "ue_semantic_events")) {
                ensureColumn(db, "ue_semantic_events", "raw_observation_id", "INTEGER DEFAULT 0");
                ensureColumn(db, "ue_semantic_events", "stream_id", "INTEGER DEFAULT 0");
                ensureColumn(db, "ue_semantic_events", "semantic_type", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_semantic_events", "intent", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_semantic_events", "subject", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_semantic_events", "summary", "TEXT DEFAULT ''");
                ensureColumn(db, "ue_semantic_events", "confidence", "REAL DEFAULT 0");
                ensureColumn(db, "ue_semantic_events", "semantic_state", "TEXT DEFAULT 'complete'");
                ensureColumn(db, "ue_semantic_events", "meaningful", "INTEGER DEFAULT 1");
                ensureColumn(db, "ue_semantic_events", "occurred_at", "INTEGER DEFAULT 0");
                ensureColumn(db, "ue_semantic_events", "superseded_by", "INTEGER DEFAULT 0");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Human-readable, read-only capture schema audit for diagnostics/tests. */
    static String captureSchemaReport(SQLiteDatabase db) {
        if (db == null) return "database unavailable";
        List<String> missing = new ArrayList<>();
        require(db, missing, "ue_streams", "source_type", "external_key", "last_seen_at");
        require(db, missing, "ue_raw_observations", "source_type", "source_key", "event_type", "title", "body", "occurred_at");
        require(db, missing, "ue_semantic_events", "raw_observation_id", "stream_id", "semantic_type", "subject", "summary", "confidence", "semantic_state", "occurred_at", "superseded_by");
        return missing.isEmpty() ? "OK" : "MISSING: " + String.join(", ", missing);
    }

    private static void require(SQLiteDatabase db, List<String> missing, String table, String... columns) {
        if (!tableExists(db, table)) {
            missing.add(table + ".<table>");
            return;
        }
        for (String column : columns) if (!columnExists(db, table, column)) missing.add(table + "." + column);
    }

    private static void ensureColumn(SQLiteDatabase db, String table, String column, String definition) {
        if (!columnExists(db, table, column)) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    static boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", new String[]{table});
            return c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }

    static boolean columnExists(SQLiteDatabase db, String table, String column) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c.moveToNext()) if (column.equals(c.getString(1))) return true;
            return false;
        } finally {
            if (c != null) c.close();
        }
    }
}
