package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Versioned parse/chunk storage layered on top of WorkVaultSchema. */
public final class WorkVaultIndexSchema {
    public static final String VERSION="work_vault_index_schema_005";
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
                "version_id INTEGER NOT NULL DEFAULT 0,"+
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

        // Additive migrations for databases created before version lineage became first-class.
        addColumnIfMissing(db,"work_facts","version_id","INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db,"work_procurement_refs","version_id","INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db,"work_price_records","version_id","INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db,"work_followup_records","version_id","INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db,"work_files","active_version_id","INTEGER NOT NULL DEFAULT 0");

        // Existing installs adopt their newest successful parse as the initial active version.
        db.execSQL("UPDATE work_files SET active_version_id=("+
                "SELECT v.id FROM work_file_versions v "+
                "WHERE v.file_id=work_files.id AND v.state IN ('complete','partial_needs_ocr') "+
                "ORDER BY v.parsed_at DESC,v.id DESC LIMIT 1"+
                ") WHERE active_version_id=0 AND EXISTS("+
                "SELECT 1 FROM work_file_versions v2 WHERE v2.file_id=work_files.id "+
                "AND v2.state IN ('complete','partial_needs_ocr'))");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_files_active_version ON work_files(active_version_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_facts_version ON work_facts(file_id,version_id,fact_type)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_procurement_ref_version ON work_procurement_refs(file_id,version_id,ref_type,normalized_value)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_prices_version ON work_price_records(file_id,version_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_version ON work_followup_records(file_id,version_id,row_number)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_file ON work_followup_records(file_id,row_number)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_status ON work_followup_records(status_normalized,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_ref ON work_followup_records(reference_type,reference_value)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_followup_project ON work_followup_records(project_id,status_normalized)");

        db.execSQL("CREATE TABLE IF NOT EXISTS work_procurement_links("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "from_kind TEXT NOT NULL,"+
                "from_id INTEGER NOT NULL,"+
                "to_kind TEXT NOT NULL,"+
                "to_id INTEGER NOT NULL,"+
                "relation TEXT NOT NULL,"+
                "confidence REAL NOT NULL,"+
                "evidence_rule TEXT NOT NULL,"+
                "source_file_id INTEGER NOT NULL DEFAULT 0,"+
                "project_id INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "UNIQUE(from_kind,from_id,to_kind,to_id,relation,evidence_rule))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_proc_links_from ON work_procurement_links(from_kind,from_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_proc_links_to ON work_procurement_links(to_kind,to_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_proc_links_file ON work_procurement_links(source_file_id,relation)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_proc_links_project ON work_procurement_links(project_id,relation)");
    }

    private static void addColumnIfMissing(SQLiteDatabase db,String table,String column,String definition){
        Cursor c=null;boolean found=false;
        try{
            c=db.rawQuery("PRAGMA table_info("+table+")",null);
            int name=c.getColumnIndex("name");
            while(c.moveToNext())if(name>=0&&column.equals(c.getString(name))){found=true;break;}
        }finally{if(c!=null)c.close();}
        if(!found)db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }
}
