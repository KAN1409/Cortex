package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Additive v2 schema for investigation, research, novelty and personalization. */
public final class DiscoveryAdvancedSchema {
    public static final String VERSION="discovery_schema_002";
    private DiscoveryAdvancedSchema(){}
    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_hypotheses("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,family TEXT NOT NULL,question TEXT NOT NULL,"+
                "status TEXT NOT NULL DEFAULT 'open',support_count INTEGER NOT NULL DEFAULT 0,counter_count INTEGER NOT NULL DEFAULT 0,"+
                "confidence REAL NOT NULL DEFAULT 0,needs_research INTEGER NOT NULL DEFAULT 0,fingerprint TEXT NOT NULL UNIQUE,"+
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_hypothesis_situation ON discovery_hypotheses(situation_id,status,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_research("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,hypothesis_id INTEGER NOT NULL DEFAULT 0,"+
                "provider TEXT,model TEXT,query_text TEXT NOT NULL,result_text TEXT,citations_json TEXT,state TEXT NOT NULL DEFAULT 'queued',"+
                "error TEXT,latency_ms INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_research_state ON discovery_research(state,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_insight_history("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,candidate_id INTEGER NOT NULL,fingerprint TEXT NOT NULL,event TEXT NOT NULL,"+
                "score REAL NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_insight_history_fp ON discovery_insight_history(fingerprint,created_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_user_signals("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,candidate_id INTEGER NOT NULL DEFAULT 0,situation_id INTEGER NOT NULL DEFAULT 0,"+
                "family TEXT,event TEXT NOT NULL,weight REAL NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_user_signals_family ON discovery_user_signals(family,created_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_domain_state("+
                "situation_id INTEGER PRIMARY KEY,domain TEXT NOT NULL,reasoner_version TEXT NOT NULL,quality REAL NOT NULL DEFAULT 0,"+
                "last_assessed_at INTEGER NOT NULL,assessment_json TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_entity_aliases("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,space TEXT NOT NULL,canonical_key TEXT NOT NULL,alias_norm TEXT NOT NULL,"+
                "entity_kind TEXT,confidence REAL NOT NULL DEFAULT 0,source_item_id INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,"+
                "UNIQUE(space,canonical_key,alias_norm))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_discovery_alias_norm ON discovery_entity_aliases(space,alias_norm)");

        db.execSQL("INSERT OR REPLACE INTO discovery_meta(key,value,updated_at) VALUES('advanced_schema_version',?,?)",
                new Object[]{VERSION,System.currentTimeMillis()});
    }
}
