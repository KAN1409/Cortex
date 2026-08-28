package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveAutoReasoningV4RegressionTest {
    private static CognitivePulseProjectionV4.Item item(String kind,double nowScore,long until,boolean fresh,long changedAt){
        return new CognitivePulseProjectionV4.Item(
                "si_test",kind,"DETECTED","Test situation","",
                .55,nowScore,.20,.85,changedAt,until,0,"","",false,0,
                changedAt,fresh,true);
    }

    @Test public void freshDeadlineWithinTwoHoursTriggersUrgentAutonomousPass(){
        long now=System.currentTimeMillis();
        CognitivePulseProjectionV4.Item deadline=item("DEADLINE",.74,now+90L*60L*1000L,true,now);
        CognitivePulseProjectionV4.Snapshot pulse=new CognitivePulseProjectionV4.Snapshot(Collections.singletonList(deadline),0,1,0,1,0);
        CognitiveAutoReasoningPolicyV4.Decision d=CognitiveAutoReasoningPolicyV4.evaluate(pulse,now);
        assertTrue(d.shouldRun);assertTrue(d.urgent);assertEquals(1,d.freshCount);assertFalse(d.fingerprint.isEmpty());
    }

    @Test public void unchangedSituationDoesNotSpendCloudReasoning(){
        long now=System.currentTimeMillis();
        CognitivePulseProjectionV4.Item deadline=item("DEADLINE",.90,now+30L*60L*1000L,false,now-1000);
        CognitivePulseProjectionV4.Snapshot pulse=new CognitivePulseProjectionV4.Snapshot(Collections.singletonList(deadline),1,0,0,0,now-500);
        CognitiveAutoReasoningPolicyV4.Decision d=CognitiveAutoReasoningPolicyV4.evaluate(pulse,now);
        assertFalse(d.shouldRun);assertEquals("no_meaningful_change",d.reason);
    }

    @Test public void lowAttentionFollowUpDoesNotTriggerAutonomousCloudCall(){
        long now=System.currentTimeMillis();
        CognitivePulseProjectionV4.Item follow=item("FOLLOW_UP",.41,0,true,now);
        CognitivePulseProjectionV4.Snapshot pulse=new CognitivePulseProjectionV4.Snapshot(Collections.singletonList(follow),0,1,0,1,0);
        assertFalse(CognitiveAutoReasoningPolicyV4.evaluate(pulse,now).shouldRun);
    }

    @Test public void realtimeProjectionWakesBrainOnlyForMaterialSituationChange(){
        CognitiveSituationEngineV4.Result oldCandidateOnly=new CognitiveSituationEngineV4.Result(25,0,0,Collections.singletonList("si_old"));
        CognitiveSituationEngineV4.Result newlyDetected=new CognitiveSituationEngineV4.Result(25,1,0,Collections.singletonList("si_new"));
        CognitiveSituationEngineV4.Result enrichedTiming=new CognitiveSituationEngineV4.Result(25,0,1,Collections.singletonList("si_existing"));
        assertFalse(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(oldCandidateOnly));
        assertTrue(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(newlyDetected));
        assertTrue(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(enrichedTiming));
        assertFalse(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(null));
    }

    @Test public void autonomousApplyDoesNotAcceptHallucinatedNonEmptyRankingAsCoverage(){
        assertTrue(CognitiveDeepBrainApplyV4.rankingGrounded(true,0,0));
        assertTrue(CognitiveDeepBrainApplyV4.rankingGrounded(true,2,1));
        assertTrue(CognitiveDeepBrainApplyV4.rankingGrounded(false,0,0));
        assertFalse(CognitiveDeepBrainApplyV4.rankingGrounded(true,2,0));
    }

    @Test public void geminiAutonomousResponseRequiresCompleteContract()throws Exception{
        JSONObject ok=new JSONObject();ok.put("request_id","brq_test");ok.put("answer","ok");ok.put("priority_items",new JSONArray());ok.put("priority_updates",new JSONArray());ok.put("suggested_actions",new JSONArray());ok.put("reasoning_blocks",new JSONArray());
        GeminiCognitiveReasoningProviderV4.validateShape(ok);

        JSONObject bad=new JSONObject(ok.toString());bad.remove("priority_items");
        try{GeminiCognitiveReasoningProviderV4.validateShape(bad);fail("missing priority_items must fail closed");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("priority_items"));}
    }

    @Test public void staleApplyRejectionIsNotClassifiedAsProviderFailure(){
        assertTrue(CognitiveReasoningOrchestratorV4.isStaleContext(new IllegalArgumentException("Cortex context changed after this Deep Brain request was built; refresh reasoning")));
        assertFalse(CognitiveReasoningOrchestratorV4.isStaleContext(new IllegalArgumentException("Deep Brain priority_items contained no grounded Cortex IDs")));
        assertFalse(CognitiveReasoningOrchestratorV4.isStaleContext(new IllegalStateException("Gemini HTTP failure")));
    }
}
