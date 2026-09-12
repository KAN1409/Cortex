package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/** Versioned parse/chunk storage layered on top of WorkVaultSchema. */
public final class WorkVaultIndexSchema {
    public static final String VERSION="work_vault_index_schema_001";
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
    }
}
