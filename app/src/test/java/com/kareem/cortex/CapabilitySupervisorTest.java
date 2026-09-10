package com.kareem.cortex;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CapabilitySupervisorTest {
    private Context context;

    @Before public void reset() {
        context = ApplicationProvider.getApplicationContext();
        SafeCoreRuntime.resetForTests();
        context.getSharedPreferences("cortex_capability_supervisor", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void coreUiRemainsAvailableDuringRecoveryQuarantine() {
        assertTrue(StartupSafetyGate.active());
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.CORE_UI));
        assertEquals(CapabilitySupervisor.State.READY,
                CapabilitySupervisor.status(context, CapabilitySupervisor.Capability.CORE_UI).state);
    }

    @Test public void coldStartBlocksSafeCoreAndNativeCapabilities() {
        assertEquals(SafeCoreRuntime.Phase.COLD_START, SafeCoreRuntime.phase());
        for (CapabilitySupervisor.Capability capability : CapabilitySupervisor.Capability.values()) {
            if (capability == CapabilitySupervisor.Capability.CORE_UI) continue;
            assertFalse(capability.name(), CapabilitySupervisor.allowed(context, capability));
            assertEquals(capability.name(), CapabilitySupervisor.State.QUARANTINED,
                    CapabilitySupervisor.status(context, capability).state);
        }
    }

    @Test public void safeCoreReadyReEnablesOnlyExplicitJavaSqliteCapabilities() {
        SafeCoreRuntime.forceReadyForTests();
        assertTrue(StartupSafetyGate.active());
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.DATABASE));
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE));
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION));
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING));

        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.OCR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.ASR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.PROACTIVE_ACTIONS));
    }

    @Test public void oneSafeCoreFailureIsDegradedButStillRunnable() {
        SafeCoreRuntime.forceReadyForTests();
        CapabilitySupervisor.Capability cap=CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION;
        CapabilitySupervisor.recordFailure(context,cap,new IllegalStateException("transient failure"));
        CapabilitySupervisor.Status status=CapabilitySupervisor.status(context,cap);
        assertEquals(CapabilitySupervisor.State.DEGRADED,status.state);
        assertEquals(1,status.consecutiveFailures);
        assertTrue(status.allowed());
        assertTrue(CapabilitySupervisor.allowed(context,cap));
    }

    @Test public void secondConsecutiveFailureOpensOnlySelectedSafeCoreBreaker() {
        SafeCoreRuntime.forceReadyForTests();
        CapabilitySupervisor.Capability cap=CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION;
        CapabilitySupervisor.recordFailure(context,cap,new IllegalStateException("first failure"));
        CapabilitySupervisor.recordFailure(context,cap,new IllegalStateException("second failure"));
        assertEquals(CapabilitySupervisor.State.QUARANTINED,CapabilitySupervisor.status(context,cap).state);
        assertFalse(CapabilitySupervisor.allowed(context,cap));
        assertTrue(CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.DATABASE));
        assertTrue(CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE));
    }

    @Test public void safeCoreBreakerBecomesHalfOpenAfterCooldownAndCanRecover() {
        SafeCoreRuntime.forceReadyForTests();
        CapabilitySupervisor.Capability cap=CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING;
        CapabilitySupervisor.recordFailure(context,cap,new IllegalStateException("first failure"));
        CapabilitySupervisor.recordFailure(context,cap,new IllegalStateException("second failure"));
        assertFalse(CapabilitySupervisor.allowed(context,cap));

        String key=CapabilitySupervisor.key(cap);
        context.getSharedPreferences("cortex_capability_supervisor",Context.MODE_PRIVATE).edit()
                .putLong(key+".last_failure_at",System.currentTimeMillis()-CapabilitySupervisor.BREAKER_PROBE_DELAY_MS-1000L)
                .commit();
        CapabilitySupervisor.Status halfOpen=CapabilitySupervisor.status(context,cap);
        assertEquals(CapabilitySupervisor.State.DEGRADED,halfOpen.state);
        assertTrue(halfOpen.allowed());

        CapabilitySupervisor.recordHealthy(context,cap);
        assertEquals(CapabilitySupervisor.State.READY,CapabilitySupervisor.status(context,cap).state);
        assertTrue(CapabilitySupervisor.allowed(context,cap));
    }

    @Test public void nativeCapabilitiesRemainQuarantinedEvenWhenSafeCoreIsReady() {
        SafeCoreRuntime.forceReadyForTests();
        CapabilitySupervisor.recordHealthy(context,CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE);
        assertEquals(CapabilitySupervisor.State.QUARANTINED,
                CapabilitySupervisor.status(context,CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE).state);
        assertFalse(CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.OCR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.ASR_NATIVE));
        assertTrue(CapabilitySupervisor.allowed(context,CapabilitySupervisor.Capability.DATABASE));
    }

    @Test public void capabilityKeysAreStableAndIndependent() {
        assertEquals("database", CapabilitySupervisor.key(CapabilitySupervisor.Capability.DATABASE));
        assertEquals("local_llm_native", CapabilitySupervisor.key(CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));
        assertNotEquals(
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.OCR_NATIVE),
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.ASR_NATIVE));
    }
}
