package com.kareem.cortex;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttentionDecisionEngineTest {
    private static final long NOW = 1_000_000_000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private AttentionDecisionEngine.Candidate c(
            long id,
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
            long deadlineAt,
            int repeated,
            int evidence,
            boolean unresolved,
            boolean commitment,
            boolean material,
            boolean request,
            boolean severeImpact) {
        return new AttentionDecisionEngine.Candidate(
                id, type, state, subject, summary, confidence, urgency,
                actionability, relevance, risk, novelty, deadlineAt, NOW,
                repeated, evidence, unresolved, commitment, material, request, severeImpact);
    }

    @Test
    public void googleSecuritySituationSurfacesOnce() {
        AttentionDecisionEngine.Candidate security = c(
                1, "security_alert", "OPEN", "Google security alert",
                "Saved passwords were found online; change them now",
                .95, .90, .95, .95, .95, .90,
                0, 1, 3, true, false, true, false, false);

        AttentionDecisionEngine.Decision d = AttentionDecisionEngine.evaluate(security);
        assertTrue(d.surfaceNow);
        assertTrue(d.reason.contains("security"));
    }

    @Test
    public void routineWeatherDoesNotSurface() {
        AttentionDecisionEngine.Candidate weather = c(
                2, "weather_event", "OPEN", "New Cairo",
                "23° and clear",
                .96, .10, .05, .30, .05, .20,
                0, 1, 1, true, false, false, false, false);

        assertFalse(AttentionDecisionEngine.evaluate(weather).surfaceNow);
    }

    @Test
    public void severeWeatherAffectingPlanSurfaces() {
        AttentionDecisionEngine.Candidate weather = c(
                3, "weather_event", "OPEN", "New Cairo",
                "Severe storm affects imminent trip",
                .94, .85, .75, .90, .80, .80,
                NOW + HOUR, 1, 2, true, false, true, false, true);

        assertTrue(AttentionDecisionEngine.evaluate(weather).surfaceNow);
    }

    @Test
    public void socialStoryStaysOutOfNow() {
        AttentionDecisionEngine.Candidate story = c(
                4, "social_update", "OPEN", "Nabil",
                "Nabil has a new story",
                .93, .05, .00, .20, .00, .30,
                0, 1, 1, true, false, false, false, false);

        assertFalse(AttentionDecisionEngine.evaluate(story).surfaceNow);
    }

    @Test
    public void screenshotSavedStaysOutOfNow() {
        AttentionDecisionEngine.Candidate screenshot = c(
                5, "technical_event", "OPEN", "Screenshot saved",
                "Screenshot saved to device",
                .99, .00, .00, .10, .00, .10,
                0, 1, 1, true, false, false, false, false);

        assertFalse(AttentionDecisionEngine.evaluate(screenshot).surfaceNow);
    }

    @Test
    public void ordinaryMessageWithoutContextStaysOutOfNow() {
        AttentionDecisionEngine.Candidate message = c(
                6, "conversation_message", "OPEN", "Ahmed",
                "Okay, thanks",
                .92, .10, .10, .40, .00, .20,
                0, 1, 1, true, false, false, false, false);

        assertFalse(AttentionDecisionEngine.evaluate(message).surfaceNow);
    }

    @Test
    public void repeatedMissedCallsAloneAreNotAutomaticallyImportant() {
        AttentionDecisionEngine.Candidate calls = c(
                7, "call_event", "OPEN", "01000000000",
                "Repeated missed calls",
                .95, .40, .30, .40, .10, .50,
                0, 3, 3, true, false, true, false, false);

        assertFalse(AttentionDecisionEngine.evaluate(calls).surfaceNow);
    }

    @Test
    public void repeatedMissedCallsLinkedToDueCommitmentSurface() {
        AttentionDecisionEngine.Candidate calls = c(
                8, "call_event", "OPEN", "Ahmed",
                "Repeated missed calls linked to quotation commitment",
                .95, .75, .75, .90, .30, .75,
                NOW + 2 * HOUR, 3, 4, true, true, true, false, false);

        assertTrue(AttentionDecisionEngine.evaluate(calls).surfaceNow);
    }

    @Test
    public void approachingOpenCommitmentSurfaces() {
        AttentionDecisionEngine.Candidate commitment = c(
                9, "commitment", "WAITING", "Quotation",
                "Need to send revised quotation today",
                .94, .80, .85, .95, .30, .70,
                NOW + 3 * HOUR, 1, 2, true, true, true, false, false);

        assertTrue(AttentionDecisionEngine.evaluate(commitment).surfaceNow);
    }

    @Test
    public void resolvedSituationNeverSurfaces() {
        AttentionDecisionEngine.Candidate resolved = c(
                10, "commitment", "RESOLVED", "Quotation",
                "Quotation sent",
                .99, 1.0, 1.0, 1.0, 1.0, 1.0,
                NOW - HOUR, 4, 4, false, true, true, true, true);

        assertFalse(AttentionDecisionEngine.evaluate(resolved).surfaceNow);
    }

    @Test
    public void lowConfidenceInferenceStaysOutOfNow() {
        AttentionDecisionEngine.Candidate uncertain = c(
                11, "commitment", "OPEN", "Unknown person",
                "Maybe promised something",
                .55, .90, .90, .90, .90, .90,
                NOW + HOUR, 2, 2, true, true, true, true, false);

        assertFalse(AttentionDecisionEngine.evaluate(uncertain).surfaceNow);
    }

    @Test
    public void rankForNowIsSparseAndGlobal() {
        AttentionDecisionEngine.Candidate security = c(
                20, "security_alert", "OPEN", "Google security alert",
                "Compromised password; change it now",
                .95, .95, .95, .95, .95, .90,
                0, 1, 3, true, false, true, false, false);
        AttentionDecisionEngine.Candidate commitment = c(
                21, "commitment", "WAITING", "Quotation",
                "Need to send quotation today",
                .94, .80, .85, .95, .20, .70,
                NOW + 3 * HOUR, 1, 2, true, true, true, false, false);
        AttentionDecisionEngine.Candidate weather = c(
                22, "weather_event", "OPEN", "New Cairo", "24° and clear",
                .96, .10, .05, .20, .00, .10,
                0, 1, 1, true, false, false, false, false);
        AttentionDecisionEngine.Candidate story = c(
                23, "social_update", "OPEN", "Nabil", "Nabil has a new story",
                .95, .00, .00, .10, .00, .20,
                0, 1, 1, true, false, false, false, false);

        List<AttentionDecisionEngine.Decision> ranked = AttentionDecisionEngine.rankForNow(
                Arrays.asList(weather, story, commitment, security), 3);

        assertEquals(2, ranked.size());
        assertEquals(20L, ranked.get(0).candidate.situationId);
        assertEquals(21L, ranked.get(1).candidate.situationId);
    }
}
