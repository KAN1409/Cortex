package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Versioned parse/chunk storage layered on top of WorkVaultSchema. */
public final class WorkVaultIndexSchema {
    public static final String VERSION="work_vault_index_schema_002";
    private WorkVaultIndexSchema(){}

    public static void ensure(SQLiteDatabase db){
        WorkVaultSchema.ensure(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS work_file_versions("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "fingerprint TEXT NOT NULL,"+
                "parser_version TEXT NOT NULL,"+
                "state TEXT NOT NULL,"+
                "parsed_at INTEGER NOT NULL,"+
                "error TEXT,"+
                "UNIQUE(file_id,fingerprint,parser_version))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_file_versions_file ON work_file_versions(file_id,parsed_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_chunks("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "version_id INTEGER NOT NULL,"+
                "chunk_index INTEGER NOT NULL,"+
                "chunk_kind TEXT NOT NULL,"+
                "chunk_text TEXT NOT NULL,"+
                "sheet_name TEXT,"+
                "page_number INTEGER NOT NULL DEFAULT 0,"+
                "slide_number INTEGER NOT NULL DEFAULT 0,"+
                "row_number INTEGER NOT NULL DEFAULT 0,"+
                "location_json TEXT,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(version_id,chunk_index))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_chunks_file ON work_chunks(file_id,chunk_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_chunks_location ON work_chunks(file_id,sheet_name,page_number,slide_number,row_number)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_followup_records("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "file_id INTEGER NOT NULL,"+
                "project_id INTEGER NOT NULL DEFAULT 0,"+
                "reference_type TEXT,"+
                "reference_value TEXT,"+
                "item_name TEXT,"+
                "status TEXT,"+
                "status_normalized TEXT NOT NULL DEFAULT 'unknown',"+
                "owner_name TEXT,"+
                "due_text TEXT,"+
                "remarks TEXT,"+
                "vendor_name TEXT,"+
                "sheet_name TEXT,"+
                "page_number INTEGER NOT NULL DEFAULT 0,"+
                "row_number INTEGER NOT NULL DEFAULT 0,"+
                "confidence REAL NOT NULL DEFAULT 0,"+
                "extractor_version TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_file ON work_followup_records(file_id,row_number)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_status ON work_followup_records(status_normalized,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_ref ON work_followup_records(reference_type,reference_value)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_project ON work_followup_records(project_id,status_normalized)");
    }
}
