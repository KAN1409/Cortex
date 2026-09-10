package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Single authoritative CI test for Cortex.
 *
 * This deliberately exercises production health evaluators instead of maintaining dozens of
 * disconnected test counters. The test fails only for broken Cortex wiring/contracts; missing
 * user permissions, credentials or optional models are represented by runtime status and are not
 * faked as CI failures.
 */
@RunWith(RobolectricTestRunner.class)
public class CortexExtensiveSystemTest {
    @Test public void cortexExtensiveSystemContract() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("com.kareem.cortex", context.getPackageName());

        // Recovery/startup contract: UI survives while native engines remain isolated.
        assertTrue("recovery gate must stay active in this recovery lineage", StartupSafetyGate.active());
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.CORE_UI));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.OCR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.ASR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));

        VaultDb db = new VaultDb(context);
        try {
            // Real database integrity, schema and production self-test.
            Cursor quick = db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)", null);
            assertTrue(quick.moveToFirst());
            assertEquals("ok", quick.getString(0).toLowerCase());
            quick.close();

            CognitiveStore.ensure(db);
            CommitmentLifecycleStore.ensure(db.getWritableDatabase());
            CognitiveShadowStore.ensure(db.getWritableDatabase());

            CortexFunctionalSelfTest.Report report = CortexFunctionalSelfTest.run(context);
            assertEquals("Production functional self-test reported failures: " + report.text(), 0, report.fail);
            assertTrue("Extensive self-test did not exercise enough real paths: " + report.text(), report.pass >= 12);

            // The 43-capability registry is the runtime source of truth. Every capability must be
            // classifiable; permissions/setup may legitimately be non-green on CI/device profiles.
            assertEquals(43, CortexCapabilityRegistry.all().size());
            Set<String> keys = new HashSet<>();
            for (CortexCapabilityRegistry.Capability capability : CortexCapabilityRegistry.all()) {
                assertTrue("duplicate capability key " + capability.key, keys.add(capability.key));
                CortexCapabilityRegistry.State state = CortexCapabilityRegistry.evaluate(context, db, capability);
                assertNotNull(state);
                assertNotNull(state.status);
                assertFalse("capability has no status: " + capability.key, state.status.trim().isEmpty());
                assertNotEquals("capability evaluator missing: " + capability.key,
                        CortexCapabilityRegistry.NOT_VERIFIED, state.status);
            }

            // Cognitive observability and commitment lifecycle must be exportable from the real DB.
            String trace = AttentionTraceExporter.export(db.getReadableDatabase());
            assertTrue(trace.contains("CORTEX_ATTENTION_TRACE_V4"));
            assertTrue(trace.contains("commitment_count"));
            assertTrue(trace.contains("overdue_commitment_count"));

            // Full diagnostic transfer must remain a real JSON attachment contract.
            assertEquals("application/json", DiagnosticFileShare.mimeForFileName("cortex-attention-trace.json"));
            assertEquals("text/plain", DiagnosticFileShare.mimeForFileName("cortex-java-crash.txt"));
        } finally {
            db.close();
        }
    }
}
