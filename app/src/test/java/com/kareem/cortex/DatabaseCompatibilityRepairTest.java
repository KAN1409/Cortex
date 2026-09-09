package com.kareem.cortex;

import static org.junit.Assert.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DatabaseCompatibilityRepairTest {
    @Test public void addsMissingSourceKeyAndPreservesStreamData() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE ue_streams(id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,external_key TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'active')");
            db.execSQL("INSERT INTO ue_streams(source_type,external_key,state) VALUES('notification','com.google.android.gms|security','active')");

            assertFalse(DatabaseCompatibilityRepair.columnExists(db,"ue_streams","source_key"));
            DatabaseCompatibilityRepair.repair(db);
            assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_streams","source_key"));

            Cursor c=db.rawQuery("SELECT external_key,source_key,state FROM ue_streams LIMIT 1",null);
            assertTrue(c.moveToFirst());
            assertEquals("com.google.android.gms|security",c.getString(0));
            assertEquals("com.google.android.gms|security",c.getString(1));
            assertEquals("active",c.getString(2));
            c.close();

            DatabaseCompatibilityRepair.repair(db);
            assertEquals(1L,count(db,"SELECT COUNT(*) FROM ue_streams"));
        } finally {
            db.close();
        }
    }

    @Test public void noOpWhenStreamsTableDoesNotExist() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            DatabaseCompatibilityRepair.repair(db);
            assertFalse(DatabaseCompatibilityRepair.tableExists(db,"ue_streams"));
        } finally {
            db.close();
        }
    }

    private static long count(SQLiteDatabase db,String sql){
        Cursor c=db.rawQuery(sql,null);
        try{return c.moveToFirst()?c.getLong(0):0L;}finally{c.close();}
    }
}
