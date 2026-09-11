package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Cortex Knowledge V2.
 *
 * Additive, rebuildable knowledge model:
 * raw evidence is preserved; derived facts/events never replace the evidence that produced them.
 */
public final class KnowledgeV2Schema {
    public static final String REVISION="knowledge_v2_002";
    public static final int PIPELINE_VERSION=2;

    private KnowledgeV2Schema(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_evidence("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "source_type TEXT NOT NULL,"+
                "source_key TEXT NOT NULL UNIQUE,"+
                "source_uri TEXT,"+
                "source_media_id INTEGER DEFAULT 0,"+
                "raw_text TEXT,"+
                "content_hash TEXT,"+
                "origin TEXT DEFAULT 'UNKNOWN',"+
                "self_reference_score REAL DEFAULT 0,"+
                "derivation_depth INTEGER DEFAULT 0,"+
                "knowledge_eligible INTEGER DEFAULT 1,"+
                "provenance_reason TEXT,"+
                "captured_at INTEGER DEFAULT 0,"+
                "observed_at INTEGER NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_evidence_time ON kv2_evidence(observed_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_evidence_source ON kv2_evidence(source_type,source_media_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_understanding("+
                "evidence_id INTEGER PRIMARY KEY,"+
                "title TEXT,"+
                "summary TEXT,"+
                "category TEXT,"+
                "tags TEXT,"+
                "engine TEXT,"+
                "extraction_version INTEGER NOT NULL,"+
                "confidence REAL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_facts("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "subject_type TEXT NOT NULL,"+
                "subject_key TEXT NOT NULL,"+
                "predicate TEXT NOT NULL,"+
                "object_type TEXT NOT NULL,"+
                "object_value TEXT NOT NULL,"+
                "confidence REAL DEFAULT 0,"+
                "state TEXT DEFAULT 'active',"+
                "valid_from INTEGER DEFAULT 0,"+
                "valid_to INTEGER DEFAULT 0,"+
                "observed_at INTEGER NOT NULL,"+
                "extraction_version INTEGER NOT NULL,"+
                "fingerprint TEXT NOT NULL UNIQUE,"+
                "metadata_json TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_fact_subject ON kv2_facts(subject_type,subject_key,predicate)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_fact_state ON kv2_facts(state,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_events("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "event_type TEXT NOT NULL,"+
                "title TEXT,"+
                "body TEXT,"+
                "status TEXT DEFAULT 'observed',"+
                "event_time_start INTEGER DEFAULT 0,"+
                "event_time_end INTEGER DEFAULT 0,"+
                "observed_at INTEGER NOT NULL,"+
                "confidence REAL DEFAULT 0,"+
                "fingerprint TEXT NOT NULL UNIQUE,"+
                "metadata_json TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_event_time ON kv2_events(event_time_start,observed_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_fact_evidence("+
                "fact_id INTEGER NOT NULL,"+
                "evidence_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL DEFAULT 'DERIVED_FROM',"+
                "confidence REAL DEFAULT 1,"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(fact_id,evidence_id,relation))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_fact_evidence_ev ON kv2_fact_evidence(evidence_id,fact_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_event_evidence("+
                "event_id INTEGER NOT NULL,"+
                "evidence_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL DEFAULT 'DERIVED_FROM',"+
                "confidence REAL DEFAULT 1,"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(event_id,evidence_id,relation))");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_entity_mentions("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "evidence_id INTEGER NOT NULL,"+
                "mention_kind TEXT NOT NULL,"+
                "mention_text TEXT NOT NULL,"+
                "normalized_text TEXT NOT NULL,"+
                "resolved_entity_id INTEGER DEFAULT 0,"+
                "resolution_confidence REAL DEFAULT 0,"+
                "resolution_method TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_mentions_norm ON kv2_entity_mentions(mention_kind,normalized_text)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_edges("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "from_type TEXT NOT NULL,"+
                "from_id INTEGER NOT NULL,"+
                "to_type TEXT NOT NULL,"+
                "to_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL,"+
                "confidence REAL DEFAULT 0,"+
                "valid_from INTEGER DEFAULT 0,"+
                "valid_to INTEGER DEFAULT 0,"+
                "metadata_json TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(from_type,from_id,to_type,to_id,relation,valid_from))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_edges_from ON kv2_edges(from_type,from_id,relation)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_edges_to ON kv2_edges(to_type,to_id,relation)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_categories("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "canonical_name TEXT NOT NULL UNIQUE,"+
                "parent_id INTEGER DEFAULT 0,"+
                "description TEXT,"+
                "origin TEXT DEFAULT 'emergent',"+
                "confidence REAL DEFAULT 0,"+
                "state TEXT DEFAULT 'active',"+
                "created_at INTEGER NOT NULL,"+
                "last_active_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_category_memberships("+
                "knowledge_type TEXT NOT NULL,"+
                "knowledge_id INTEGER NOT NULL,"+
                "category_id INTEGER NOT NULL,"+
                "score REAL DEFAULT 0,"+
                "reason TEXT,"+
                "model_version TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(knowledge_type,knowledge_id,category_id))");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_processing("+
                "evidence_id INTEGER NOT NULL,"+
                "stage TEXT NOT NULL,"+
                "pipeline_version INTEGER NOT NULL,"+
                "state TEXT NOT NULL DEFAULT 'PENDING',"+
                "attempt_count INTEGER NOT NULL DEFAULT 0,"+
                "last_error TEXT,"+
                "started_at INTEGER DEFAULT 0,"+
                "completed_at INTEGER DEFAULT 0,"+
                "updated_at INTEGER NOT NULL,"+
                "PRIMARY KEY(evidence_id,stage,pipeline_version))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kv2_processing_queue ON kv2_processing(stage,pipeline_version,state,updated_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS kv2_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        long now=System.currentTimeMillis();
        db.execSQL("INSERT OR REPLACE INTO kv2_meta(key,value,updated_at) VALUES('schema_revision','"+REVISION+"',"+now+")");
        db.execSQL("INSERT OR REPLACE INTO kv2_meta(key,value,updated_at) VALUES('pipeline_version','"+PIPELINE_VERSION+"',"+now+")");
    }

    public static boolean ready(SQLiteDatabase db){
        Cursor c=db.rawQuery("SELECT value FROM kv2_meta WHERE key='schema_revision' LIMIT 1",null);
        boolean ok=c.moveToFirst()&&REVISION.equals(c.getString(0));c.close();return ok;
    }
}
