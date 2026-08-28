package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TrustedConnectorEnrichmentV1RegressionTest {

    @Test public void richerWhatsAppDeadlineRequestCrossesDurableBoundary(){
        String body="Kareem Abdel Nasser\nCORTEX_E2E_001 — يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده. لو مش هتلحق ابعتلي وقولي عشان أتصرف.";
        MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification","com.whatsapp","Kareem Abdel Nasser",body,"{}",1_800_000_000_000L,false);
        MasterRelevanceFilter.Decision d=RawSignalStore.evaluateTrustedEnrichment(s,body);
        assertEquals(MasterRelevanceFilter.Disposition.ACTION,d.disposition);assertTrue(d.durable());assertTrue(d.importance>=60);
    }

    @Test public void acknowledgementDoesNotRecreateHistoricalRequestAsNewAction(){
        String oldRequest="يا كريم محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده";
        MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification","com.whatsapp","Kareem Abdel Nasser","حاضر","{}",1_800_000_001_000L,false);
        MasterRelevanceFilter.Decision d=RawSignalStore.evaluateTrustedEnrichment(s,oldRequest+"\nحاضر");
        assertFalse(d.durable());assertEquals(MasterRelevanceFilter.Disposition.REVIEW,d.disposition);assertEquals("ACTION",d.candidateKind);
    }

    @Test public void richerSecurityTextCanPromoteEvenWhenPreviewMissedIt(){
        String body="Google\nSecurity alert: a new device logged into your account";
        MasterRelevanceFilter.Signal s=new MasterRelevanceFilter.Signal("notification","com.google.android.gm","Google",body,"{}",1_800_000_002_000L,false);
        MasterRelevanceFilter.Decision d=RawSignalStore.evaluateTrustedEnrichment(s,body);
        assertEquals(MasterRelevanceFilter.Disposition.MEMORY,d.disposition);assertTrue(d.durable());
    }
}
