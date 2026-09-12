package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;

/** Audit trail for explicit user-invoked ChatGPT document build requests. */
public final class WorkChatGptBuildRequestRegistry {
    public static final String VERSION="work_chatgpt_build_request_registry_002";
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
                "returned_file_uri TEXT,"+
                "returned_file_name TEXT,"+
                "returned_file_mime TEXT,"+
                "generated_document_id INTEGER NOT NULL DEFAULT 0,"+
                "completed_at INTEGER NOT NULL DEFAULT 0,"+
                "created_at INTEGER NOT NULL,"+
                "updated_at INTEGER NOT NULL)");
        addColumnIfMissing(db,"returned_file_uri","TEXT");
        addColumnIfMissing(db,"returned_file_name","TEXT");
        addColumnIfMissing(db,"returned_file_mime","TEXT");
        addColumnIfMissing(db,"generated_document_id","INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db,"completed_at","INTEGER NOT NULL DEFAULT 0");
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

    public static void markReturned(SQLiteDatabase db,long id,long generatedDocumentId,String uri,String name,String mime){
        if(id<=0)return;ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("status","RETURNED_FILE_ATTACHED");
        v.put("returned_file_uri",uri==null?"":uri);
        v.put("returned_file_name",name==null?"":name);
        v.put("returned_file_mime",mime==null?"":mime);
        v.put("generated_document_id",Math.max(0L,generatedDocumentId));
        v.put("completed_at",now);v.put("updated_at",now);
        db.update("work_chatgpt_build_requests",v,"id=?",new String[]{String.valueOf(id)});
    }

    private static void addColumnIfMissing(SQLiteDatabase db,String name,String declaration){
        if(hasColumn(db,name))return;
        db.execSQL("ALTER TABLE work_chatgpt_build_requests ADD COLUMN "+name+" "+declaration);
    }

    private static boolean hasColumn(SQLiteDatabase db,String name){
        Cursor c=db.rawQuery("PRAGMA table_info(work_chatgpt_build_requests)",null);
        try{int idx=c.getColumnIndex("name");while(c.moveToNext())if(idx>=0&&name.equals(c.getString(idx)))return true;return false;}finally{c.close();}
    }
}
