package com.kareem.cortex;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CognitiveWorldStateTest {
    private static final long NOW = 2_000_000_000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private CognitiveWorldState.Observation observation(
            long id,
            String link,
            String type,
            String state,
            String subject,
            String summary,
            double confidence,
            double urgency,
            double actionability,
            double relevance,
            double risk,
            double novelty,
            long deadline,
            long occurredAt,
            boolean material,
            boolean request,
            boolean severe) {
        return new CognitiveWorldState.Observation(
                id, link, type, state, subject, summary, confidence, urgency,
                actionability, relevance, risk, novelty, deadline, occurredAt,
                material, request, severe);
    }

    @Test
    public void sameSituationAccumulatesEvidenceInsteadOfCreatingNewWorldItems() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(1, "google-account", "security_alert", "OPEN",
                "Google security alert", "Saved passwords found online",
                .94, .80, .90, .85, .92, .80, 0, NOW - HOUR,
                true, false, false));
        world.observe(observation(1, "google-account", "security_alert", "OPEN",
                "Google Play services", "Password security warning",
                .96, .82, .92, .85, .94, .20, 0, NOW - HOUR / 2,
                false, false, false));

        CognitiveWorldState.Situation s = world.getSituation(1);
        assertEquals(2, s.evidenceCount);
        assertEquals(2, s.repeatedCount);
        assertEquals(1, world.attentionCandidates(NOW).size());
    }

    @Test
    public void ordinaryCallWithoutContextDoesNotSurface() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(2, "person:ahmed", "call_event", "OPEN",
                "Ahmed", "Missed call",
                .96, .35, .20, .35, .05, .50, 0, NOW - 15 * 60_000L,
                true, false, false));

        assertTrue(world.rankNow(NOW, 5).isEmpty());
    }

    @Test
    public void sameCallBecomesAttentionWorthyWhenLinkedToDueCommitment() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(3, "person:ahmed", "call_event", "OPEN",
                "Ahmed", "Missed call about quotation",
                .96, .55, .35, .50, .10, .65, 0, NOW - 15 * 60_000L,
                true, false, false));
        world.observe(observation(3, "person:ahmed", "call_event", "OPEN",
                "Ahmed", "Second missed call about quotation",
                .96, .65, .40, .55, .10, .70, 0, NOW - 5 * 60_000L,
                true, false, false));
        world.putCommitment(new CognitiveWorldState.Commitment(
                "commitment-1", "person:ahmed", "Ahmed owes revised quotation",
                NOW + 2 * HOUR, true, .95));

        List<AttentionDecisionEngine.Decision> now = world.rankNow(NOW, 5);
        assertEquals(1, now.size());
        assertEquals(3L, now.get(0).candidate.situationId);
        assertTrue(now.get(0).candidate.linkedOpenCommitment);
    }

    @Test
    public void closedCommitmentStopsInfluencingAttention() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(4, "person:ahmed", "call_event", "OPEN",
                "Ahmed", "Repeated missed calls",
                .96, .55, .35, .55, .10, .70, 0, NOW - 5 * 60_000L,
                true, false, false));
        world.putCommitment(new CognitiveWorldState.Commitment(
                "commitment-2", "person:ahmed", "Quotation",
                NOW + HOUR, false, .95));

        AttentionDecisionEngine.Candidate c = world.attentionCandidates(NOW).get(0);
        assertFalse(c.linkedOpenCommitment);
        assertTrue(world.rankNow(NOW, 5).isEmpty());
    }

    @Test
    public void resolvedSituationIsRemovedFromAttentionWithoutDeletingEvidence() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(5, "task:quote", "commitment", "WAITING",
                "Quotation", "Need to send quotation today",
                .95, .85, .90, .95, .20, .80, NOW + HOUR, NOW - HOUR,
                true, false, false));
        world.observe(observation(5, "task:quote", "commitment", "RESOLVED",
                "Quotation", "Quotation sent",
                .98, .10, .10, .95, .05, .90, NOW + HOUR, NOW,
                true, false, false));

        CognitiveWorldState.Situation s = world.getSituation(5);
        assertEquals(2, s.evidenceCount);
        assertFalse(s.unresolved);
        assertEquals(0L, s.deadlineAt);
        assertTrue(world.rankNow(NOW, 5).isEmpty());
    }

    @Test
    public void expiredContextDoesNotInflateImportance() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(6, "person:nabil", "conversation_message", "OPEN",
                "Nabil", "Okay thanks",
                .94, .10, .10, .20, .00, .20, 0, NOW - HOUR,
                false, false, false));
        world.putContext(new CognitiveWorldState.Context(
                "person:nabil", .95, .90, .90, NOW - 1));

        AttentionDecisionEngine.Candidate c = world.attentionCandidates(NOW).get(0);
        assertTrue(c.personalRelevance < .95);
        assertFalse(AttentionDecisionEngine.evaluate(c).surfaceNow);
    }

    @Test
    public void activeContextCanElevateRiskAndRelevanceButDoesNotBypassMeaningRules() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(7, "person:nabil", "social_update", "OPEN",
                "Nabil", "Nabil has a new story",
                .95, .10, .00, .20, .00, .20, 0, NOW - HOUR,
                false, false, false));
        world.putContext(new CognitiveWorldState.Context(
                "person:nabil", .95, .60, .20, NOW + HOUR));

        AttentionDecisionEngine.Candidate c = world.attentionCandidates(NOW).get(0);
        assertEquals(.95, c.personalRelevance, .0001);
        assertFalse(AttentionDecisionEngine.evaluate(c).surfaceNow);
    }

    @Test
    public void materialUpdateReplacesCurrentSummaryButKeepsHistoryCounts() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(8, "account:google", "security_alert", "OPEN",
                "Google", "Password found online",
                .93, .70, .80, .90, .80, .70, 0, NOW - HOUR,
                true, false, false));
        world.observe(observation(8, "account:google", "security_alert", "ESCALATED",
                "Google", "Unauthorized login detected; change password now",
                .98, .95, .95, .95, .98, .95, 0, NOW,
                true, true, false));

        CognitiveWorldState.Situation s = world.getSituation(8);
        assertEquals("Unauthorized login detected; change password now", s.summary);
        assertEquals(2, s.evidenceCount);
        assertTrue(world.rankNow(NOW, 5).get(0).surfaceNow);
    }

    @Test
    public void newerDeescalationChangesCurrentRiskButKeepsHistoricalPeak() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(9, "account:test", "security_alert", "ESCALATED",
                "Account", "Unauthorized login detected",
                .97, .95, .90, .90, .96, .90, 0, NOW - HOUR,
                true, true, false));
        world.observe(observation(9, "account:test", "account_update", "OPEN",
                "Account", "Login reviewed; no further action requested",
                .96, .20, .15, .70, .10, .35, 0, NOW,
                true, false, false));

        CognitiveWorldState.Situation s = world.getSituation(9);
        assertEquals(.20, s.urgency, .0001);
        assertEquals(.10, s.risk, .0001);
        assertEquals(.95, s.peakUrgency, .0001);
        assertEquals(.96, s.peakRisk, .0001);
        assertFalse(s.explicitRequest);
    }

    @Test
    public void outOfOrderOlderEvidenceCannotOverwriteNewerCurrentTruth() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(10, "task:po", "commitment", "OPEN",
                "PO", "Latest status is awaiting final signature",
                .95, .70, .80, .90, .15, .70, NOW + 4 * HOUR, NOW,
                true, true, false));
        world.observe(observation(10, "task:po", "commitment", "OPEN",
                "PO", "Old draft was still being prepared",
                .92, .30, .30, .60, .05, .30, NOW + HOUR, NOW - 3 * HOUR,
                true, false, false));

        CognitiveWorldState.Situation s = world.getSituation(10);
        assertEquals("Latest status is awaiting final signature", s.summary);
        assertEquals(.70, s.urgency, .0001);
        assertEquals(NOW + 4 * HOUR, s.deadlineAt);
        assertEquals(NOW, s.lastSeenAt);
        assertEquals(2, s.evidenceCount);
    }

    @Test
    public void newerExplicitDeadlineCanReplaceEarlierSchedule() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(11, "task:quote", "commitment", "WAITING",
                "Quotation", "Send quotation at noon",
                .95, .70, .80, .90, .10, .70, NOW + HOUR, NOW - HOUR,
                true, true, false));
        world.observe(observation(11, "task:quote", "commitment", "WAITING",
                "Quotation", "Deadline moved to end of day",
                .96, .55, .80, .90, .10, .80, NOW + 6 * HOUR, NOW,
                true, true, false));

        CognitiveWorldState.Situation s = world.getSituation(11);
        assertEquals(NOW + 6 * HOUR, s.deadlineAt);
    }

    @Test
    public void globalRankingUsesWorldContextNotCaptureOrder() {
        CognitiveWorldState world = new CognitiveWorldState();
        world.observe(observation(20, "weather:new-cairo", "weather_event", "OPEN",
                "New Cairo", "24° clear",
                .98, .05, .00, .20, .00, .20, 0, NOW,
                false, false, false));
        world.observe(observation(21, "account:google", "security_alert", "OPEN",
                "Google security alert", "Compromised password; change it now",
                .97, .95, .95, .95, .95, .90, 0, NOW - HOUR,
                true, false, false));
        world.observe(observation(22, "task:quote", "commitment", "WAITING",
                "Quotation", "Send revised quotation today",
                .95, .80, .85, .95, .20, .70, NOW + 3 * HOUR, NOW - 2 * HOUR,
                true, false, false));
        world.putCommitment(new CognitiveWorldState.Commitment(
                "commitment-3", "task:quote", "Send revised quotation",
                NOW + 3 * HOUR, true, .95));

        List<AttentionDecisionEngine.Decision> ranked = world.rankNow(NOW, 5);
        assertEquals(2, ranked.size());
        assertEquals(21L, ranked.get(0).candidate.situationId);
        assertEquals(22L, ranked.get(1).candidate.situationId);
    }
}
