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
        java.lang.reflect.Field f=DiscoveryV3Schema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);
        db=SQLiteDatabase.create(null);DiscoveryV3Schema.ensure(db);
        db.execSQL("CREATE TABLE knowledge_items(id INTEGER PRIMARY KEY,created_at INTEGER)");
        db.execSQL("INSERT INTO knowledge_items VALUES(1,1000),(2,2000),(3,3000)");
    }
    @After public void close(){db.close();}
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
    @Test public void activeOwnerIsNeverRecoveredEvenForOldRun()throws Exception{
        android.content.Context context=androidx.test.core.app.ApplicationProvider.getApplicationContext();
        db.execSQL("INSERT INTO discovery_v3_council_runs(situation_id,state,started_at,updated_at) VALUES(1,'running',1,1)");
        try(CouncilExecutionLease lease=CouncilExecutionLease.acquire(context)){
            assertNotNull(lease);
            assertEquals(0,CognitiveCouncilRunRecovery.recoverStale(context,db,999999999));
        }
        assertEquals(1,CognitiveCouncilRunRecovery.recoverStale(context,db,999999999));
        assertEquals(0,CognitiveCouncilRunRecovery.recoverStale(context,db,999999999));
    }
}
