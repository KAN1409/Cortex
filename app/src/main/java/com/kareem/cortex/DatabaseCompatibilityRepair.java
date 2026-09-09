package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/**
 * Tiny, idempotent compatibility repair for persisted databases created by older Cortex builds.
 *
 * Some Capture builds queried ue_streams.source_key while the canonical stream schema stores
 * the same identity as external_key. Existing installs can therefore contain a valid ue_streams
 * table without source_key and crash before Capture can render. This repair adds only the missing
 * compatibility alias and backfills it from external_key. It never deletes or rebuilds data.
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
            // Never make process bootstrap fail because a best-effort compatibility repair failed.
            // The normal DB owner will still report any remaining schema problem through CrashRecorder.
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    static void repair(SQLiteDatabase db) {
        if (db == null || !tableExists(db, "ue_streams")) return;
        if (!columnExists(db, "ue_streams", "source_key")) {
            db.execSQL("ALTER TABLE ue_streams ADD COLUMN source_key TEXT");
        }
        if (columnExists(db, "ue_streams", "external_key")) {
            db.execSQL("UPDATE ue_streams SET source_key=external_key WHERE source_key IS NULL OR TRIM(source_key)='' ");
        }
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
            while (c.moveToNext()) {
                if (column.equals(c.getString(1))) return true;
            }
            return false;
        } finally {
            if (c != null) c.close();
        }
    }
}
