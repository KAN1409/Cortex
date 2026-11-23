package com.kareem.cortex;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

/** A failed council run must not consume the situation's retry eligibility. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class DiscoveryV3DeepWorkerOutcomeTest {
    static final long NOW=10L*24L*60L*60L*1000L;
    SQLiteDatabase db;
    @Before public void setup()throws Exception{
        java.lang.reflect.Field f=DiscoveryV3Schema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);
        db=SQLiteDatabase.create(null);DiscoveryV3Schema.ensure(db);
        db.execSQL("INSERT INTO discovery_v3_situations(id,situation_key,space,domain,label,state,evidence_count,first_seen,last_seen,created_at,updated_at) VALUES(1,'s1','WORK','WORK','Marble PO','active',2,1000,"+(NOW-1000)+",1000,1000)");
    }
    @After public void close(){db.close();}
    static CognitiveCouncilOrchestrator.Result failure(String err){return new CognitiveCouncilOrchestrator.Result(false,false,"","","","","",0,"","",err,Collections.emptyList());}
    static CognitiveCouncilOrchestrator.Result success(){return new CognitiveCouncilOrchestrator.Result(true,false,"","","","","",0,"","Model A","",Collections.emptyList());}
    long lastRunAt(){try(Cursor c=db.rawQuery("SELECT last_run_at FROM discovery_v3_deep_state WHERE scope_key='situation:1'",null)){assertTrue(c.moveToFirst());return c.getLong(0);}}
    String lastError(){try(Cursor c=db.rawQuery("SELECT last_error FROM discovery_v3_deep_state WHERE scope_key='situation:1'",null)){assertTrue(c.moveToFirst());return c.getString(0);}}

    @Test public void failedRunStaysDueForImmediateRetry(){
        assertEquals(Arrays.asList(1L),DiscoveryV3DeepWorker.dueSituations(db,1,NOW));
        DiscoveryV3DeepWorker.recordOutcome(db,1,failure("Primary 30B local brain is not ready"),NOW);
        assertEquals("Primary 30B local brain is not ready",lastError());
        assertEquals(0,lastRunAt());
        assertEquals(Arrays.asList(1L),DiscoveryV3DeepWorker.dueSituations(db,1,NOW+1));
    }
    @Test public void completedRunConsumesEligibilityUntilNewEvidence(){
        DiscoveryV3DeepWorker.recordOutcome(db,1,success(),NOW);
        assertEquals(NOW,lastRunAt());
        assertTrue(DiscoveryV3DeepWorker.dueSituations(db,1,NOW+1).isEmpty());
        db.execSQL("UPDATE discovery_v3_situations SET last_seen=? WHERE id=1",new Object[]{NOW+500});
        assertEquals(Arrays.asList(1L),DiscoveryV3DeepWorker.dueSituations(db,1,NOW+1000));
    }
    @Test public void failureAfterSuccessKeepsPreviousRunStamp(){
        DiscoveryV3DeepWorker.recordOutcome(db,1,success(),NOW);
        DiscoveryV3DeepWorker.recordOutcome(db,1,failure("Another council run is already active"),NOW+5000);
        assertEquals(NOW,lastRunAt());
        assertEquals("Another council run is already active",lastError());
    }
}
