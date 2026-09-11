package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexChatGptAppTeacherTest {

    @Test public void acceptsValidChatGptPolicy()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","chatgpt-teacher-test")
                .put("ttlMs",86400000)
                .put("attentionThreshold",.73)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject().put("urgency",.2))
                .put("boosts",new JSONArray().put(new JSONObject().put("match","negma").put("weight",.2)))
                .put("teacherNotes","test");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertTrue(r.error,r.ok);
        assertEquals("chatgpt-teacher-test",CortexPersonalPolicy.version(context));
    }


    @Test public void acceptsChatGptFeatureBoostShape()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","chatgpt-teacher-conservative-v1")
                .put("ttlMs",604800000)
                .put("attentionThreshold",.76)
                .put("maxNowItems",5)
                .put("interruptionPenaltyScale",.30)
                .put("featureWeights",new JSONObject().put("priority",.32))
                .put("boosts",new JSONArray().put(
                        new JSONObject().put("feature","securityRisk").put("weight",.22)))
                .put("teacherNotes","test");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertTrue(r.error,r.ok);
        assertEquals("chatgpt-teacher-conservative-v1",CortexPersonalPolicy.version(context));
    }

    @Test public void rejectsOutOfBoundsPolicy()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject policy=new JSONObject()
                .put("version","bad")
                .put("ttlMs",86400000)
                .put("attentionThreshold",1.3)
                .put("maxNowItems",6)
                .put("interruptionPenaltyScale",.25)
                .put("featureWeights",new JSONObject())
                .put("boosts",new JSONArray())
                .put("teacherNotes","bad");

        CortexChatGptAppTeacher.ImportResult r=
                CortexChatGptAppTeacher.importText(context,policy.toString());

        assertFalse(r.ok);
        assertTrue(r.error.contains("attentionThreshold"));
    }

    @Test public void teacherImpactReportPersistsAndSummarizes()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        JSONObject report=new JSONObject()
                .put("version","cortex_teacher_impact_001")
                .put("createdAt",System.currentTimeMillis())
                .put("currentPolicy","before")
                .put("proposedPolicy","after")
                .put("candidateCount",8)
                .put("beforeNow",5)
                .put("afterNow",3)
                .put("promoted",1)
                .put("deferred",3)
                .put("unchanged",4)
                .put("averageScoreDelta",-0.12)
                .put("examples",new JSONArray());

        CortexTeacherImpact.save(context,report);
        JSONObject loaded=CortexTeacherImpact.latest(context);

        assertEquals("before",loaded.getString("currentPolicy"));
        assertEquals("after",loaded.getString("proposedPolicy"));
        assertTrue(CortexTeacherImpact.summary(context).contains("5 → 3"));
    }

    @Test public void policyLifecycleHoldsExplosiveProposalAndRollsBack()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();
        CortexPolicyLifecycle.clearForTests(context);
        CortexPersonalPolicy.clear(context);

        JSONObject current=new JSONObject()
                .put("version","stable-before")
                .put("generatedAt",System.currentTimeMillis())
                .put("ttlMs",604800000L)
                .put("attentionThreshold",.72)
                .put("maxNowItems",5)
                .put("featureWeights",new JSONObject())
                .put("boosts",new JSONArray());
        CortexPersonalPolicy.save(context,current);

        JSONObject proposed=new JSONObject(current.toString())
                .put("version","teacher-explosive")
                .put("attentionThreshold",.20);

        JSONObject impact=new JSONObject()
                .put("candidateCount",10)
                .put("beforeNow",3)
                .put("afterNow",9)
                .put("promoted",6)
                .put("deferred",0)
                .put("averageScoreDelta",.31);

        CortexPolicyLifecycle.Result held=
                CortexPolicyLifecycle.stage(context,current,proposed,impact);
        assertEquals(CortexPolicyLifecycle.Activation.HELD,held.activation);
        assertEquals("stable-before",CortexPersonalPolicy.version(context));

        JSONObject safeImpact=new JSONObject()
                .put("candidateCount",10)
                .put("beforeNow",3)
                .put("afterNow",4)
                .put("promoted",1)
                .put("deferred",0)
                .put("averageScoreDelta",.04);

        CortexPolicyLifecycle.Result canary=
                CortexPolicyLifecycle.stage(context,current,proposed,safeImpact);
        assertEquals(CortexPolicyLifecycle.Activation.CANARY,canary.activation);
        CortexPersonalPolicy.save(context,proposed);
        assertEquals("teacher-explosive",CortexPersonalPolicy.version(context));

        assertTrue(CortexPolicyLifecycle.rollback(context,"test rollback"));
        assertEquals("stable-before",CortexPersonalPolicy.version(context));
    }

    @Test public void extractsJsonFromSharedText()throws Exception{
        String shared="ChatGPT result:\n{\"version\":\"chatgpt-teacher-share\",\"ttlMs\":86400000,\"attentionThreshold\":0.72,\"maxNowItems\":7,\"interruptionPenaltyScale\":0.24,\"featureWeights\":{},\"boosts\":[],\"teacherNotes\":\"ok\"}\n";
        JSONObject p=CortexChatGptAppTeacher.extractPolicy(shared);
        assertEquals("chatgpt-teacher-share",p.getString("version"));
    }
}
