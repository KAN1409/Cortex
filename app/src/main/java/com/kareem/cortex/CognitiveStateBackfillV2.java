package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/**
 * V2 invariant: no raw signal may exist without an explicit cognitive outcome/state.
 *
 * Existing legacy rows are mapped conservatively from their already-persisted disposition/state.
 * A SQLite trigger protects future inserts from any capture path that has not yet migrated to V2.
 */
public final class CognitiveStateBackfillV2 {
    private static volatile boolean ready;
    private CognitiveStateBackfillV2(){}

    public static void ensure(SQLiteDatabase db){
        if(db==null||ready)return;
        synchronized(CognitiveStateBackfillV2.class){
            if(ready)return;
            db.execSQL("UPDATE raw_signals SET cognitive_state='LOCAL_QUEUED', final_reason=CASE WHEN COALESCE(final_reason,'')='' THEN 'legacy pending adjudication migrated to V2 queue' ELSE final_reason END WHERE cognitive_state='PENDING_ADJUDICATION'");
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
            ready=true;
        }
    }
}
