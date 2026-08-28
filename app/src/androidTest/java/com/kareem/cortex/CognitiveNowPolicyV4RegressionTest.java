package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveNowPolicyV4RegressionTest {
    private static final long NOW=1_800_000_000_000L;

    @Test public void imminentDeadlineBeatsStaticBaseline(){
        CognitiveNowPolicyV4.Evaluation x=CognitiveNowPolicyV4.evaluate(
                "DEADLINE","DETECTED",.46,.18,.84,NOW-2*CognitiveNowPolicyV4.HOUR_MS,NOW+CognitiveNowPolicyV4.HOUR_MS,0,0,true,NOW);
        assertTrue(x.eligible);assertTrue(x.nowScore>.65);
    }

    @Test public void deferredSituationDoesNotReturnToPulseBecauseOfOldBrainRank(){
        CognitiveNowPolicyV4.Evaluation x=CognitiveNowPolicyV4.evaluate(
                "RISK","DEFERRED",.95,.5,.95,NOW-CognitiveNowPolicyV4.HOUR_MS,0,1,NOW-CognitiveNowPolicyV4.HOUR_MS,true,NOW);
        assertFalse(x.eligible);assertFalse(x.currentDeepBrain);
    }

    @Test public void weekOldDeepBrainRankingStopsDominatingCurrentAttention(){
        CognitiveNowPolicyV4.Evaluation stale=CognitiveNowPolicyV4.evaluate(
                "FOLLOW_UP","RELEVANT",.55,.08,.8,NOW-2*CognitiveNowPolicyV4.DAY_MS,0,1,NOW-8*CognitiveNowPolicyV4.DAY_MS,false,NOW);
        CognitiveNowPolicyV4.Evaluation fresh=CognitiveNowPolicyV4.evaluate(
                "FOLLOW_UP","RELEVANT",.55,.08,.8,NOW-2*CognitiveNowPolicyV4.DAY_MS,0,1,NOW-CognitiveNowPolicyV4.HOUR_MS,false,NOW);
        assertEquals(0,stale.brainFreshness,0.0001);assertFalse(stale.currentDeepBrain);assertTrue(fresh.currentDeepBrain);assertTrue(fresh.nowScore>stale.nowScore);
    }

    @Test public void pastUpcomingEventFallsOutOfNowAfterTwelveHours(){
        CognitiveNowPolicyV4.Evaluation x=CognitiveNowPolicyV4.evaluate(
                "UPCOMING_EVENT","DETECTED",.7,.2,.9,NOW-CognitiveNowPolicyV4.DAY_MS,NOW-13*CognitiveNowPolicyV4.HOUR_MS,0,0,false,NOW);
        assertFalse(x.eligible);
    }

    @Test public void recentSecurityRiskRemainsEligibleWithoutModelHelp(){
        CognitiveNowPolicyV4.Evaluation x=CognitiveNowPolicyV4.evaluate(
                "RISK","DETECTED",.58,.28,.88,NOW-CognitiveNowPolicyV4.HOUR_MS,0,0,0,false,NOW);
        assertTrue(x.eligible);assertTrue(x.nowScore>.55);assertFalse(x.currentDeepBrain);
    }
}
