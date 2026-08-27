package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Persistent attention/open-loop layer. Kept additive so existing cognitive schema remains backward compatible. */
public final class CortexAttentionSchema {
    public static final String REVISION="attention_003";
    private CortexAttentionSchema(){}

    public static void ensure(VaultDb db){if(db!=null)ensure(db.getWritableDatabase());}
    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS open_loops(id INTEGER PRIMARY KEY AUTOINCREMENT,fingerprint TEXT NOT NULL UNIQUE,type TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'OPEN',subject TEXT NOT NULL,owner TEXT DEFAULT 'USER',person_key TEXT DEFAULT '',project_key TEXT DEFAULT '',thread_id INTEGER DEFAULT 0,anchor_signal_id INTEGER DEFAULT 0,user_committed INTEGER DEFAULT 0,due_at INTEGER DEFAULT 0,follow_up_at INTEGER DEFAULT 0,confidence REAL DEFAULT 0,resolution_kind TEXT DEFAULT '',resolution_json TEXT DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,resolved_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_open_loops_active ON open_loops(state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_open_loops_thread ON open_loops(thread_id,state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_open_loops_person ON open_loops(person_key,state,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS open_loop_evidence(loop_id INTEGER NOT NULL,signal_id INTEGER NOT NULL,relation TEXT NOT NULL DEFAULT 'supports',confidence REAL DEFAULT 1,created_at INTEGER NOT NULL,PRIMARY KEY(loop_id,signal_id,relation))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_open_loop_evidence_signal ON open_loop_evidence(signal_id,loop_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS attention_assessments(id INTEGER PRIMARY KEY AUTOINCREMENT,entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,score REAL NOT NULL,interrupt_score REAL NOT NULL,level TEXT NOT NULL,confidence REAL NOT NULL,urgency REAL DEFAULT 0,importance REAL DEFAULT 0,action_required REAL DEFAULT 0,commitment_strength REAL DEFAULT 0,unresolvedness REAL DEFAULT 0,context_relevance REAL DEFAULT 0,recency REAL DEFAULT 0,novelty REAL DEFAULT 0,interruption_cost REAL DEFAULT 0,primary_reason TEXT DEFAULT '',suggested_action TEXT DEFAULT '',actionability TEXT DEFAULT 'NONE',is_time_sensitive INTEGER DEFAULT 0,engine_version TEXT NOT NULL,evaluated_at INTEGER NOT NULL,UNIQUE(entity_type,entity_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attention_score ON attention_assessments(score DESC,evaluated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS attention_action_proposals(id INTEGER PRIMARY KEY AUTOINCREMENT,entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,action_type TEXT NOT NULL,label TEXT NOT NULL,reason TEXT DEFAULT '',expected_outcome TEXT DEFAULT '',risk TEXT NOT NULL,confidence REAL NOT NULL,status TEXT NOT NULL DEFAULT 'AVAILABLE',planner_version TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(entity_type,entity_id,action_type))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attention_actions_entity ON attention_action_proposals(entity_type,entity_id,status,confidence DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS attention_feed(id INTEGER PRIMARY KEY AUTOINCREMENT,entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,semantic_group TEXT DEFAULT '',person_key TEXT DEFAULT '',project_key TEXT DEFAULT '',thread_id INTEGER DEFAULT 0,section TEXT NOT NULL,title TEXT NOT NULL,subtitle TEXT DEFAULT '',rank REAL NOT NULL,confidence REAL NOT NULL,variant TEXT NOT NULL DEFAULT 'ACTIVE',status_dot TEXT DEFAULT '',primary_action TEXT DEFAULT '',explanation TEXT DEFAULT '',source_count INTEGER DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(entity_type,entity_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attention_feed_section ON attention_feed(section,rank DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attention_feed_group ON attention_feed(semantic_group,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS attention_history(entity_type TEXT NOT NULL,entity_id INTEGER NOT NULL,first_surfaced_at INTEGER DEFAULT 0,last_surfaced_at INTEGER DEFAULT 0,surface_count INTEGER DEFAULT 0,opened_count INTEGER DEFAULT 0,dismissed_count INTEGER DEFAULT 0,snoozed_until INTEGER DEFAULT 0,last_action_at INTEGER DEFAULT 0,PRIMARY KEY(entity_type,entity_id))");
        db.execSQL("INSERT OR REPLACE INTO schema_meta(key,value,updated_at) VALUES('attention_schema','"+REVISION+"',strftime('%s','now')*1000)");
    }
}
