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

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();
        CortexPersonalPolicy.clear(context);
    }

    @Test public void legacyBriefHelpersDoNotMakeFinalAttentionDecisions(){
        PrimeBriefStore.Item visual=new PrimeBriefStore.Item(
                1,"ACTION","Check important info","Check important info.","PicBrain",
                "open",.80,80,0,0,System.currentTimeMillis());
        PrimeBriefStore.Item direct=new PrimeBriefStore.Item(
                2,"ACTION","Security alert","Review compromised password","Gmail",
                "open",.93,93,0,0,System.currentTimeMillis());

        assertFalse(CortexPersonalPolicy.belowThreshold(context,visual));
        assertFalse(CortexPersonalPolicy.belowThreshold(context,direct));
        assertEquals(.80,CortexPersonalPolicy.score(context,visual),.0001);
        assertEquals(.93,CortexPersonalPolicy.score(context,direct),.0001);
    }

    @Test public void chatGptPolicyPersistsBoundedJudgeParametersWithoutDirectUiSuppression()throws Exception{
        JSONObject policy=new JSONObject();
        policy.put("version","teacher-test");
        policy.put("attentionThreshold",.72);
        policy.put("maxNowItems",5);
        policy.put("suppressPhrases",new JSONArray().put("weekly promotion"));
        policy.put("boosts",new JSONArray().put(
                new JSONObject().put("match","negma").put("weight",.25)));

        CortexPersonalPolicy.save(context,policy);

        PrimeBriefStore.Item promo=new PrimeBriefStore.Item(
                3,"INSIGHT","Weekly promotion","Sale","mail",
                "open",.9,95,0,0,System.currentTimeMillis());
        PrimeBriefStore.Item project=new PrimeBriefStore.Item(
                4,"ACTION","Negma approval","Owner reply arrived","whatsapp",
                "open",.7,60,0,0,System.currentTimeMillis());

        assertFalse(CortexPersonalPolicy.suppress(context,promo));
        assertEquals(.60,CortexPersonalPolicy.score(context,project),.0001);
        assertEquals(5,CortexPersonalPolicy.maxNowItems(context));
        assertEquals(.72,CortexPersonalPolicy.attentionThreshold(context),.0001);
        assertEquals("teacher-test",CortexPersonalPolicy.version(context));
    }
}
