package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Read-only freshness boundary between live canonical Situations and applied Deep Brain passes. */
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
                "SELECT COALESCE(MAX(updated_at),0) FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED')");
        return new Snapshot(latestAppliedAt, newOpenCount(sql), newestSituationAt);
    }

    /** Legacy/global helper retained for regression compatibility. */
    static boolean isNew(long situationChangedAt, long latestAppliedAt) {
        if (situationChangedAt <= 0) return false;
        return latestAppliedAt <= 0 || situationChangedAt > latestAppliedAt;
    }

    static boolean isNew(SQLiteDatabase sql,String situationId,long situationChangedAt){
        if(sql==null||situationId==null||situationId.trim().isEmpty()||situationChangedAt<=0)return false;
        return lastAppliedForSituation(sql,situationId)<situationChangedAt;
    }

    static long lastAppliedForSituation(SQLiteDatabase sql,String situationId){
        if(sql==null||situationId==null||situationId.trim().isEmpty())return 0L;
        Cursor c=sql.rawQuery(
                "SELECT COALESCE(MAX(applied_at),0) FROM v4_deep_brain_requests " +
                "WHERE state='APPLIED' AND situation_ids_json LIKE ?",
                new String[]{"%\""+situationId.trim()+"\"%"});
        try{return c.moveToFirst()?c.getLong(0):0L;}finally{c.close();}
    }

    /**
     * Count open Situations not covered by any applied request at-or-after their latest change.
     * Freshness is per Situation: a bounded pass cannot make an omitted item look considered.
     */
    static int newOpenCount(SQLiteDatabase sql){
        if(sql==null)return 0;
        return scalarInt(sql,
                "SELECT COUNT(*) FROM v4_situations s " +
                "WHERE s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED') " +
                "AND NOT EXISTS (SELECT 1 FROM v4_deep_brain_requests r " +
                "WHERE r.state='APPLIED' AND r.applied_at>=s.updated_at " +
                "AND r.situation_ids_json LIKE '%\"' || s.id || '\"%')");
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
