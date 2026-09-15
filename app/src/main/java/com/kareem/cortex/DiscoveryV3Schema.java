package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Additive storage for Discovery Engine v3. Raw evidence remains authoritative. */
public final class DiscoveryV3Schema {
    public static final String VERSION="discovery_v3_004_council_ownership";
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

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_reflections("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "scope TEXT NOT NULL,"+
                "scope_id INTEGER NOT NULL DEFAULT 0,"+
                "model TEXT NOT NULL,"+
                "analyst_output TEXT,"+
                "critic_output TEXT,"+
                "state TEXT NOT NULL,"+
                "accepted_count INTEGER NOT NULL DEFAULT 0,"+
                "duration_ms INTEGER NOT NULL DEFAULT 0,"+
                "error TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_reflection_scope ON discovery_v3_reflections(scope,scope_id,created_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_deep_state("+
                "scope_key TEXT PRIMARY KEY,"+
                "last_run_at INTEGER NOT NULL DEFAULT 0,"+
                "last_evidence_at INTEGER NOT NULL DEFAULT 0,"+
                "last_model TEXT,"+
                "last_error TEXT,"+
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_council_runs("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "situation_id INTEGER NOT NULL,"+
                "state TEXT NOT NULL,"+
                "models_used TEXT,"+
                "evidence_count INTEGER NOT NULL DEFAULT 0,"+
                "final_output TEXT,"+
                "error TEXT,"+
                "owner_session TEXT NOT NULL DEFAULT '',"+
                "heartbeat_at INTEGER NOT NULL DEFAULT 0,"+
                "started_at INTEGER NOT NULL,"+
                "completed_at INTEGER NOT NULL DEFAULT 0,"+
                "updated_at INTEGER NOT NULL)");
        ensureColumn(db,"discovery_v3_council_runs","owner_session","TEXT NOT NULL DEFAULT ''");
        ensureColumn(db,"discovery_v3_council_runs","heartbeat_at","INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_council_run ON discovery_v3_council_runs(situation_id,started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_council_owner ON discovery_v3_council_runs(state,heartbeat_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_council_passes("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "run_id INTEGER NOT NULL,"+
                "role TEXT NOT NULL,"+
                "model_id TEXT NOT NULL,"+
                "model_name TEXT NOT NULL,"+
                "output_text TEXT NOT NULL,"+
                "duration_ms INTEGER NOT NULL DEFAULT 0,"+
                "tokens INTEGER NOT NULL DEFAULT 0,"+
                "tokens_per_second REAL NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dv3_council_pass ON discovery_v3_council_passes(run_id,id)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_dv3_council_run_owner AFTER INSERT ON discovery_v3_council_runs "+
                "WHEN NEW.state='running' BEGIN UPDATE discovery_v3_council_runs SET "+
                "owner_session=CASE WHEN NEW.owner_session='' THEN printf('%d:%d',NEW.started_at,NEW.id) ELSE NEW.owner_session END,"+
                "heartbeat_at=CASE WHEN NEW.heartbeat_at<=0 THEN NEW.started_at ELSE NEW.heartbeat_at END "+
                "WHERE id=NEW.id; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_dv3_council_pass_heartbeat AFTER INSERT ON discovery_v3_council_passes BEGIN "+
                "UPDATE discovery_v3_council_runs SET heartbeat_at=CASE WHEN NEW.created_at>heartbeat_at THEN NEW.created_at ELSE heartbeat_at END,"+
                "updated_at=CASE WHEN NEW.created_at>updated_at THEN NEW.created_at ELSE updated_at END "+
                "WHERE id=NEW.run_id AND state='running'; END");

        db.execSQL("CREATE TABLE IF NOT EXISTS discovery_v3_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("INSERT OR REPLACE INTO discovery_v3_meta(key,value,updated_at) VALUES('schema_version',?,?)",
                new Object[]{VERSION,System.currentTimeMillis()});
        ready=true;
        }
    }

    private static void ensureColumn(SQLiteDatabase db,String table,String column,String definition){
        if(hasColumn(db,table,column))return;
        db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }

    private static boolean hasColumn(SQLiteDatabase db,String table,String column){
        Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);
        try{
            int name=c.getColumnIndex("name");
            while(c.moveToNext())if(name>=0&&column.equals(c.getString(name)))return true;
            return false;
        }finally{c.close();}
    }
}
