package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Clean-slate truth schema. Raw evidence remains authoritative and immutable; truth is derived, grounded state. */
public final class TruthSchema {
    public static final String REVISION="truth_001";
    private TruthSchema(){}

    public static void ensure(VaultDb db){
        if(db==null)return;
        CognitiveStore.ensure(db);
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS truth_events("+
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "event_key TEXT NOT NULL UNIQUE,"+
            "origin_type TEXT NOT NULL,"+
            "origin_id INTEGER NOT NULL DEFAULT 0,"+
            "signal_id INTEGER NOT NULL DEFAULT 0,"+
            "memory_id INTEGER NOT NULL DEFAULT 0,"+
            "thread_id INTEGER NOT NULL DEFAULT 0,"+
            "event_type TEXT NOT NULL DEFAULT 'EVIDENCE',"+
            "source_key TEXT,"+
            "title TEXT,"+
            "body TEXT,"+
            "occurred_at INTEGER NOT NULL,"+
            "confidence REAL NOT NULL DEFAULT 0,"+
            "metadata_json TEXT,"+
            "created_at INTEGER NOT NULL,"+
            "updated_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_events_time ON truth_events(occurred_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_events_origin ON truth_events(origin_type,origin_id)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_events_thread ON truth_events(thread_id,occurred_at DESC)");

        s.execSQL("CREATE TABLE IF NOT EXISTS truth_objects("+
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "kind TEXT NOT NULL,"+
            "state TEXT NOT NULL DEFAULT 'OPEN',"+
            "title TEXT NOT NULL,"+
            "body TEXT,"+
            "source_key TEXT,"+
            "confidence REAL NOT NULL DEFAULT 0,"+
            "importance INTEGER NOT NULL DEFAULT 0,"+
            "semantic_key TEXT NOT NULL,"+
            "thread_id INTEGER NOT NULL DEFAULT 0,"+
            "anchor_event_id INTEGER NOT NULL DEFAULT 0,"+
            "anchor_signal_id INTEGER NOT NULL DEFAULT 0,"+
            "anchor_memory_id INTEGER NOT NULL DEFAULT 0,"+
            "first_seen_at INTEGER NOT NULL,"+
            "last_seen_at INTEGER NOT NULL,"+
            "resolved_at INTEGER NOT NULL DEFAULT 0,"+
            "metadata_json TEXT)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_kind_state ON truth_objects(kind,state,importance DESC,last_seen_at DESC)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_anchor_event ON truth_objects(anchor_event_id,kind,state)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_anchor_signal ON truth_objects(anchor_signal_id,kind,state)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_anchor_memory ON truth_objects(anchor_memory_id,kind,state)");
        s.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_truth_active_semantic ON truth_objects(kind,semantic_key) WHERE state IN ('OPEN','CONFIRMED')");

        s.execSQL("CREATE TABLE IF NOT EXISTS truth_evidence("+
            "truth_id INTEGER NOT NULL,"+
            "event_id INTEGER NOT NULL,"+
            "relation TEXT NOT NULL DEFAULT 'supports',"+
            "confidence REAL NOT NULL DEFAULT 0,"+
            "created_at INTEGER NOT NULL,"+
            "PRIMARY KEY(truth_id,event_id,relation))");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_evidence_event ON truth_evidence(event_id,truth_id)");

        s.execSQL("CREATE TABLE IF NOT EXISTS truth_transitions("+
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "truth_id INTEGER NOT NULL,"+
            "from_state TEXT,"+
            "to_state TEXT NOT NULL,"+
            "reason TEXT,"+
            "evidence_event_id INTEGER NOT NULL DEFAULT 0,"+
            "created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_truth_transitions_truth ON truth_transitions(truth_id,created_at DESC)");
        s.execSQL("INSERT OR REPLACE INTO schema_meta(key,value,updated_at) VALUES('truth_schema','"+REVISION+"',strftime('%s','now')*1000)");
    }
}
