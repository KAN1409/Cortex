package com.kareem.cortex;

import org.junit.Test;

import static org.junit.Assert.*;

public final class CortexExecutionRouterTest {
    private static CortexExecutionRouter.Availability a(
            boolean normal, boolean assistant, boolean notifications,
            boolean accessibility, boolean persisted, boolean shizuku,
            boolean platformAsr, boolean localAsr) {
        return new CortexExecutionRouter.Availability(normal, assistant, notifications,
                accessibility, persisted, shizuku, platformAsr, localAsr);
    }

    @Test public void standardAndroidAlwaysBeatsShizukuForNormalActions() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.NORMAL_ANDROID_ACTION,
                a(true, false, false, true, true, true, false, false));
        assertEquals(CortexExecutionRouter.Lane.NORMAL_ANDROID, d.lane);
    }

    @Test public void assistantContextPrefersUserSelectedAssistantWithoutShizuku() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.ASSISTANT_CONTEXT,
                a(false, true, false, true, false, false, false, false));
        assertEquals(CortexExecutionRouter.Lane.ASSISTANT, d.lane);
    }

    @Test public void assistantContextFallsBackToAccessibilityNotShizuku() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.ASSISTANT_CONTEXT,
                a(false, false, false, true, false, true, false, false));
        assertEquals(CortexExecutionRouter.Lane.ACCESSIBILITY, d.lane);
    }

    @Test public void notificationContextRequiresActualListenerGrant() {
        assertEquals(CortexExecutionRouter.Lane.NOTIFICATION_LISTENER,
                CortexExecutionRouter.route(CortexExecutionRouter.Operation.NOTIFICATION_CONTEXT,
                        a(false, false, true, false, false, false, false, false)).lane);
        assertEquals(CortexExecutionRouter.Lane.UNAVAILABLE,
                CortexExecutionRouter.route(CortexExecutionRouter.Operation.NOTIFICATION_CONTEXT,
                        a(false, false, false, true, true, true, false, false)).lane);
    }

    @Test public void durablePrivilegeBeatsAccessibilityAndShizukuForProtectedSetting() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.PRIVILEGED_SETTING,
                a(false, false, false, true, true, true, false, false));
        assertEquals(CortexExecutionRouter.Lane.PERSISTED_PRIVILEGE, d.lane);
    }

    @Test public void shizukuIsOnlyAnOptionalLateFallback() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.PRIVILEGED_SETTING,
                a(false, false, false, false, false, true, false, false));
        assertEquals(CortexExecutionRouter.Lane.SHIZUKU_OPTIONAL, d.lane);
    }

    @Test public void coreRoutingDoesNotRequireShizuku() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.NORMAL_ANDROID_ACTION,
                a(true, false, false, false, false, false, false, false));
        assertTrue(d.available());
        assertNotEquals(CortexExecutionRouter.Lane.SHIZUKU_OPTIONAL, d.lane);
    }

    @Test public void voiceUsesVerifiedPlatformLocalAsrBeforeBundledModel() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.VOICE_TRANSCRIPTION,
                a(false, false, false, false, false, false, true, true));
        assertEquals(CortexExecutionRouter.Lane.PLATFORM_ON_DEVICE_ASR, d.lane);
    }

    @Test public void voiceFallsBackToCortexLocalModelWithNoCloudLane() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.VOICE_TRANSCRIPTION,
                a(false, false, false, false, false, false, false, true));
        assertEquals(CortexExecutionRouter.Lane.LOCAL_ASR_MODEL, d.lane);
        for (CortexExecutionRouter.Lane lane : CortexExecutionRouter.Lane.values()) {
            assertFalse("Router must never grow a paid/cloud execution lane",
                    lane.name().contains("CLOUD") || lane.name().contains("PAID"));
        }
    }

    @Test public void voiceIsExplicitlyUnavailableUntilALocalEngineIsActuallyReady() {
        CortexExecutionRouter.Decision d = CortexExecutionRouter.route(
                CortexExecutionRouter.Operation.VOICE_TRANSCRIPTION,
                a(false, false, false, false, false, true, false, false));
        assertEquals(CortexExecutionRouter.Lane.UNAVAILABLE, d.lane);
    }
}
