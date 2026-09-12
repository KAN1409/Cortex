package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Tracks generated document requests separately from authoritative archive evidence. */
public final class WorkGeneratedDocumentRegistry {
    public static final String VERSION="work_generated_document_registry_002";
    private WorkGeneratedDocumentRegistry(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS work_generated_documents("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "document_kind TEXT NOT NULL,"+
                "output_format TEXT NOT NULL,"+
                "project_filter TEXT,"+
                "generator TEXT NOT NULL,"+
                "origin TEXT NOT NULL,"+
                "package_path TEXT NOT NULL,"+
                "package_uri TEXT,"+
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_generated_docs_created ON work_generated_documents(created_at DESC)");
    }

    public static long register(SQLiteDatabase db,WorkDocumentRecipe.Kind kind,String outputFormat,String project,String packagePath,String packageUri){
        return register(db,kind,outputFormat,project,"CHATGPT_DOCUMENT_BUILDER_BRIDGE",packagePath,packageUri);
    }

    public static long register(SQLiteDatabase db,WorkDocumentRecipe.Kind kind,String outputFormat,String project,String generator,String path,String uri){
        ensure(db);ContentValues v=new ContentValues();
        v.put("document_kind",kind==null?"UNKNOWN":kind.name());
        v.put("output_format",outputFormat==null?"":outputFormat);
        v.put("project_filter",project==null?"":project);
        v.put("generator",generator==null||generator.trim().isEmpty()?"UNKNOWN_GENERATOR":generator.trim());
        v.put("origin","GENERATED_DOCUMENT");
        v.put("package_path",path==null?"":path);
        v.put("package_uri",uri==null?"":uri);
        v.put("created_at",System.currentTimeMillis());
        return db.insert("work_generated_documents",null,v);
    }

    public static boolean isGeneratedPackagePath(SQLiteDatabase db,String path){
        ensure(db);if(path==null||path.trim().isEmpty())return false;
        Cursor c=db.rawQuery("SELECT 1 FROM work_generated_documents WHERE package_path=? LIMIT 1",new String[]{path});
        boolean found=c.moveToFirst();c.close();return found;
    }
}
