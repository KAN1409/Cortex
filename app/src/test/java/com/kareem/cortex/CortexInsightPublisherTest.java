package com.kareem.cortex;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CortexInsightPublisherTest {
    SQLiteDatabase db;
    @Before public void setup()throws Exception{
        resetSchemaReady();
        db=SQLiteDatabase.create(null);DiscoveryV3Schema.ensure(db);
        db.execSQL("CREATE TABLE knowledge_items(id INTEGER PRIMARY KEY,created_at INTEGER)");
        db.execSQL("INSERT INTO knowledge_items VALUES(1,1000),(2,2000),(3,3000)");
    }
    @After public void close(){db.close();}
    void resetSchemaReady()throws Exception{java.lang.reflect.Field f=DiscoveryV3Schema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);}
    long submit(List<Long> ids){return CortexInsightPublisher.submit(db,1,"price|marble","PRICE_CHANGE","WORK","Marble quotation increased by 18%","The comparable marble quotation changed from 1000 to 1180 EGP/m2.","Higher unit cost requires review.","Vendor confirmation is pending.","Compare the quotation and confirm the revised unit price.",.95,.92,ids,4000);}
    String state(long id){try(Cursor c=db.rawQuery("SELECT state FROM discovery_v3_insights WHERE id=?",new String[]{""+id})){assertTrue(c.moveToFirst());return c.getString(0);}}
    @Test public void repeatedSubmissionMergesAndPreservesDismissal(){
        long id=submit(Arrays.asList(1L,2L));assertEquals("published",state(id));
        assertEquals(id,submit(Arrays.asList(1L,2L,3L)));
        try(Cursor c=db.rawQuery("SELECT COUNT(*),MAX(evidence_count) FROM discovery_v3_insights",null)){c.moveToFirst();assertEquals(1,c.getInt(0));assertEquals(3,c.getInt(1));}
        db.execSQL("UPDATE discovery_v3_insights SET state='dismissed'");
        submit(Arrays.asList(1L,2L));assertEquals("dismissed",state(id));
    }
    @Test public void missingSourceCannotPublish(){assertEquals("quarantined",state(submit(Arrays.asList(1L,99L))));}
    @Test public void noSourceCannotPublish(){assertEquals("quarantined",state(submit(Collections.emptyList())));}
    @Test public void checkpointedRunRemainsResumableWhenOwnerIsGone()throws Exception{
        android.content.Context context=androidx.test.core.app.ApplicationProvider.getApplicationContext();
        db.execSQL("INSERT INTO discovery_v3_council_runs(situation_id,state,started_at,updated_at) VALUES(1,'running',1000,1000)");
        long runId;String owner;
        try(Cursor c=db.rawQuery("SELECT id,owner_session,heartbeat_at FROM discovery_v3_council_runs LIMIT 1",null)){
            assertTrue(c.moveToFirst());runId=c.getLong(0);owner=c.getString(1);assertFalse(owner.isEmpty());assertEquals(1000,c.getLong(2));
        }
        db.execSQL("INSERT INTO discovery_v3_council_passes(run_id,role,model_id,model_name,output_text,created_at) VALUES(?,?,?,?,?,?)",
                new Object[]{runId,"investigator","m1","Model 1","grounded pass",2000});
        try(CouncilExecutionLease lease=CouncilExecutionLease.acquire(context)){
            assertNotNull(lease);
            assertEquals(0,CognitiveCouncilRunRecovery.recoverStale(context,db,999999999));
        }
        assertEquals(0,CognitiveCouncilRunRecovery.recoverStale(context,db,999999999));
        try(Cursor c=db.rawQuery("SELECT state,owner_session,heartbeat_at FROM discovery_v3_council_runs WHERE id=?",new String[]{String.valueOf(runId)})){
            assertTrue(c.moveToFirst());assertEquals("running",c.getString(0));assertEquals(owner,c.getString(1));assertEquals(2000,c.getLong(2));
        }
        try(Cursor c=db.rawQuery("SELECT role,output_text FROM discovery_v3_council_passes WHERE run_id=?",new String[]{String.valueOf(runId)})){
            assertTrue(c.moveToFirst());assertEquals("investigator",c.getString(0));assertEquals("grounded pass",c.getString(1));
        }
    }
    @Test public void legacyCouncilRunTableUpgradesWithoutDataLoss()throws Exception{
        SQLiteDatabase legacy=SQLiteDatabase.create(null);
        try{
            legacy.execSQL("CREATE TABLE discovery_v3_council_runs(id INTEGER PRIMARY KEY AUTOINCREMENT,situation_id INTEGER NOT NULL,state TEXT NOT NULL,models_used TEXT,evidence_count INTEGER NOT NULL DEFAULT 0,final_output TEXT,error TEXT,started_at INTEGER NOT NULL,completed_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL)");
            legacy.execSQL("INSERT INTO discovery_v3_council_runs(situation_id,state,started_at,updated_at) VALUES(7,'running',1234,1234)");
            resetSchemaReady();DiscoveryV3Schema.ensure(legacy);
            boolean owner=false,heartbeat=false;
            try(Cursor c=legacy.rawQuery("PRAGMA table_info(discovery_v3_council_runs)",null)){
                int name=c.getColumnIndex("name");while(c.moveToNext()){String n=c.getString(name);if("owner_session".equals(n))owner=true;if("heartbeat_at".equals(n))heartbeat=true;}
            }
            assertTrue(owner);assertTrue(heartbeat);
            try(Cursor c=legacy.rawQuery("SELECT situation_id,state FROM discovery_v3_council_runs WHERE id=1",null)){
                assertTrue(c.moveToFirst());assertEquals(7,c.getInt(0));assertEquals("running",c.getString(1));
            }
        }finally{legacy.close();}
    }
}
