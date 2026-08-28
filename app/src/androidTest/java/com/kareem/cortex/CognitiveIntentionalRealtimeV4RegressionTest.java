package com.kareem.cortex;

import static org.junit.Assert.*;
import java.util.Calendar;
import org.junit.Test;

public final class CognitiveIntentionalRealtimeV4RegressionTest {
    @Test public void spokenArabicClockInIntentionalCaptureBecomesExplicitTomorrowTime(){
        String normalized=CognitiveIntentionalRealtimeV4.normalizeSpokenClock("بكرة الساعة عشرة أأكل البغبغان");
        assertTrue(normalized.contains("الساعة 10"));
        assertTrue(CognitiveIntentionalRealtimeV4.hasExplicitFutureDay(normalized));

        Calendar anchor=Calendar.getInstance();
        anchor.set(2026,Calendar.AUGUST,28,21,48,0);anchor.set(Calendar.MILLISECOND,0);
        Long eventAt=CognitiveSituationEngineV4.parseExplicitFutureTime(normalized,anchor.getTimeInMillis());
        assertNotNull(eventAt);
        Calendar event=Calendar.getInstance();event.setTimeInMillis(eventAt.longValue());
        assertEquals(29,event.get(Calendar.DAY_OF_MONTH));
        assertEquals(10,event.get(Calendar.HOUR_OF_DAY));
        assertEquals(0,event.get(Calendar.MINUTE));
    }

    @Test public void plainVoiceNoteDoesNotInventTimedSituation(){
        String normalized=CognitiveIntentionalRealtimeV4.normalizeSpokenClock("كنت بفكر في أكل البغبغان");
        assertFalse(CognitiveIntentionalRealtimeV4.hasExplicitFutureDay(normalized));
    }

    @Test public void onlyUserIntentionalSourcesUseTimedFallback(){
        assertTrue(CognitiveIntentionalRealtimeV4.intentionalSource("manual_recording"));
        assertTrue(CognitiveIntentionalRealtimeV4.intentionalSource("manual"));
        assertFalse(CognitiveIntentionalRealtimeV4.intentionalSource("com.whatsapp"));
    }
}
