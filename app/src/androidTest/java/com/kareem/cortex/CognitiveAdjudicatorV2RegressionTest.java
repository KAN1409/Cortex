package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CognitiveAdjudicatorV2RegressionTest {
    private static MasterRelevanceFilter.Signal signal(String source,String title,String body,boolean ongoing){
        return new MasterRelevanceFilter.Signal("notification",source,title,body,"{}",System.currentTimeMillis(),ongoing);
    }

    @Test public void tier0OnlyDropsObviousMachineNoise(){
        assertEquals(MasterRelevanceFilter.Disposition.IGNORE,MasterRelevanceFilter.evaluateTier0(signal("com.android.systemui","Battery","Charging · 76%",true)).disposition);
        assertEquals(MasterRelevanceFilter.Disposition.IGNORE,MasterRelevanceFilter.evaluateTier0(signal("com.spotify.music","Spotify","Now playing · Song",true)).disposition);
        assertEquals(MasterRelevanceFilter.Disposition.IGNORE,MasterRelevanceFilter.evaluateTier0(signal("android.system","Android System","App is running in background",true)).disposition);

        MasterRelevanceFilter.Decision dentist=MasterRelevanceFilter.evaluateTier0(signal("com.calendar","Dentist","Tomorrow 4:00 PM",false));
        assertEquals(MasterRelevanceFilter.Disposition.CONTEXT,dentist.disposition);
        MasterRelevanceFilter.Decision unknown=MasterRelevanceFilter.evaluateTier0(signal("com.example","Project","Need the final drawing",false));
        assertEquals(MasterRelevanceFilter.Disposition.CONTEXT,unknown.disposition);
    }

    @Test public void familyClassificationIsContextBuilderNotFinalDecision(){
        assertEquals(CognitiveSignalV2.SignalFamily.COMMUNICATION,CognitiveSignalV2.classify(signal("com.whatsapp","Ahmed","send me the PDF",false)));
        assertEquals(CognitiveSignalV2.SignalFamily.EVENT,CognitiveSignalV2.classify(signal("com.google.android.calendar","Dentist","Tomorrow 4 PM",false)));
        assertEquals(CognitiveSignalV2.SignalFamily.CONTENT,CognitiveSignalV2.classify(signal("com.instagram.android","Sara","sent you a reel",false)));
        assertEquals(CognitiveSignalV2.SignalFamily.TRANSACTION,CognitiveSignalV2.classify(signal("com.bank.app","Card","Purchase approved",false)));
    }

    @Test public void deterministicPriorityRewardsActionAndUrgency(){
        long now=System.currentTimeMillis();
        int action=CognitiveSignalV2.priorityScore(82,78,CognitiveSignalV2.Kind.ACTION,true,false,false,now+60*60*1000L,now,50,false,now);
        int passive=CognitiveSignalV2.priorityScore(50,20,CognitiveSignalV2.Kind.MESSAGE,false,false,false,0,now,50,false,now);
        assertTrue(action>passive);
        assertTrue(action>=80);
        assertFalse(CognitiveSignalV2.pulseEligible(CognitiveSignalV2.Kind.MESSAGE,40,false,false,false));
        assertTrue(CognitiveSignalV2.pulseEligible(CognitiveSignalV2.Kind.CONTENT,45,false,false,true));
    }

    @Test public void s26LocalBrainProfileIsBoundedAndLocalFirst(){
        assertEquals("Qwen3-1.7B Q4_K_M",LocalModelManager.MODEL_NAME);
        assertEquals(3072,LocalBrainConfig.CONTEXT_SIZE);
        assertEquals(160,LocalBrainConfig.MAX_OUTPUT_TOKENS);
        assertEquals(4,LocalBrainConfig.THREADS);
        assertEquals(6,LocalBrainConfig.MAX_BATCH_SIGNALS);
        assertEquals(0.78,LocalBrainConfig.ACCEPT_LOCAL_CONFIDENCE,0.0001);
        assertEquals(0.55,LocalBrainConfig.TRY_DEEP_CONFIDENCE,0.0001);
    }
}
