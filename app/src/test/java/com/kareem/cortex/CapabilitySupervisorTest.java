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

    @Test public void nativeBreakerStateCannotOpenTheSafeCoreByAccident() {
        SafeCoreRuntime.forceReadyForTests();
        CapabilitySupervisor.recordFailure(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE,
                new IllegalStateException("native probe failed"));
        CapabilitySupervisor.recordFailure(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE,
                new IllegalStateException("native probe failed again"));
        assertEquals(CapabilitySupervisor.State.QUARANTINED,
                CapabilitySupervisor.status(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE).state);
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.DATABASE));
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION));
    }

    @Test public void capabilityKeysAreStableAndIndependent() {
        assertEquals("database", CapabilitySupervisor.key(CapabilitySupervisor.Capability.DATABASE));
        assertEquals("local_llm_native", CapabilitySupervisor.key(CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));
        assertNotEquals(
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.OCR_NATIVE),
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.ASR_NATIVE));
    }
}
