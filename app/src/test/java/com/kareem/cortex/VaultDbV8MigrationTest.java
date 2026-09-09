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
        seed.execSQL("CREATE TABLE ue_streams(id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,external_key TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'active',current_hash TEXT,current_title TEXT,current_body TEXT,platform_hint TEXT,technical_type TEXT,observation_count INTEGER DEFAULT 0,meaningful_count INTEGER DEFAULT 0,first_seen_at INTEGER NOT NULL,last_seen_at INTEGER NOT NULL,last_meaningful_at INTEGER DEFAULT 0,metadata_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(source_type,external_key))");
        seed.execSQL("INSERT INTO ue_streams(id,source_type,external_key,state,first_seen_at,last_seen_at,created_at,updated_at) VALUES(1,'notification','gmail|thread','active',100,100,100,100)");
        seed.execSQL("CREATE TABLE ue_raw_observations(id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,source_key TEXT NOT NULL,source_observation_key TEXT,event_type TEXT NOT NULL,platform_hint TEXT,technical_type TEXT,title TEXT,body TEXT,payload_json TEXT NOT NULL,immutable_hash TEXT NOT NULL,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        seed.execSQL("INSERT INTO ue_raw_observations(id,source_type,source_key,event_type,payload_json,immutable_hash,occurred_at,created_at) VALUES(10,'notification','gmail','posted','{}','hash10',100,100)");
        seed.execSQL("CREATE TABLE ue_semantic_events(id INTEGER PRIMARY KEY AUTOINCREMENT,raw_observation_id INTEGER NOT NULL,stream_id INTEGER NOT NULL,revision INTEGER NOT NULL DEFAULT 1,semantic_type TEXT NOT NULL,intent TEXT,subject TEXT,summary TEXT,confidence REAL DEFAULT 0,semantic_state TEXT NOT NULL DEFAULT 'complete',meaningful INTEGER NOT NULL DEFAULT 1,processor_version TEXT NOT NULL,model_route TEXT,reason TEXT,occurred_at INTEGER NOT NULL,created_at INTEGER NOT NULL,superseded_by INTEGER DEFAULT 0)");
        seed.execSQL("INSERT INTO ue_semantic_events(id,raw_observation_id,stream_id,semantic_type,semantic_state,processor_version,occurred_at,created_at) VALUES(20,10,1,'message','complete','uee_001',100,100)");
        seed.setVersion(7);
        seed.close();

        VaultDb helper=new VaultDb(context);
        SQLiteDatabase db=helper.getWritableDatabase();
        assertEquals(8,db.getVersion());
        assertTrue(DatabaseCompatibilityRepair.columnExists(db,"ue_streams","source_key"));
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
