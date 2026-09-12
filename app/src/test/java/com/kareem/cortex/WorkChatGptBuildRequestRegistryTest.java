package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorkChatGptBuildRequestRegistryTest {
    @Test public void returnedFileCompletesRequestAndStoresDerivedLinkage(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            WorkChatGptBuildRequestRegistry.ensure(db);
            long id=WorkChatGptBuildRequestRegistry.registerPrepared(db,WorkDocumentRecipe.Kind.PROJECT_STATUS_REPORT,"Negma","Build report",new JSONArray(),"/pkg.json","content://pkg");
            assertTrue(id>0);
            WorkChatGptBuildRequestRegistry.markOpened(db,id,true);
            WorkChatGptBuildRequestRegistry.markReturned(db,id,77L,"content://finished","status.pdf","application/pdf");
            Cursor c=db.rawQuery("SELECT status,returned_file_uri,returned_file_name,returned_file_mime,generated_document_id,completed_at FROM work_chatgpt_build_requests WHERE id=?",new String[]{String.valueOf(id)});
            assertTrue(c.moveToFirst());
            assertEquals("RETURNED_FILE_ATTACHED",c.getString(0));
            assertEquals("content://finished",c.getString(1));
            assertEquals("status.pdf",c.getString(2));
            assertEquals("application/pdf",c.getString(3));
            assertEquals(77L,c.getLong(4));
            assertTrue(c.getLong(5)>0);
            c.close();
        }finally{db.close();}
    }

    @Test public void ensureMigratesLegacyRequestTableInPlace(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            db.execSQL("CREATE TABLE work_chatgpt_build_requests(id INTEGER PRIMARY KEY AUTOINCREMENT,document_kind TEXT NOT NULL,project_filter TEXT,requirements TEXT NOT NULL,reference_count INTEGER NOT NULL DEFAULT 0,reference_metadata_json TEXT NOT NULL DEFAULT '[]',package_path TEXT,package_uri TEXT,status TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            WorkChatGptBuildRequestRegistry.ensure(db);
            assertTrue(hasColumn(db,"returned_file_uri"));
            assertTrue(hasColumn(db,"returned_file_name"));
            assertTrue(hasColumn(db,"returned_file_mime"));
            assertTrue(hasColumn(db,"generated_document_id"));
            assertTrue(hasColumn(db,"completed_at"));
        }finally{db.close();}
    }

    private static boolean hasColumn(SQLiteDatabase db,String name){
        Cursor c=db.rawQuery("PRAGMA table_info(work_chatgpt_build_requests)",null);
        try{while(c.moveToNext())if(name.equals(c.getString(c.getColumnIndexOrThrow("name"))))return true;return false;}finally{c.close();}
    }
}
