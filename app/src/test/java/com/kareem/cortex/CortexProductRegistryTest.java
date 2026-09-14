package com.kareem.cortex;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CortexProductRegistryTest {
    @Test public void destinationRegistryHasExactlyFourPrimaryProductAreas() {
        CortexDestinationRegistry.validateOrThrow();
        assertEquals(4, CortexDestinationRegistry.primary().size());
        Set<String> actual = new HashSet<>();
        for (CortexDestinationRegistry.Destination d : CortexDestinationRegistry.primary()) actual.add(d.destinationId);
        assertEquals(new HashSet<>(Arrays.asList("NOW", "WORK", "MEMORY", "CAPTURE")), actual);
    }

    @Test public void actionRegistryRejectsSemanticIdentityAndTruthContractDrift() {
        CortexDestinationRegistry.validateOrThrow();
        CortexActionRegistry.validateOrThrow();
        assertEquals(56, CortexActionRegistry.all().size());
    }

    @Test public void externalActionsNeverTreatDispatchAsVerifiedCompletion() {
        for (CortexActionRegistry.Action a : CortexActionRegistry.all()) {
            if (a.category != CortexActionRegistry.Category.EXTERNAL_HANDOFF) continue;
            assertTrue(a.verificationPolicy == CortexActionRegistry.VerificationPolicy.HANDOFF_ONLY
                    || a.verificationPolicy == CortexActionRegistry.VerificationPolicy.EFFECT_OBSERVED);
            assertEquals(CortexActionRegistry.EvidencePolicy.HANDOFF_RECEIPT, a.evidencePolicy);
        }
    }

    @Test public void localMutationsRequireReadBackReceiptEvidence() {
        for (CortexActionRegistry.Action a : CortexActionRegistry.all()) {
            if (a.category != CortexActionRegistry.Category.LOCAL_MUTATION) continue;
            assertEquals(CortexActionRegistry.VerificationPolicy.LOCAL_READ_BACK, a.verificationPolicy);
            assertEquals(CortexActionRegistry.EvidencePolicy.RECEIPT_REQUIRED, a.evidencePolicy);
        }
    }

    @Test public void engineeringDiagnosticsDoNotBecomePrimaryDestinations() {
        for (CortexDestinationRegistry.Destination d : CortexDestinationRegistry.primary()) {
            assertNotEquals(CortexDestinationRegistry.SYSTEM_HEALTH, d.destinationId);
            assertNotEquals(CortexDestinationRegistry.SETTINGS, d.destinationId);
        }
    }
}
