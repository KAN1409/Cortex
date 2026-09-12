package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Additive schema for the Cortex Work Vault domain. Originals remain outside the app database. */
public final class WorkVaultSchema {
    public static final String VERSION="work_vault_schema_001";
    private WorkVaultSchema(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS work_sources("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "tree_uri TEXT NOT NULL UNIQUE,"+
                "display_name TEXT NOT NULL,"+
                "state TEXT NOT NULL DEFAULT 'active',"+
                "last_scan_at INTEGER NOT NULL DEFAULT 0,"+
                "last_error TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_sources_state ON work_sources(state,updated_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_files("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "source_id INTEGER NOT NULL,"+
                "document_uri TEXT NOT NULL UNIQUE,"+
                "parent_uri TEXT,"+
                "display_name TEXT NOT NULL,"+
                "mime_type TEXT,"+
                "extension TEXT,"+
                "size_bytes INTEGER NOT NULL DEFAULT 0,"+
                "modified_at INTEGER NOT NULL DEFAULT 0,"+
                "fingerprint TEXT,"+
                "state TEXT NOT NULL DEFAULT 'new',"+
                "parser_version TEXT,"+
                "indexed_at INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_files_source ON work_files(source_id,state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_files_name ON work_files(display_name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_files_fingerprint ON work_files(fingerprint)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_projects("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "canonical_name TEXT NOT NULL,"+
                "normalized_key TEXT NOT NULL UNIQUE,"+
                "state TEXT NOT NULL DEFAULT 'active',"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_entities("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "kind TEXT NOT NULL,"+
                "canonical_name TEXT NOT NULL,"+
                "normalized_key TEXT NOT NULL UNIQUE,"+
                "metadata_json TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_facts("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "project_id INTEGER NOT NULL DEFAULT 0,"+
                "fact_type TEXT NOT NULL,"+
                "fact_key TEXT,"+
                "text_value TEXT,"+
                "numeric_value REAL,"+
                "unit TEXT,"+
                "currency TEXT,"+
                "sheet_name TEXT,"+
                "page_number INTEGER NOT NULL DEFAULT 0,"+
                "slide_number INTEGER NOT NULL DEFAULT 0,"+
                "row_number INTEGER NOT NULL DEFAULT 0,"+
                "column_name TEXT,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "extractor_version TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_facts_file ON work_facts(file_id,fact_type)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_facts_project ON work_facts(project_id,fact_type)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_relations("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "from_type TEXT NOT NULL,"+
                "from_id INTEGER NOT NULL,"+
                "to_type TEXT NOT NULL,"+
                "to_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "source_file_id INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_relations_from ON work_relations(from_type,from_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_relations_to ON work_relations(to_type,to_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_procurement_refs("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "project_id INTEGER NOT NULL DEFAULT 0,"+
                "ref_type TEXT NOT NULL,"+
                "ref_value TEXT NOT NULL,"+
                "normalized_value TEXT NOT NULL,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(file_id,ref_type,normalized_value))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_procurement_ref ON work_procurement_refs(ref_type,normalized_value)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_price_records("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "project_id INTEGER NOT NULL DEFAULT 0,"+
                "item_name TEXT NOT NULL,"+
                "vendor_name TEXT,"+
                "quantity REAL,"+
                "unit TEXT,"+
                "unit_price REAL,"+
                "total_price REAL,"+
                "currency TEXT,"+
                "reference_type TEXT,"+
                "reference_value TEXT,"+
                "sheet_name TEXT,"+
                "page_number INTEGER NOT NULL DEFAULT 0,"+
                "row_number INTEGER NOT NULL DEFAULT 0,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_prices_item ON work_price_records(item_name,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_prices_project ON work_price_records(project_id,created_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_index_jobs("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "source_id INTEGER NOT NULL,"+
                "state TEXT NOT NULL,"+
                "total_files INTEGER NOT NULL DEFAULT 0,"+
                "processed_files INTEGER NOT NULL DEFAULT 0,"+
                "failed_files INTEGER NOT NULL DEFAULT 0,"+
                "last_document_uri TEXT,"+
                "started_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL,"+
                "completed_at INTEGER NOT NULL DEFAULT 0,"+
                "error TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_jobs_source ON work_index_jobs(source_id,started_at DESC)");
    }
}
