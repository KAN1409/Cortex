package com.kareem.cortex;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AttentionShadowComparatorTest {
    private static final long NOW = 3_000_000_000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private AttentionDecisionEngine.Candidate c(long id, String type, String subject, String summary,
                                                double urgency, double actionability, double relevance,
                                                double risk, long deadline, boolean commitment,
                                                boolean material, boolean request) {
        return new AttentionDecisionEngine.Candidate(
                id, type, "OPEN", subject, summary,
                .95, urgency, actionability, relevance, risk, .70,
                deadline, NOW, 1, 2, true, commitment, material, request, false);
    }

    @Test
    public void shadowReportSeparatesRecoveryNoiseSuppressionAndAgreement() {
        AttentionDecisionEngine.Candidate security = c(
                1, "security_alert", "Google", "Compromised password; change it now",
                .95, .95, .95, .95, 0, false, true, false);
        AttentionDecisionEngine.Candidate contextualCommitment = c(
                2, "call_event", "Ahmed", "Missed call linked to quotation due today",
                .75, .75, .90, .20, NOW + 2 * HOUR, true, true, false);
        AttentionDecisionEngine.Candidate routineWeather = c(
                3, "weather_event", "New Cairo", "24° clear",
                .10, .05, .20, .00, 0, false, false, false);

        List<AttentionShadowComparator.LegacyDecision> legacy = Arrays.asList(
                new AttentionShadowComparator.LegacyDecision(1, true, "legacy security projection"),
                new AttentionShadowComparator.LegacyDecision(2, false, "legacy call suppressed"),
                new AttentionShadowComparator.LegacyDecision(3, true, "legacy noisy projection"));

        AttentionShadowComparator.Report r = AttentionShadowComparator.compare(
                legacy, Arrays.asList(routineWeather, contextualCommitment, security), 5);

        assertEquals(3, r.comparisons.size());
        assertEquals(1, r.agreements);
        assertEquals(1, r.cognitiveRecoveries);
        assertEquals(1, r.cognitiveNoiseSuppressions);
        assertEquals(AttentionShadowComparator.Delta.AGREES_SURFACE, r.comparisons.get(0).delta);
        assertEquals(AttentionShadowComparator.Delta.NEW_SURFACES_LEGACY_MISSED, r.comparisons.get(1).delta);
        assertEquals(AttentionShadowComparator.Delta.NEW_SUPPRESSES_LEGACY_NOISE, r.comparisons.get(2).delta);
    }

    @Test
    public void shadowComparisonUsesGlobalNowCapacity() {
        AttentionDecisionEngine.Candidate critical = c(
                10, "security_alert", "Account", "Unauthorized login; act now",
                .95, .95, .95, .95, 0, false, true, false);
        AttentionDecisionEngine.Candidate due = c(
                11, "commitment", "Quotation", "Send quotation today",
                .80, .85, .95, .20, NOW + 3 * HOUR, true, true, false);

        AttentionShadowComparator.Report r = AttentionShadowComparator.compare(
                Arrays.asList(
                        new AttentionShadowComparator.LegacyDecision(10, true, "legacy"),
                        new AttentionShadowComparator.LegacyDecision(11, true, "legacy")),
                Arrays.asList(due, critical),
                1);

        assertEquals(AttentionShadowComparator.Delta.AGREES_SURFACE, r.comparisons.get(0).delta);
        assertEquals(1, r.comparisons.get(0).cognitiveRank);
        assertEquals(AttentionShadowComparator.Delta.NEW_SUPPRESSES_LEGACY_NOISE, r.comparisons.get(1).delta);
    }
}
