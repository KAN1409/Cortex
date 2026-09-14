package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Additive storage for Discovery Engine v3. Raw evidence remains authoritative. */
public final class DiscoveryV3Schema {
    public static final String VERSION="discovery_v3_001";
    private static volatile boolean ready=false;
    private DiscoveryV3Schema(){}

    public static void ensure(SQLiteDatabase db){
        if(ready)return;
        synchronized(DiscoveryV3Schema.class){
            if(ready)return;
        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_situations("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_key TEXT NOT NULL UNIQUE,"+
                "space TEXT NOT NULL,"+
                "domain TEXT NOT NULL,"+
                "label TEXT NOT NULL,"+
                "state TEXT NOT NULL DEFAULT 'active',"+
                "evidence_count INTEGER NOT NULL DEFAULT 0,"+
                "first_seen INTEGER NOT NULL,"+
                "last_seen INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_situation_space ON discovery_v3_situations(space,last_seen DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_evidence("+
                "item_id INTEGER PRIMARY KEY,"+
                "situation_id INTEGER NOT NULL,"+
                "space TEXT NOT NULL,"+
                "source_type TEXT NOT NULL,"+
                "source_key TEXT,"+
                "quality REAL NOT NULL DEFAULT 0,"+
                "processed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_evidence_situation ON discovery_v3_evidence(situation_id,item_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_claims("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "item_id INTEGER NOT NULL,"+
                "subject_key TEXT NOT NULL,"+
                "subject_label TEXT NOT NULL,"+
                "predicate TEXT NOT NULL,"+
                "value TEXT NOT NULL,"+
                "value_norm TEXT NOT NULL,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "observed_at INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(item_id,subject_key,predicate,value_norm))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_claim_subject ON discovery_v3_claims(situation_id,subject_key,predicate,observed_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_history("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "revision INTEGER NOT NULL,"+
                "body TEXT NOT NULL,"+
                "evidence_count INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(situation_id,revision))");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_insights("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "issue_key TEXT NOT NULL UNIQUE,"+
                "family TEXT NOT NULL,"+
                "domain TEXT NOT NULL,"+
                "title TEXT NOT NULL,"+
                "what_found TEXT NOT NULL,"+
                "why_matters TEXT NOT NULL,"+
                "why_now TEXT NOT NULL,"+
                "suggested_action TEXT NOT NULL,"+
                "confidence REAL NOT NULL,"+
                "score REAL NOT NULL,"+
                "state TEXT NOT NULL,"+
                "quality_reason TEXT,"+
                "evidence_count INTEGER NOT NULL,"+
                "last_evidence_at INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_insight_feed ON discovery_v3_insights(state,score DESC,last_evidence_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_insight_situation ON discovery_v3_insights(situation_id,family,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_insight_evidence("+
                "insight_id INTEGER NOT NULL,"+
                "item_id INTEGER NOT NULL,"+
                "role TEXT NOT NULL DEFAULT 'supports',"+
                "PRIMARY KEY(insight_id,item_id,role))");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_feedback("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "insight_id INTEGER NOT NULL,"+
                "family TEXT NOT NULL,"+
                "event TEXT NOT NULL,"+
                "weight REAL NOT NULL,"+
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_research("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "insight_id INTEGER NOT NULL,"+
                "query_text TEXT NOT NULL,"+
                "state TEXT NOT NULL DEFAULT 'queued',"+
                "result_text TEXT,"+
                "citations_json TEXT,"+
                "provider TEXT,"+
                "error TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("INSERT OR REPLACE INTO discovery_v3_meta(key,value,updated_at) VALUES('schema_version',?,?)",
                new Object[]{VERSION,System.currentTimeMillis()});
        ready=true;
        }
    }
}
