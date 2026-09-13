package com.kareem.cortex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class CortexAttentionJudgeResolutionTest {

    @Test public void unresolvedHighValueCandidateIsNotSuppressedByReasonSubstring() throws Exception {
        AttentionDecisionEngine.Candidate c = candidate("open", true);

        AttentionDecisionEngine.Decision structural = AttentionDecisionEngine.evaluate(c);
        assertTrue(structural.reason.contains("unresolved situation"));

        CortexAttentionJudge.Judgment judgment = CortexAttentionJudge.evaluateWithPolicy(
                null,
                c,
                new CortexAttentionJudge.RuntimeContext(0.50, 0.10),
                permissivePolicy());

        assertTrue("unresolved candidate must remain eligible", judgment.surfaceNow);
        assertFalse("reason text must not override structured state",
                judgment.reason.contains("resolved state"));
    }

    @Test public void literalUnresolvedStateIsNotResolved() throws Exception {
        AttentionDecisionEngine.Candidate c = candidate("unresolved", true);
        CortexAttentionJudge.Judgment judgment = CortexAttentionJudge.evaluateWithPolicy(
                null, c, CortexAttentionJudge.RuntimeContext.neutral(), permissivePolicy());
        assertTrue(judgment.surfaceNow);
    }

    @Test public void explicitResolvedStateIsSuppressed() throws Exception {
        AttentionDecisionEngine.Candidate c = candidate("resolved", true);
        CortexAttentionJudge.Judgment judgment = CortexAttentionJudge.evaluateWithPolicy(
                null, c, CortexAttentionJudge.RuntimeContext.neutral(), permissivePolicy());
        assertFalse(judgment.surfaceNow);
        assertTrue(judgment.reason.contains("resolved state"));
    }

    @Test public void unresolvedFlagFalseIsSuppressedEvenWhenStateLooksOpen() throws Exception {
        AttentionDecisionEngine.Candidate c = candidate("open", false);
        CortexAttentionJudge.Judgment judgment = CortexAttentionJudge.evaluateWithPolicy(
                null, c, CortexAttentionJudge.RuntimeContext.neutral(), permissivePolicy());
        assertFalse(judgment.surfaceNow);
    }

    private static JSONObject permissivePolicy() throws Exception {
        return new JSONObject()
                .put("version", "test-structured-resolution")
                .put("attentionThreshold", 0.50)
                .put("interruptionPenaltyScale", 0.10)
                .put("featureWeights", new JSONObject())
                .put("boosts", new org.json.JSONArray());
    }

    private static AttentionDecisionEngine.Candidate candidate(String state, boolean unresolved) {
        long now = 1_800_000_000_000L;
        return new AttentionDecisionEngine.Candidate(
                101L,
                "PROJECT_STATE",
                state,
                "Material project change",
                "Important actionable update with fresh grounded evidence",
                0.98,
                0.95,
                0.95,
                0.95,
                0.35,
                0.90,
                0L,
                now,
                now,
                0,
                2,
                unresolved,
                false,
                true,
                false,
                false);
    }
}
