package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NowSurfaceQualityRegressionTest {

    @Test public void notificationChromeNeverBecomesCognition() {
        MasterRelevanceFilter.Signal stacked = new MasterRelevanceFilter.Signal(
                "notification", "com.android.systemui", "1 more notification", "", "{}",
                System.currentTimeMillis(), false);
        MasterRelevanceFilter.Signal copyUrl = new MasterRelevanceFilter.Signal(
                "notification", "com.example.app", "Tap to copy the URL for this app", "", "{}",
                System.currentTimeMillis(), false);
        assertEquals(MasterRelevanceFilter.Disposition.IGNORE, MasterRelevanceFilter.evaluateFast(stacked).disposition);
        assertEquals(MasterRelevanceFilter.Disposition.IGNORE, MasterRelevanceFilter.evaluateFast(copyUrl).disposition);
    }

    @Test public void shortUngroundedEventIsNotAVisibleChange() {
        assertTrue(CortexSurfacePolicy.isSurfaceNoise("EVENT", "بلاطيس البلاطي", "", "com.example.app"));
        assertFalse(CortexSurfacePolicy.meaningfulChange("EVENT", "بلاطيس البلاطي", "", "com.example.app", 70, .82));
        assertTrue(CortexSurfacePolicy.meaningfulChange("EVENT", "Appointment confirmed", "Your appointment is confirmed for 10:00", "com.example.app", 70, .82));
    }

    @Test public void scheduledReminderWaitsUntilNearItsTrigger() {
        long now = System.currentTimeMillis();
        PrimeBriefStore.Item reminder = new PrimeBriefStore.Item(
                1, 1, "REMINDER", "Call Ahmed", "Remind me to call Ahmed", "manual", "open",
                .90, 70, 1, 1, now + 10L * 60L * 60L * 1000L, now, now, 1, null);
        AttentionEngine.Decision d = AttentionEngine.evaluate(reminder, now);
        assertNotEquals(AttentionEngine.Band.NOW, d.band);
    }

    @Test public void urgentActionCanStillEnterNow() {
        long now = System.currentTimeMillis();
        PrimeBriefStore.Item action = new PrimeBriefStore.Item(
                2, 2, "ACTION", "Send final validation file", "Please send the final validation file", "relay", "open",
                .90, 75, 2, 2, now + 2L * 60L * 60L * 1000L, now, now, 1, null);
        AttentionEngine.Decision d = AttentionEngine.evaluate(action, now);
        assertEquals(AttentionEngine.Band.NOW, d.band);
    }

    @Test public void lowValueReviewDoesNotConsumeNow() {
        long now = System.currentTimeMillis();
        assertFalse(CortexSurfacePolicy.reviewDeservesNow("ACTION", 52, .64, now - 60_000L, now));
        assertTrue(CortexSurfacePolicy.reviewDeservesNow("ACTION", 72, .82, now - 60_000L, now));
    }
}
