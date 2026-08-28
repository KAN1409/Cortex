package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveRealtimeProjectionV4RegressionTest {
    @Test public void richerConnectorTextWinsMemoryProjection(){
        String base="Kareem Abdel Nasser";
        String connector="Kareem Abdel Nasser CORTEX_E2E_001 يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده";
        assertEquals(connector,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }

    @Test public void explicitConnectorRequestWinsEvenWhenShorter(){
        String base="Conversation update with several ordinary words and no responsibility or request at all";
        String connector="محتاج منك تبعتلي الملف";
        assertEquals(connector,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }

    @Test public void weakerConnectorTextDoesNotReplaceUsefulMemory(){
        String base="محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده";
        String connector="حاضر";
        assertEquals(base,CognitiveRealtimeProjectionV4.preferConnectorText(base,connector));
    }

    @Test public void realE2eConnectorPayloadCanRecoverFromWeakNativePreviewAndBecomeDeadline(){
        Calendar base=Calendar.getInstance();base.set(2026,Calendar.AUGUST,28,14,4,0);base.set(Calendar.MILLISECOND,0);long now=base.getTimeInMillis();
        String nativePreview="Kareem Abdel Nasser";
        String connector="Kareem Abdel Nasser\nCORTEX_E2E_001 — يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده. لو مش هتلحق ابعتلي وقولي عشان أتصرف.";
        MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification","com.whatsapp","Kareem Abdel Nasser",connector,"{}",now,false);
        MasterRelevanceFilter.Decision recovered=RawSignalStore.evaluateTrustedEnrichment(s,connector);
        assertEquals(MasterRelevanceFilter.Disposition.ACTION,recovered.disposition);
        String memoryBody=CognitiveRealtimeProjectionV4.preferConnectorText(nativePreview,connector);assertEquals(connector,memoryBody);
        CognitiveSituationEngineV4.Candidate situation=CognitiveSituationEngineV4.detect("mem_e2e","CONVERSATION","Kareem Abdel Nasser",memoryBody,"com.whatsapp",now,.68,memoryBody,now);
        assertNotNull(situation);assertEquals(CognitiveDomainV4.SituationKind.DEADLINE,situation.kind);assertNotNull(situation.relevantUntil);
        Calendar deadline=Calendar.getInstance();deadline.setTimeInMillis(situation.relevantUntil);assertEquals(17,deadline.get(Calendar.HOUR_OF_DAY));
    }
}
