package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/**
 * V2 invariant: no raw signal may exist without an explicit cognitive outcome/state.
 *
 * Existing legacy rows are mapped conservatively from their already-persisted disposition/state.
 * SQLite triggers protect future inserts and normalize the temporary PENDING_ADJUDICATION alias
 * while older call sites are being retired.
 */
public final class CognitiveStateBackfillV2 {
    private static volatile boolean ready;
    private CognitiveStateBackfillV2(){}

    public static void ensure(SQLiteDatabase db){
        if(db==null||ready)return;
        synchronized(CognitiveStateBackfillV2.class){if(ready)return;install(db);ready=true;}
    }

    /** Package-visible regression hook for an isolated in-memory SQLite database. */
    static void installForTest(SQLiteDatabase db){if(db!=null)install(db);}

    private static void install(SQLiteDatabase db){
        db.execSQL("UPDATE raw_signals SET cognitive_state=CASE WHEN LOWER(COALESCE(final_reason,'')) LIKE '%analyz%' THEN 'LOCAL_RUNNING' ELSE 'LOCAL_QUEUED' END, final_reason=CASE WHEN COALESCE(final_reason,'')='' THEN 'legacy pending adjudication migrated to V2 queue' ELSE final_reason END WHERE cognitive_state='PENDING_ADJUDICATION'");
        db.execSQL("UPDATE raw_signals SET cognitive_state=CASE " +
                "WHEN UPPER(COALESCE(disposition,''))='IGNORE' THEN 'IGNORED_NOISE' " +
                "WHEN UPPER(COALESCE(disposition,''))='REVIEW' OR LOWER(COALESCE(state,''))='review' THEN 'REVIEW_REQUIRED' " +
                "WHEN COALESCE(promoted_item_id,0)>0 OR LOWER(COALESCE(state,''))='promoted' OR UPPER(COALESCE(disposition,'')) IN ('ACTION','WAITING','DECISION','MEMORY') THEN 'DERIVED' " +
                "ELSE 'CONTEXT_ONLY' END, " +
                "final_reason=CASE WHEN COALESCE(final_reason,'')='' THEN 'legacy raw signal assigned explicit V2 cognitive state' ELSE final_reason END " +
                "WHERE cognitive_state IS NULL OR TRIM(cognitive_state)=''");

        db.execSQL("DROP TRIGGER IF EXISTS trg_raw_signal_cognitive_state_v2");
        db.execSQL("CREATE TRIGGER trg_raw_signal_cognitive_state_v2 AFTER INSERT ON raw_signals " +
                "WHEN NEW.cognitive_state IS NULL OR TRIM(NEW.cognitive_state)='' BEGIN " +
                "UPDATE raw_signals SET cognitive_state=CASE " +
                "WHEN UPPER(COALESCE(NEW.disposition,''))='IGNORE' THEN 'IGNORED_NOISE' " +
                "WHEN UPPER(COALESCE(NEW.disposition,''))='REVIEW' OR LOWER(COALESCE(NEW.state,''))='review' THEN 'REVIEW_REQUIRED' " +
                "WHEN COALESCE(NEW.promoted_item_id,0)>0 OR LOWER(COALESCE(NEW.state,''))='promoted' OR UPPER(COALESCE(NEW.disposition,'')) IN ('ACTION','WAITING','DECISION','MEMORY') THEN 'DERIVED' " +
                "ELSE 'CONTEXT_ONLY' END, " +
                "final_reason=CASE WHEN COALESCE(NEW.final_reason,'')='' THEN 'capture path defaulted to explicit V2 cognitive state' ELSE NEW.final_reason END " +
                "WHERE id=NEW.id; END");

        db.execSQL("DROP TRIGGER IF EXISTS trg_raw_signal_pending_alias_v2");
        db.execSQL("CREATE TRIGGER trg_raw_signal_pending_alias_v2 AFTER INSERT ON raw_signals " +
                "WHEN UPPER(COALESCE(NEW.cognitive_state,''))='PENDING_ADJUDICATION' BEGIN " +
                "UPDATE raw_signals SET cognitive_state=CASE WHEN LOWER(COALESCE(NEW.final_reason,'')) LIKE '%analyz%' THEN 'LOCAL_RUNNING' ELSE 'LOCAL_QUEUED' END WHERE id=NEW.id; END");

        db.execSQL("DROP TRIGGER IF EXISTS trg_raw_signal_pending_alias_update_v2");
        db.execSQL("CREATE TRIGGER trg_raw_signal_pending_alias_update_v2 AFTER UPDATE OF cognitive_state,final_reason ON raw_signals " +
                "WHEN UPPER(COALESCE(NEW.cognitive_state,''))='PENDING_ADJUDICATION' BEGIN " +
                "UPDATE raw_signals SET cognitive_state=CASE WHEN LOWER(COALESCE(NEW.final_reason,'')) LIKE '%analyz%' THEN 'LOCAL_RUNNING' ELSE 'LOCAL_QUEUED' END WHERE id=NEW.id; END");
    }
}
