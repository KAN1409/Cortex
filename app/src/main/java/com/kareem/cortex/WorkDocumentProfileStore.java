package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/** Stores descriptive document-type metadata derived from latest grounded chunks. */
public final class WorkDocumentProfileStore {
    public static final String VERSION="work_document_profile_store_001";
    private WorkDocumentProfileStore(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS work_document_profiles("+
                "file_id INTEGER PRIMARY KEY,"+
                "document_type TEXT NOT NULL,"+
                "confidence REAL NOT NULL,"+
                "classifier_version TEXT NOT NULL,"+
                "scores_json TEXT NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_doc_profile_type ON work_document_profiles(document_type,confidence DESC)");
    }

    public static int classifySource(SQLiteDatabase db,long sourceId){
        ensure(db);int count=0;
        Cursor files=db.rawQuery("SELECT id,display_name FROM work_files WHERE source_id=? AND state IN ('indexed','needs_ocr')",new String[]{String.valueOf(sourceId)});
        while(files.moveToNext()){
            long fileId=files.getLong(0);String name=files.isNull(1)?"":files.getString(1);
            StringBuilder body=new StringBuilder();
            Cursor c=db.rawQuery("SELECT chunk_text,sheet_name FROM work_chunks WHERE file_id=? ORDER BY chunk_index ASC LIMIT 120",new String[]{String.valueOf(fileId)});
            while(c.moveToNext()){
                if(!c.isNull(1))body.append(' ').append(c.getString(1));
                if(!c.isNull(0))body.append(' ').append(c.getString(0));
                if(body.length()>40000)break;
            }c.close();
            WorkDocumentClassifier.Result r=WorkDocumentClassifier.classifyText(name,body.toString());
            ContentValues v=new ContentValues();v.put("file_id",fileId);v.put("document_type",r.type);v.put("confidence",r.confidence);v.put("classifier_version",WorkDocumentClassifier.VERSION);v.put("scores_json",new JSONObject(r.scores).toString());v.put("updated_at",System.currentTimeMillis());
            db.insertWithOnConflict("work_document_profiles",null,v,SQLiteDatabase.CONFLICT_REPLACE);count++;
        }files.close();return count;
    }

    public static String typeForFile(SQLiteDatabase db,long fileId){
        ensure(db);Cursor c=db.rawQuery("SELECT document_type FROM work_document_profiles WHERE file_id=? LIMIT 1",new String[]{String.valueOf(fileId)});String out=c.moveToFirst()?c.getString(0):"OTHER";c.close();return out==null?"OTHER":out;
    }
}
