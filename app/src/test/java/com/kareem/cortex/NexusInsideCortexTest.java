package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NexusInsideCortexTest {
    private Context context;
    private VaultDb db;

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase("cortex.db");
        db=new VaultDb(context);
        CognitiveStore.ensure(db);
        NexusEngine.ensure(db.getWritableDatabase());
    }

    @After public void tearDown(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        context.deleteDatabase("cortex.db");
    }

    @Test public void repeatedGroundedSignalsCreateInterestButDoNotFabricateAction(){
        addSignal("github","Android Kotlin Compose build",80,1);
        addSignal("github","Kotlin Android APK CI",75,2);
        addSignal("termux","ADB Android developer workflow",70,3);

        NexusEngine.refresh(db);

        assertFalse(NexusEngine.interests(db,10).isEmpty());
        assertEquals(0,NexusEngine.readyActionCount(db));
    }

    @Test public void existingCortexActionEntersApprovalLifecycleAndFeedback(){
        addSignal("calendar","Appointment tomorrow reminder",85,10);
        long id=CognitiveStore.addDerived(db,"ACTION","Confirm appointment","Confirm tomorrow appointment","open",.91,90,"test-nexus-action","{}");
        assertTrue(id>0);
        CognitiveStore.setDerivedRoutingChecked(db,id,"calendar",0,0,"ACTION","test:nexus");

        NexusEngine.refresh(db);
        assertEquals(1,NexusEngine.readyActionCount(db));
        assertFalse(NexusEngine.actions(db,10).isEmpty());

        NexusEngine.defer(db,id);
        assertEquals("DRAFT",NexusEngine.actions(db,10).get(0).state);
        NexusEngine.approve(db,id);
        assertEquals("APPROVED",NexusEngine.actions(db,10).get(0).state);
        assertTrue(count("SELECT COUNT(*) FROM feedback_events WHERE target_type='derived' AND target_id="+id)>=2);
    }

    private void addSignal(String source,String body,int importance,int suffix){
        long now=System.currentTimeMillis()-suffix*1000L;
        ContentValues v=new ContentValues();
        v.put("kind","notification");v.put("source",source);v.put("title",body);v.put("body",body);
        v.put("metadata_json","{}");v.put("fingerprint","nx-test-"+suffix);v.put("state","filtered");v.put("disposition","keep");v.put("importance",importance);v.put("reason","test");v.put("promoted_item_id",0);v.put("occurred_at",now);v.put("retention_until",0);v.put("created_at",now);v.put("updated_at",now);
        assertTrue(db.getWritableDatabase().insert("raw_signals",null,v)>0);
    }

    private int count(String sql){android.database.Cursor c=db.getReadableDatabase().rawQuery(sql,null);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
}
