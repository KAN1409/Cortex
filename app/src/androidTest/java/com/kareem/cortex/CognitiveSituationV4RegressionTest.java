package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveSituationV4RegressionTest {
    @Test public void securityMemoryBecomesRiskNotGenericNotification(){
        long now=System.currentTimeMillis();
        CognitiveSituationEngineV4.Candidate x=CognitiveSituationEngineV4.detect("m1","CONVERSATION","Security Alert","A new device logged into your account","com.google.android.gm",now,.78,"security alert a new device logged into your account",now);
        assertNotNull(x);assertEquals(CognitiveDomainV4.SituationKind.RISK,x.kind);assertTrue(x.confidence>=.8);
    }

    @Test public void explicitSaturdayHospitalTimeBecomesUpcomingEvent(){
        Calendar base=Calendar.getInstance();base.set(2026,Calendar.AUGUST,28,10,0,0);base.set(Calendar.MILLISECOND,0);long now=base.getTimeInMillis();
        String text="عندي اشاعات في مستشفى النسائم يوم السبت الساعة 6 مساءً محتاج أعمل reminder ضروري";
        CognitiveSituationEngineV4.Candidate x=CognitiveSituationEngineV4.detect("m2","VOICE","أشعة مستشفى النسائم",text,"manual_recording",now,.5,text,now);
        assertNotNull(x);assertEquals(CognitiveDomainV4.SituationKind.UPCOMING_EVENT,x.kind);assertNotNull(x.relevantUntil);
        Calendar event=Calendar.getInstance();event.setTimeInMillis(x.relevantUntil);assertEquals(Calendar.SATURDAY,event.get(Calendar.DAY_OF_WEEK));assertEquals(18,event.get(Calendar.HOUR_OF_DAY));
    }

    @Test public void realWhatsAppE2ePhraseBecomesSameDayDeadline(){
        Calendar base=Calendar.getInstance();base.set(2026,Calendar.AUGUST,28,14,4,0);base.set(Calendar.MILLISECOND,0);long now=base.getTimeInMillis();
        String text="CORTEX_E2E_001 يا كريم، محتاج منك تبعتلي ملف التصميم قبل الساعة 5 النهارده. لو مش هتلحق ابعتلي وقولي عشان أتصرف.";
        CognitiveSituationEngineV4.Candidate x=CognitiveSituationEngineV4.detect("m_e2e","CONVERSATION","Kareem Abdel Nasser",text,"com.whatsapp",now,.68,text,now);
        assertNotNull(x);assertEquals(CognitiveDomainV4.SituationKind.DEADLINE,x.kind);assertNotNull(x.relevantUntil);
        Calendar deadline=Calendar.getInstance();deadline.setTimeInMillis(x.relevantUntil);assertEquals(17,deadline.get(Calendar.HOUR_OF_DAY));assertEquals(28,deadline.get(Calendar.DAY_OF_MONTH));
    }

    @Test public void explicitTodayDeadlineDoesNotSlideToTomorrowAfterItPasses(){
        Calendar base=Calendar.getInstance();base.set(2026,Calendar.AUGUST,28,18,30,0);base.set(Calendar.MILLISECOND,0);long now=base.getTimeInMillis();
        Long deadline=CognitiveSituationEngineV4.parseExplicitFutureTime("لازم قبل الساعة 5 النهارده",now);assertNotNull(deadline);
        Calendar d=Calendar.getInstance();d.setTimeInMillis(deadline);assertEquals(28,d.get(Calendar.DAY_OF_MONTH));assertEquals(17,d.get(Calendar.HOUR_OF_DAY));assertTrue(deadline<now);
    }

    @Test public void ambiguousTodayHourUsesMorningWhenItIsTheNextOccurrence(){
        Calendar base=Calendar.getInstance();base.set(2026,Calendar.AUGUST,28,3,0,0);base.set(Calendar.MILLISECOND,0);long now=base.getTimeInMillis();
        Long deadline=CognitiveSituationEngineV4.parseExplicitFutureTime("قبل الساعة 5 النهارده",now);assertNotNull(deadline);
        Calendar d=Calendar.getInstance();d.setTimeInMillis(deadline);assertEquals(5,d.get(Calendar.HOUR_OF_DAY));assertEquals(28,d.get(Calendar.DAY_OF_MONTH));
    }

    @Test public void missedCallIsFollowUpWithoutInventedUrgency(){
        long now=System.currentTimeMillis();String text="missed call sameh john";
        CognitiveSituationEngineV4.Candidate x=CognitiveSituationEngineV4.detect("m3","CONVERSATION","Missed call Sameh John","Missed call Sameh John","com.samsung.android.dialer",now,.52,text,now);
        assertNotNull(x);assertEquals(CognitiveDomainV4.SituationKind.FOLLOW_UP,x.kind);assertTrue(x.attention<.5);assertNull(x.relevantUntil);
    }

    @Test public void foodPromotionDoesNotBecomeSituation(){
        long now=System.currentTimeMillis();String text="don't miss up to 50% off massive offers delivered straight to your door";
        assertNull(CognitiveSituationEngineV4.detect("m4","CONVERSATION","Don't miss up to 50% OFF",text,"com.talabat",now,.55,text,now));
    }
}
