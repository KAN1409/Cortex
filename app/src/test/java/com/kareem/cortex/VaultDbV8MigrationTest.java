package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class VaultDbV8MigrationTest {
    @Test public void v7DatabaseUpgradesToV8WithoutDroppingCaptureEvidence() {
        Context context = RuntimeEnvironment.getApplication();
        File file = context.getDatabasePath("cortex.db");
        if (file.exists()) assertTrue(file.delete());
        File parent=file.getParentFile();if(parent!=null)parent.mkdirs();

        SQLiteDatabase seed=SQLiteDatabase.openOrCreateDatabase(file,null);
        seed.execSQL("CREATE TABLE ue_streams(id INTEGER PRIMARY KEY,source_type TEXT,external_key TEXT,state TEXT,last_seen_at INTEGER)");
        seed.execSQL("INSERT INTO ue_streams(id,source_type,external_key,state,last_seen_at) VALUES(1,'notification','gmail|thread','active',100)");
        seed.execSQL("CREATE TABLE ue_raw_observations(id INTEGER PRIMARY KEY,source_type TEXT,source_key TEXT,event_type TEXT,occurred_at INTEGER)");
        seed.execSQL("INSERT INTO ue_raw_observations(id,source_type,source_key,event_type,occurred_at) VALUES(10,'notification','gmail','posted',100)");
        seed.execSQL("CREATE TABLE ue_semantic_events(id INTEGER PRIMARY KEY,raw_observation_id INTEGER,stream_id INTEGER,semantic_type TEXT,semantic_state TEXT,occurred_at INTEGER)");
        seed.execSQL("INSERT INTO ue_semantic_events(id,raw_observation_id,stream_id,semantic_type,semantic_state,occurred_at) VALUES(20,10,1,'message','complete',100)");
        seed.setVersion(7);
        seed.close();

        VaultDb helper=new VaultDb(context);
        SQLiteDatabase db=helper.getWritableDatabase();
        assertEquals(8,db.getVersion());
        assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_streams","source_key"));
        assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_semantic_events","confidence"));
        assertEquals("OK",DatabaseCompatibilityRepair.captureSchemaReport(db));

        Cursor c=db.rawQuery("SELECT external_key,source_key FROM ue_streams WHERE id=1",null);
        assertTrue(c.moveToFirst());
        assertEquals("gmail|thread",c.getString(0));
        assertEquals("gmail|thread",c.getString(1));
        c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM ue_raw_observations WHERE id=10",null);
        assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));c.close();
        c=db.rawQuery("SELECT COUNT(*) FROM ue_semantic_events WHERE id=20",null);
        assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));c.close();
        helper.close();
    }
}
