package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Read-only freshness boundary between live canonical Situations and the last applied Deep Brain pass. */
public final class CognitiveReasoningFreshnessV4 {
    private CognitiveReasoningFreshnessV4() {}

    public static Snapshot current(VaultDb db) {
        if (db == null) return new Snapshot(0L, 0, 0L);
        CognitiveDeepBrainStoreV4.ensure(db);
        CognitiveStoreV4.ensure(db);
        SQLiteDatabase sql = db.getReadableDatabase();
        long latestAppliedAt = scalarLong(sql,
                "SELECT COALESCE(MAX(applied_at),0) FROM v4_deep_brain_requests WHERE state='APPLIED' AND applied_at>0");
        long newestSituationAt = scalarLong(sql,
                "SELECT COALESCE(MAX(created_at),0) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED')");
        int newOpen = scalarInt(sql,
                latestAppliedAt > 0
                        ? "SELECT COUNT(*) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED') AND created_at>" + latestAppliedAt
                        : "SELECT COUNT(*) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED')");
        return new Snapshot(latestAppliedAt, newOpen, newestSituationAt);
    }

    static boolean isNew(long situationCreatedAt, long latestAppliedAt) {
        if (situationCreatedAt <= 0) return false;
        return latestAppliedAt <= 0 || situationCreatedAt > latestAppliedAt;
    }

    private static long scalarLong(SQLiteDatabase sql, String query) {
        Cursor c = sql.rawQuery(query, null);
        try { return c.moveToFirst() ? c.getLong(0) : 0L; }
        finally { c.close(); }
    }

    private static int scalarInt(SQLiteDatabase sql, String query) {
        Cursor c = sql.rawQuery(query, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    public static final class Snapshot {
        public final long latestAppliedAt, newestSituationAt;
        public final int newOpenSituations;
        Snapshot(long latestAppliedAt, int newOpenSituations, long newestSituationAt) {
            this.latestAppliedAt = latestAppliedAt;
            this.newOpenSituations = Math.max(0, newOpenSituations);
            this.newestSituationAt = newestSituationAt;
        }
        public boolean neverReasoned() { return latestAppliedAt <= 0; }
        public boolean hasNewContext() { return newOpenSituations > 0; }
    }
}
