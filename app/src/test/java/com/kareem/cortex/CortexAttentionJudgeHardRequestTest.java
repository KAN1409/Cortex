package com.kareem.cortex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class CortexAttentionJudgeHardRequestTest {

    @Test public void staleExplicitActionableRequestRemainsHardAfterGroundingGates() throws Exception {
        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                null,
                staleCandidate(true),
                new CortexAttentionJudge.RuntimeContext(0.50, 0.90),
                strictPolicy());
        assertTrue(j.surfaceNow);
        assertTrue(j.reason.contains("explicit personally relevant request"));
    }

    @Test public void staleNonRequestStillUsesSoftScoringAndCanDefer() throws Exception {
        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                null,
                staleCandidate(false),
                new CortexAttentionJudge.RuntimeContext(0.50, 0.90),
                strictPolicy());
        assertFalse(j.surfaceNow);
    }

    @Test public void lowRelevanceAmbientRequestDoesNotBecomeHardInterruption() throws Exception {
        long now = 1_800_000_000_000L;
        AttentionDecisionEngine.Candidate c = new AttentionDecisionEngine.Candidate(
                203L, "SOCIAL", "OPEN", "Ambient social request",
                "Grounded but weakly personal ambient social activity",
                0.95, 0.72, 0.90, 0.30, 0.02, 0.50,
                now + 3L * 60L * 60L * 1000L, now, now, 0, 2,
                true, false, false, true, false);

        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                null,
                c,
                new CortexAttentionJudge.RuntimeContext(0.20, 0.95),
                strictPolicy());

        assertFalse(j.surfaceNow);
    }

    @Test public void rawTechnicalEvidenceCannotBecomeAttentionFromFlagsAlone() throws Exception {
        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                null,
                actionableCandidate("TECHNICAL_EVENT", 204L),
                CortexAttentionJudge.RuntimeContext.neutral(),
                strictPolicy());

        assertFalse(j.surfaceNow);
        assertTrue(j.reason.contains("technical evidence remains below the attention layer"));
    }

    // Judge-level eligibility only: this does not exercise production semantic translation.
    @Test public void preconstructedUserFacingBlockerCanUseHardRequestBoundary() throws Exception {
        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                null,
                actionableCandidate("USER_ACTION_BLOCKER", 205L),
                CortexAttentionJudge.RuntimeContext.neutral(),
                strictPolicy());

        assertTrue(j.surfaceNow);
        assertTrue(j.reason.contains("explicit personally relevant request"));
    }

    @Test public void hardRequestRespectsExactThresholdsAcrossEvidenceAges() throws Exception {
        long now = 1_800_000_000_000L;
        double[] actions = { Math.nextDown(.80), .80, Math.nextUp(.80) };
        double[] relevance = { Math.nextDown(.45), .45, Math.nextUp(.45) };
        for (long ageDays : new long[] { 0, 5, 30 }) {
            for (double action : actions) {
                for (double personal : relevance) {
                    for (boolean explicit : new boolean[] { false, true }) {
                        AttentionDecisionEngine.Candidate c = new AttentionDecisionEngine.Candidate(
                                206L, "DIRECT_MESSAGE", "OPEN", "Action requested",
                                "Grounded request remains unresolved",
                                .95, .10, action, personal, .02, .01,
                                0L, now, now - ageDays * 86_400_000L, 0, 1,
                                true, false, false, explicit, false);
                        CortexAttentionJudge.Judgment j = CortexAttentionJudge.evaluateWithPolicy(
                                null, c, new CortexAttentionJudge.RuntimeContext(.20, .95), strictPolicy());
                        boolean expected = explicit && action >= .80 && personal >= .45;
                        assertTrue("ageDays=" + ageDays + " action=" + action + " relevance="
                                + personal + " explicit=" + explicit + " score=" + j.score
                                + " reason=" + j.reason, expected == j.surfaceNow);
                    }
                }
            }
        }
    }

    private static AttentionDecisionEngine.Candidate actionableCandidate(String type, long id) {
        long now = 1_800_000_000_000L;
        return new AttentionDecisionEngine.Candidate(
                id, type, "OPEN", "Verified action blocker",
                "Grounded user-facing action is required",
                0.95, 0.72, 0.90, 0.90, 0.20, 0.50,
                0L, now, now, 0, 2,
                true, true, false, true, false);
    }

    private static JSONObject strictPolicy() throws Exception {
        return new JSONObject()
                .put("version", "test-hard-explicit-request")
                .put("attentionThreshold", 0.95)
                .put("interruptionPenaltyScale", 0.55)
                .put("featureWeights", new JSONObject())
                .put("boosts", new JSONArray());
    }

    private static AttentionDecisionEngine.Candidate staleCandidate(boolean explicitRequest) {
        long now = 1_800_000_000_000L;
        long staleLastSeen = now - 5L * 24L * 60L * 60L * 1000L;
        return new AttentionDecisionEngine.Candidate(
                202L, "DIRECT_MESSAGE", "OPEN", "Action requested",
                "Grounded action request that remains unresolved",
                0.95, 0.72, 0.90, 0.90, 0.20, 0.50,
                0L, now, staleLastSeen, 0, 2,
                true, false, false, explicitRequest, false);
    }
}
