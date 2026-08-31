package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Pins the Relay V2 control-result correlation gate: an inbound control result may only be
 * treated as authoritative when it names an outstanding request id Cortex itself minted, of the
 * matching kind, inside the TTL, and not already answered.
 */
public final class CortexRelayControlCorrelatorV2Test {

    private static final long T0 = 1_700_000_000_000L;

    @Before public void reset() { CortexRelayControlCorrelatorV2.reset(); }

    @Test public void correlatesExactlyOneAuthoritativeResultPerRequest() {
        assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-1", "action", T0));
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.ACCEPTED_FIRST,
                CortexRelayControlCorrelatorV2.correlate("req-1", "action", T0 + 100L));
        // A replayed copy of the same result is bounded to a non-authoritative verdict.
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.DUPLICATE_REPLAY,
                CortexRelayControlCorrelatorV2.correlate("req-1", "action", T0 + 200L));
    }

    @Test public void rejectsResultForARequestCortexNeverIssued() {
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST,
                CortexRelayControlCorrelatorV2.correlate("forged-1", "action", T0));
        assertFalse(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST.authoritative());
    }

    @Test public void rejectsResultWithNoRequestId() {
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.MISSING_REQUEST_ID,
                CortexRelayControlCorrelatorV2.correlate("", "action", T0));
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.MISSING_REQUEST_ID,
                CortexRelayControlCorrelatorV2.correlate(null, "action", T0));
    }

    @Test public void rejectsResultOfTheWrongControlKind() {
        assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-kind", "action", T0));
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.KIND_MISMATCH,
                CortexRelayControlCorrelatorV2.correlate("req-kind", "policy", T0 + 10L));
        // The outstanding request survives a kind mismatch and still correlates correctly.
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.ACCEPTED_FIRST,
                CortexRelayControlCorrelatorV2.correlate("req-kind", "action", T0 + 20L));
    }

    @Test public void expiresOutstandingRequestsAfterTtl() {
        assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-ttl", "policy", T0));
        long past = T0 + CortexRelayControlCorrelatorV2.TTL_MS + 1L;
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST,
                CortexRelayControlCorrelatorV2.correlate("req-ttl", "policy", past));
        assertEquals(0, CortexRelayControlCorrelatorV2.outstandingCount(past));
    }

    @Test public void boundsOutstandingRequestsAndEvictsOldestFirst() {
        for (int i = 0; i <= CortexRelayControlCorrelatorV2.MAX_OUTSTANDING; i++) {
            assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-" + i, "action", T0));
        }
        assertEquals(CortexRelayControlCorrelatorV2.MAX_OUTSTANDING,
                CortexRelayControlCorrelatorV2.outstandingCount(T0));
        // The oldest id was evicted; the newest survives.
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST,
                CortexRelayControlCorrelatorV2.correlate("req-0", "action", T0));
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.ACCEPTED_FIRST,
                CortexRelayControlCorrelatorV2.correlate(
                        "req-" + CortexRelayControlCorrelatorV2.MAX_OUTSTANDING, "action", T0));
    }

    @Test public void refusesToReuseARequestId() {
        assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-once", "action", T0));
        assertFalse(CortexRelayControlCorrelatorV2.registerOutstanding("req-once", "action", T0));
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.ACCEPTED_FIRST,
                CortexRelayControlCorrelatorV2.correlate("req-once", "action", T0));
        // Still refused after it has been answered, so a replay cannot re-arm it.
        assertFalse(CortexRelayControlCorrelatorV2.registerOutstanding("req-once", "action", T0));
    }

    @Test public void rejectsOverlongRequestId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= CortexRelayControlCorrelatorV2.MAX_REQUEST_ID_CHARS; i++) sb.append('x');
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.MISSING_REQUEST_ID,
                CortexRelayControlCorrelatorV2.correlate(sb.toString(), "action", T0));
    }

    @Test public void sessionTeardownDropsOutstandingRequests() {
        assertTrue(CortexRelayControlCorrelatorV2.registerOutstanding("req-session", "action", T0));
        CortexRelayControlCorrelatorV2.reset();
        assertEquals(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST,
                CortexRelayControlCorrelatorV2.correlate("req-session", "action", T0));
    }

    @Test public void onlyAcceptedFirstIsAuthoritative() {
        assertTrue(CortexRelayControlCorrelatorV2.Verdict.ACCEPTED_FIRST.authoritative());
        assertFalse(CortexRelayControlCorrelatorV2.Verdict.DUPLICATE_REPLAY.authoritative());
        assertFalse(CortexRelayControlCorrelatorV2.Verdict.UNKNOWN_REQUEST.authoritative());
        assertFalse(CortexRelayControlCorrelatorV2.Verdict.KIND_MISMATCH.authoritative());
        assertFalse(CortexRelayControlCorrelatorV2.Verdict.MISSING_REQUEST_ID.authoritative());
    }
}
