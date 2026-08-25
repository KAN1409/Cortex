package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** First-class live-context schema. Kept separate from durable memory so context can evolve safely. */
public final class ContextSchema {
    private ContextSchema(){}

    public static void ensure(VaultDb db){if(db!=null)ensure(db.getWritableDatabase());}
    public static void ensure(SQLiteDatabase db){
        if(db==null)return;
        db.execSQL("CREATE TABLE IF NOT EXISTS contexts("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "stable_key TEXT NOT NULL UNIQUE,"+
                "title TEXT NOT NULL,"+
                "scope TEXT NOT NULL DEFAULT 'TASK',"+
                "lifecycle TEXT NOT NULL DEFAULT 'ACTIVE',"+
                "confidence REAL DEFAULT 0,"+
                "goal TEXT DEFAULT '',"+
                "summary TEXT DEFAULT '',"+
                "metadata_json TEXT DEFAULT '{}',"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL,"+
                "last_active_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contexts_active ON contexts(lifecycle,last_active_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contexts_scope ON contexts(scope,last_active_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS context_episodes("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "context_id INTEGER NOT NULL,"+
                "state TEXT NOT NULL DEFAULT 'ACTIVE',"+
                "transition TEXT DEFAULT 'START_NEW',"+
                "reason TEXT DEFAULT '',"+
                "confidence REAL DEFAULT 0,"+
                "anchor_signal_id INTEGER DEFAULT 0,"+
                "started_at INTEGER NOT NULL,"+
                "ended_at INTEGER DEFAULT 0,"+
                "metadata_json TEXT DEFAULT '{}')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_episode_open ON context_episodes(context_id,state,started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_episode_time ON context_episodes(started_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS context_stack_state("+
                "context_id INTEGER PRIMARY KEY,"+
                "role TEXT NOT NULL DEFAULT 'BACKGROUND',"+
                "priority INTEGER DEFAULT 50,"+
                "confidence REAL DEFAULT 0,"+
                "last_evidence_at INTEGER NOT NULL,"+
                "last_transition_at INTEGER NOT NULL,"+
                "transition_reason TEXT DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_stack_role ON context_stack_state(role,priority DESC,last_evidence_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS context_snapshots("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "context_id INTEGER NOT NULL,"+
                "episode_id INTEGER DEFAULT 0,"+
                "title TEXT DEFAULT '',"+
                "goal TEXT DEFAULT '',"+
                "current_activity TEXT DEFAULT '',"+
                "open_loop TEXT DEFAULT '',"+
                "next_step TEXT DEFAULT '',"+
                "evidence_summary TEXT DEFAULT '',"+
                "privacy_json TEXT DEFAULT '{}',"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_snapshot_recent ON context_snapshots(context_id,created_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS context_fingerprint_features("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "context_id INTEGER NOT NULL,"+
                "feature_type TEXT NOT NULL,"+
                "feature_key TEXT NOT NULL,"+
                "weight REAL DEFAULT 0,"+
                "positive_count INTEGER DEFAULT 0,"+
                "negative_count INTEGER DEFAULT 0,"+
                "updated_at INTEGER NOT NULL,"+
                "UNIQUE(context_id,feature_type,feature_key))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_feature_lookup ON context_fingerprint_features(feature_type,feature_key,weight DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS context_feedback("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "context_id INTEGER DEFAULT 0,"+
                "other_context_id INTEGER DEFAULT 0,"+
                "event_type TEXT NOT NULL,"+
                "value_json TEXT DEFAULT '{}',"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_context_feedback_recent ON context_feedback(context_id,event_type,created_at DESC)");
    }
}
