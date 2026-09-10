package com.kareem.cortex;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CapabilitySupervisorTest {
    @Test public void coreUiRemainsAvailableDuringRecoveryQuarantine() {
        Context c = ApplicationProvider.getApplicationContext();
        assertTrue(StartupSafetyGate.active());
        assertTrue(CapabilitySupervisor.allowed(c, CapabilitySupervisor.Capability.CORE_UI));
        assertEquals(CapabilitySupervisor.State.READY,
                CapabilitySupervisor.status(c, CapabilitySupervisor.Capability.CORE_UI).state);
    }

    @Test public void persistenceBackgroundAndNativeCapabilitiesRemainIsolatedDuringRecovery() {
        Context c = ApplicationProvider.getApplicationContext();
        CapabilitySupervisor.Capability[] blocked = {
                CapabilitySupervisor.Capability.DATABASE,
                CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE,
                CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION,
                CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,
                CapabilitySupervisor.Capability.OCR_NATIVE,
                CapabilitySupervisor.Capability.ASR_NATIVE,
                CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE,
                CapabilitySupervisor.Capability.PROACTIVE_ACTIONS
        };
        for (CapabilitySupervisor.Capability capability : blocked) {
            assertFalse(capability.name(), CapabilitySupervisor.allowed(c, capability));
            assertEquals(capability.name(), CapabilitySupervisor.State.QUARANTINED,
                    CapabilitySupervisor.status(c, capability).state);
        }
    }

    @Test public void capabilityKeysAreStableAndIndependent() {
        assertEquals("database", CapabilitySupervisor.key(CapabilitySupervisor.Capability.DATABASE));
        assertEquals("local_llm_native", CapabilitySupervisor.key(CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));
        assertNotEquals(
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.OCR_NATIVE),
                CapabilitySupervisor.key(CapabilitySupervisor.Capability.ASR_NATIVE));
    }
}
