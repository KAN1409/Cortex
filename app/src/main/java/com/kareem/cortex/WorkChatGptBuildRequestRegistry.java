package com.kareem.cortex;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;

/** Audit trail for explicit user-invoked ChatGPT document build requests. */
public final class WorkChatGptBuildRequestRegistry {
    public static final String VERSION="work_chatgpt_build_request_registry_001";
    private WorkChatGptBuildRequestRegistry(){}

    public static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS work_chatgpt_build_requests("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "document_kind TEXT NOT NULL,"+
                "project_filter TEXT,"+
                "requirements TEXT NOT NULL,"+
                "reference_count INTEGER NOT NULL DEFAULT 0,"+
                "reference_metadata_json TEXT NOT NULL DEFAULT '[]',"+
                "package_path TEXT,"+
                "package_uri TEXT,"+
                "status TEXT NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chatgpt_build_requests_created ON work_chatgpt_build_requests(created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chatgpt_build_requests_status ON work_chatgpt_build_requests(status,updated_at DESC)");
    }

    public static long registerPrepared(SQLiteDatabase db,WorkDocumentRecipe.Kind kind,String project,String requirements,JSONArray refs,String packagePath,String packageUri){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("document_kind",kind==null?"UNKNOWN":kind.name());
        v.put("project_filter",project==null?"":project.trim());
        v.put("requirements",requirements==null?"":requirements.trim());
        v.put("reference_count",refs==null?0:refs.length());
        v.put("reference_metadata_json",refs==null?"[]":refs.toString());
        v.put("package_path",packagePath==null?"":packagePath);
        v.put("package_uri",packageUri==null?"":packageUri);
        v.put("status","PREPARED");v.put("created_at",now);v.put("updated_at",now);
        return db.insert("work_chatgpt_build_requests",null,v);
    }

    public static void markOpened(SQLiteDatabase db,long id,boolean opened){
        if(id<=0)return;ensure(db);ContentValues v=new ContentValues();
        v.put("status",opened?"OPENED_IN_CHATGPT":"OPEN_FAILED");
        v.put("updated_at",System.currentTimeMillis());
        db.update("work_chatgpt_build_requests",v,"id=?",new String[]{String.valueOf(id)});
    }
}
