package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexChatGptBridgeTest {
    private Context context;
    private VaultDb db;

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();
        context.deleteDatabase("cortex.db");
        db=new VaultDb(context);
        CognitiveStore.ensure(db);
        NexusEngine.ensure(db.getWritableDatabase());
        CortexPersonalPolicy.clear(context);
    }

    @After public void tearDown(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        context.deleteDatabase("cortex.db");
    }

    @Test public void contextPackIncludesLayerContractAndExplicitLegacyFallback()throws Exception{
        long id=CognitiveStore.addDerived(db,"ACTION","Negma approval","Owner reply needs review","open",.93,92,"bridge-test-action","{}");
        assertTrue(id>0);
        assertTrue(CognitiveStore.setDerivedRoutingChecked(db,id,"whatsapp:negma",0,0,"ACTION","test:negma"));

        JSONObject pack=CortexChatGptBridge.buildContextPack(context,db);
        assertEquals(2,pack.getInt("schemaVersion"));
        assertEquals(CortexIntelligenceArchitecture.VERSION,pack.getJSONObject("architecture").getString("version"));
        JSONArray candidates=pack.getJSONArray("priorityCandidates");
        assertTrue(candidates.length()>0);
        JSONObject candidate=candidates.getJSONObject(0);
        assertEquals("whatsapp:negma",candidate.getString("source"));
        assertEquals("Negma approval",candidate.getString("title"));
        assertFalse(candidate.getBoolean("canonical"));
        assertEquals("LEGACY_COMPATIBILITY",candidate.getString("layer"));
        JSONObject system=pack.getJSONObject("system");
        assertTrue(system.getBoolean("compatibilityFallback"));
        assertEquals(CortexPersonalPolicy.version(context),system.getString("policyVersion"));
    }
}
