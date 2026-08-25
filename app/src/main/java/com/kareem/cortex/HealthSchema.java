package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Idempotent health-follow-up schema layered on the Cortex evidence database. */
public final class HealthSchema {
    public static final String REVISION="health_001";
    private HealthSchema(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS health_sources(source_key TEXT PRIMARY KEY,kind TEXT NOT NULL,display_name TEXT NOT NULL,package_name TEXT,status TEXT DEFAULT 'available',last_sync_at INTEGER DEFAULT 0,metadata_json TEXT,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS health_evidence(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,evidence_kind TEXT NOT NULL,knowledge_item_id INTEGER DEFAULT 0,external_id TEXT,title TEXT,body TEXT,occurred_at INTEGER NOT NULL,metadata_json TEXT,fingerprint TEXT UNIQUE,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_evidence_time ON health_evidence(occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_evidence_item ON health_evidence(knowledge_item_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_evidence_source ON health_evidence(source_key,occurred_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS health_metrics(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,metric_type TEXT NOT NULL,value_real REAL NOT NULL,unit TEXT,start_at INTEGER NOT NULL,end_at INTEGER NOT NULL,external_id TEXT,metadata_json TEXT,fingerprint TEXT UNIQUE,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_metric_type_time ON health_metrics(metric_type,end_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_metric_source_time ON health_metrics(source_key,end_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS health_followups(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT,state TEXT DEFAULT 'open',due_at INTEGER DEFAULT 0,source_evidence_id INTEGER DEFAULT 0,source_metric_id INTEGER DEFAULT 0,confidence REAL DEFAULT 0,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,resolved_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_followup_state ON health_followups(state,due_at,updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS health_sync_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,state TEXT NOT NULL,records_seen INTEGER DEFAULT 0,records_added INTEGER DEFAULT 0,error TEXT,started_at INTEGER NOT NULL,finished_at INTEGER DEFAULT 0,metadata_json TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_sync_recent ON health_sync_runs(source_key,started_at DESC)");
        long now=System.currentTimeMillis();
        db.execSQL("INSERT OR IGNORE INTO health_sources(source_key,kind,display_name,package_name,status,last_sync_at,metadata_json,updated_at) VALUES('health_connect','GATEWAY','Health Connect','com.google.android.apps.healthdata','available',0,'{}',"+now+")");
        db.execSQL("INSERT OR IGNORE INTO health_sources(source_key,kind,display_name,package_name,status,last_sync_at,metadata_json,updated_at) VALUES('samsung_health','VENDOR','Samsung Health','com.sec.android.app.shealth','available',0,'{\"route\":\"health_connect\"}',"+now+")");
        db.execSQL("INSERT OR IGNORE INTO health_sources(source_key,kind,display_name,package_name,status,last_sync_at,metadata_json,updated_at) VALUES('huawei_health','VENDOR','Huawei Health / Watch','com.huawei.health','setup_required',0,'{\"route\":\"huawei_health_kit\"}',"+now+")");
        try{db.execSQL("INSERT OR REPLACE INTO schema_meta(key,value,updated_at) VALUES('health_schema','"+REVISION+"',"+now+")");}catch(Throwable ignored){}
    }
}
