package com.kareem.cortex;

import static org.junit.Assert.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DatabaseCompatibilityRepairTest {
    @Test public void addsMissingCaptureColumnsAndPreservesData() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE ue_streams(id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,external_key TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'active')");
            db.execSQL("INSERT INTO ue_streams(source_type,external_key,state) VALUES('notification','com.google.android.gms|security','active')");
            db.execSQL("CREATE TABLE ue_raw_observations(id INTEGER PRIMARY KEY,source_type TEXT,source_key TEXT,event_type TEXT,occurred_at INTEGER)");
            db.execSQL("INSERT INTO ue_raw_observations(id,source_type,source_key,event_type,occurred_at) VALUES(10,'notification','gmail','posted',123)");
            db.execSQL("CREATE TABLE ue_semantic_events(id INTEGER PRIMARY KEY,raw_observation_id INTEGER,stream_id INTEGER,semantic_type TEXT,semantic_state TEXT,occurred_at INTEGER)");
            db.execSQL("INSERT INTO ue_semantic_events(id,raw_observation_id,stream_id,semantic_type,semantic_state,occurred_at) VALUES(20,10,1,'security_alert','complete',123)");

            DatabaseCompatibilityRepair.repair(db);
            DatabaseCompatibilityRepair.repair(db);

            assertEquals("OK",DatabaseCompatibilityRepair.captureSchemaReport(db));
            assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_streams","source_key"));
            assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_semantic_events","confidence"));
            assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_semantic_events","superseded_by"));

            Cursor c=db.rawQuery("SELECT external_key,source_key,state FROM ue_streams LIMIT 1",null);
            assertTrue(c.moveToFirst());
            assertEquals("com.google.android.gms|security",c.getString(0));
            assertEquals("com.google.android.gms|security",c.getString(1));
            assertEquals("active",c.getString(2));
            c.close();

            assertEquals(1L,count(db,"SELECT COUNT(*) FROM ue_streams"));
            assertEquals(1L,count(db,"SELECT COUNT(*) FROM ue_raw_observations"));
            assertEquals(1L,count(db,"SELECT COUNT(*) FROM ue_semantic_events"));
        } finally {
            db.close();
        }
    }

    @Test public void noOpWhenCaptureTablesDoNotExist() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            DatabaseCompatibilityRepair.repair(db);
            assertFalse(DatabaseCompatibilityRepair.tableExists(db,"ue_streams"));
            assertTrue(DatabaseCompatibilityRepair.captureSchemaReport(db).startsWith("MISSING:"));
        } finally {
            db.close();
        }
    }

    private static long count(SQLiteDatabase db,String sql){
        Cursor c=db.rawQuery(sql,null);
        try{return c.moveToFirst()?c.getLong(0):0L;}finally{c.close();}
    }
}
