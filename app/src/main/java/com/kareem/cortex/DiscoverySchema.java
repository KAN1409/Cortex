package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Additive storage for Cortex Discovery Engine. Raw evidence remains authoritative. */
public final class DiscoverySchema {
    public static final String VERSION = "discovery_schema_001";
    private DiscoverySchema(){}

    public static void ensure(SQLiteDatabase db){
        UniversalEventStore.ensure(db);

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_annotations("+
                "item_id INTEGER PRIMARY KEY,"+
                "space TEXT NOT NULL,"+
                "topic_key TEXT NOT NULL,"+
                "topic_label TEXT,"+
                "claim_state TEXT,"+
                "source_type TEXT,"+
                "source_key TEXT,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_annotation_topic ON discovery_annotations(space,topic_key,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_situation_evidence("+
                "situation_id INTEGER NOT NULL,"+
                "item_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL DEFAULT 'supports',"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(situation_id,item_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_situation_evidence_item ON discovery_situation_evidence(item_id,situation_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_history_revisions("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "revision INTEGER NOT NULL,"+
                "history_text TEXT NOT NULL,"+
                "evidence_count INTEGER NOT NULL,"+
                "change_summary TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(situation_id,revision))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_history_situation ON discovery_history_revisions(situation_id,revision DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_candidates("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "family TEXT NOT NULL,"+
                "title TEXT NOT NULL,"+
                "body TEXT NOT NULL,"+
                "why_matters TEXT,"+
                "why_now TEXT,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "novelty REAL NOT NULL DEFAULT 0,"+
                "consequence REAL NOT NULL DEFAULT 0,"+
                "timeliness REAL NOT NULL DEFAULT 0,"+
                "evidence_count INTEGER NOT NULL DEFAULT 0,"+
                "score REAL NOT NULL DEFAULT 0,"+
                "state TEXT NOT NULL DEFAULT 'candidate',"+
                "critic_reason TEXT,"+
                "fingerprint TEXT NOT NULL UNIQUE,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_candidates_state ON discovery_candidates(state,score DESC,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_candidates_situation ON discovery_candidates(situation_id,family,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_candidate_evidence("+
                "candidate_id INTEGER NOT NULL,"+
                "item_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL DEFAULT 'supports',"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(candidate_id,item_id,relation))");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        long now=System.currentTimeMillis();
        db.execSQL("INSERT OR REPLACE INTO discovery_meta(key,value,updated_at) VALUES('schema_version',?,?)",
                new Object[]{VERSION,now});
    }
}
