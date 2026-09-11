package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexPersonalPolicyTest {
    private Context context;
    @Before public void setUp(){context=ApplicationProvider.getApplicationContext();CortexPersonalPolicy.clear(context);}

    @Test public void visualEvidenceNeedsMoreSupportThanDirectAction(){
        PrimeBriefStore.Item visual=new PrimeBriefStore.Item(1,"ACTION","Check important info","Check important info.","PicBrain","open",.80,80,0,0,System.currentTimeMillis());
        PrimeBriefStore.Item direct=new PrimeBriefStore.Item(2,"ACTION","Security alert","Review compromised password","Gmail","open",.93,93,0,0,System.currentTimeMillis());
        assertTrue(CortexPersonalPolicy.belowThreshold(context,visual));
        assertFalse(CortexPersonalPolicy.belowThreshold(context,direct));
    }

    @Test public void chatGptPolicyCanSuppressAndBoostWithoutMutatingEvidence()throws Exception{
        JSONObject policy=new JSONObject();policy.put("version","teacher-test");policy.put("attentionThreshold",.72);policy.put("maxNowItems",5);policy.put("suppressPhrases",new JSONArray().put("weekly promotion"));policy.put("boosts",new JSONArray().put(new JSONObject().put("match","negma").put("weight",.25)));
        CortexPersonalPolicy.save(context,policy);
        PrimeBriefStore.Item promo=new PrimeBriefStore.Item(3,"INSIGHT","Weekly promotion","Sale","mail","open",.9,95,0,0,System.currentTimeMillis());
        PrimeBriefStore.Item project=new PrimeBriefStore.Item(4,"ACTION","Negma approval","Owner reply arrived","whatsapp","open",.7,60,0,0,System.currentTimeMillis());
        assertTrue(CortexPersonalPolicy.suppress(context,promo));
        assertTrue(CortexPersonalPolicy.score(context,project)>=.85);
        assertEquals(5,CortexPersonalPolicy.maxNowItems(context));
        assertEquals("teacher-test",CortexPersonalPolicy.version(context));
    }
}
