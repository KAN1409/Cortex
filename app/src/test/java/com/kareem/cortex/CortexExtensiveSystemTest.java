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

/** Single authoritative CI test for Cortex. */
@RunWith(RobolectricTestRunner.class)
public class CortexExtensiveSystemTest {
    @Test public void cortexExtensiveSystemContract() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("com.kareem.cortex", context.getPackageName());

        assertTrue("recovery gate must stay active in this recovery lineage", StartupSafetyGate.active());
        assertTrue(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.CORE_UI));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.OCR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.ASR_NATIVE));
        assertFalse(CapabilitySupervisor.allowed(context, CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE));

        VaultDb db = new VaultDb(context);
        try {
            Cursor quick = db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)", null);
            assertTrue(quick.moveToFirst());
            assertEquals("ok", quick.getString(0).toLowerCase());
            quick.close();

            CognitiveStore.ensure(db);
            StatefulMeaningStore.ensure(db.getWritableDatabase());
            CommitmentLifecycleStore.ensure(db.getWritableDatabase());
            CognitiveShadowStore.ensure(db.getWritableDatabase());

            CortexFunctionalSelfTest.Report report = CortexFunctionalSelfTest.run(context);
            assertEquals("Production functional self-test reported failures: " + report.text(), 0, report.fail);
            assertTrue("Extensive self-test did not exercise enough real paths: " + report.text(), report.pass >= 12);

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

            DeterministicSemanticRecovery.Classification request =
                    DeterministicSemanticRecovery.classify("conversation_notification", "Ahmed", "Please send the quotation today");
            assertEquals("ACTION", request.attentionKind);
            assertEquals("action_request", request.type);
            DeterministicSemanticRecovery.Classification security =
                    DeterministicSemanticRecovery.classify("notification", "Google", "Critical security alert for saved passwords");
            assertEquals("ACTION", security.attentionKind);
            assertEquals("security_alert", security.type);
            DeterministicSemanticRecovery.Classification ordinary =
                    DeterministicSemanticRecovery.classify("conversation_notification", "Nasser", "وصلت البيت");
            assertNull(ordinary.attentionKind);
            assertEquals("conversation_message", ordinary.type);
            assertTrue(ordinary.confidence >= .75);

            // End-to-end read path must remain executable even when there is no fixture data.
            assertTrue(SemanticMemoryBridge.sync(db, 50) >= 0);
            assertNotNull(CognitiveNowReadModel.load(db.getReadableDatabase(), 8));

            String trace = AttentionTraceExporter.export(db.getReadableDatabase());
            assertTrue(trace.contains("CORTEX_ATTENTION_TRACE_V4"));
            assertTrue(trace.contains("commitment_count"));
            assertTrue(trace.contains("overdue_commitment_count"));

            assertEquals("application/json", DiagnosticFileShare.mimeType("cortex-attention-trace.json"));
            assertEquals("text/plain", DiagnosticFileShare.mimeType("cortex-java-crash.txt"));
        } finally {
            db.close();
        }
    }
}
