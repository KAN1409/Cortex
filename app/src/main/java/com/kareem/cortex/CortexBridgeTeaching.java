package com.kareem.cortex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Aggregates comparison verdicts into one bounded teaching proposal and stages it safely. */
public final class CortexBridgeTeaching {
    private CortexBridgeTeaching() {}

    public static ChatGptBridgeCoordinator.DispatchResult dispatchSynthesis(Context context, BridgeMailTransport transport) {
        try {
            ChatGptBridgeStore store = new ChatGptBridgeStore(context.getApplicationContext());
            List<JSONObject> verdicts = store.listVerdicts();
            JSONArray comparisons = new JSONArray();
            for (JSONObject verdict : verdicts) {
                if (verdict == null) continue;
                String testId = verdict.optString("testId", "");
                if (!testId.startsWith("comparison-batch-")) continue;
                JSONObject payload = verdict.optJSONObject("payload");
                if (payload == null) continue;
                comparisons.put(new JSONObject()
                        .put("testId", testId)
                        .put("status", payload.optString("status", ""))
                        .put("severity", payload.optString("severity", ""))
                        .put("independentAnswer", payload.opt("independentAnswer"))
                        .put("cortexAssessment", payload.opt("cortexAssessment"))
                        .put("decisionAssessment", payload.opt("decisionAssessment"))
                        .put("mismatches", payload.optJSONArray("mismatches"))
                        .put("unsupportedClaims", payload.optJSONArray("unsupportedClaims"))
                        .put("missingExpectedFacts", payload.optJSONArray("missingExpectedFacts")));
            }

            JSONObject original = new JSONObject()
                    .put("kind", "TEACHING_SYNTHESIS")
                    .put("comparisonVerdictCount", comparisons.length())
                    .put("comparisonVerdicts", comparisons)
                    .put("instruction", "Synthesize one conservative bounded Policy Pack from the comparison evidence. Do not create facts, execute actions, or bypass CortexAttentionJudge. Return the exact bounded policy inside teachingCandidate.policy.");

            JSONObject cortex = new JSONObject()
                    .put("activePolicy", CortexPersonalPolicy.current(context))
                    .put("judgeVersion", CortexAttentionJudge.VERSION)
                    .put("candidateWillRunShadowComparison", true)
                    .put("candidateWillUseRollbackSafeCanary", true);

            JSONObject reference = new JSONObject()
                    .put("minimumComparisonVerdicts", 1)
                    .put("requiredPolicyFields", new JSONArray()
                            .put("version").put("ttlMs").put("attentionThreshold").put("maxNowItems")
                            .put("interruptionPenaltyScale").put("featureWeights").put("boosts").put("teacherNotes"))
                    .put("bounds", new JSONObject()
                            .put("ttlMs", "60000..2592000000")
                            .put("attentionThreshold", "0..1")
                            .put("maxNowItems", "1..12")
                            .put("interruptionPenaltyScale", "0..0.55")
                            .put("featureWeights", "0..0.45")
                            .put("boostWeight", "-1..1"))
                    .put("safety", new JSONObject()
                            .put("canonicalWritesForbidden", true)
                            .put("executionForbidden", true)
                            .put("shadowBeforePromotion", true)
                            .put("rollbackSafeCanary", true));

            JSONObject rules = ChatGptBridgeProtocol.defaultJudgingRules()
                    .put("teachingDisabled", false)
                    .put("teachingMode", "PROPOSAL_ONLY")
                    .put("requireSingleSynthesisPolicy", true);

            return new ChatGptBridgeCoordinator(context, transport).dispatchTestWithRules(
                    "teaching-synthesis-" + System.currentTimeMillis(),
                    "teaching-synthesis",
                    original,
                    cortex,
                    reference,
                    rules);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Explicit user action only. Existing teacher validates, shadows, and canary-promotes. */
    public static CortexChatGptAppTeacher.ImportResult stageLatestCandidate(Context context) {
        try {
            List<JSONObject> verdicts = new ChatGptBridgeStore(context.getApplicationContext()).listVerdicts();
            for (int i = verdicts.size() - 1; i >= 0; i--) {
                JSONObject verdict = verdicts.get(i);
                if (!"teaching-synthesis".equals(verdict.optString("testId", ""))) continue;
                JSONObject payload = verdict.optJSONObject("payload");
                JSONObject candidate = payload == null ? null : payload.optJSONObject("teachingCandidate");
                if (candidate == null) return new CortexChatGptAppTeacher.ImportResult(false, "", "Teaching synthesis has no teachingCandidate");
                JSONObject policy = candidate.optJSONObject("policy");
                if (policy == null) policy = candidate;
                return CortexChatGptAppTeacher.importText(context, policy.toString());
            }
            return new CortexChatGptAppTeacher.ImportResult(false, "", "No teaching synthesis verdict available yet");
        } catch (Throwable t) {
            return new CortexChatGptAppTeacher.ImportResult(false, "", t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
        }
    }
}
