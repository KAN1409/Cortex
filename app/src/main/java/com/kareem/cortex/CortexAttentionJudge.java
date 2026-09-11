package com.kareem.cortex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Final attention owner for Cortex.
 *
 * Upstream systems may create candidates and estimate semantic features. Only this class decides
 * whether a candidate deserves user attention. ChatGPT policy can tune bounded weights/thresholds,
 * but cannot edit evidence, knowledge, world state, or execute an action.
 */
public final class CortexAttentionJudge {
    public static final String VERSION = "cortex_attention_judge_001";

    public static final class RuntimeContext {
        public final double contextMatch;
        public final double interruptionCost;

        public RuntimeContext(double contextMatch, double interruptionCost) {
            this.contextMatch = clamp01(contextMatch);
            this.interruptionCost = clamp01(interruptionCost);
        }

        public static RuntimeContext neutral() { return new RuntimeContext(0.50, 0.25); }
    }

    public static final class Judgment {
        public final AttentionDecisionEngine.Candidate candidate;
        public final boolean surfaceNow;
        public final double score;
        public final double threshold;
        public final double interruptionCost;
        public final String reason;
        public final String policyVersion;

        Judgment(AttentionDecisionEngine.Candidate candidate, boolean surfaceNow, double score,
                 double threshold, double interruptionCost, String reason, String policyVersion) {
            this.candidate = candidate;
            this.surfaceNow = surfaceNow;
            this.score = score;
            this.threshold = threshold;
            this.interruptionCost = interruptionCost;
            this.reason = reason == null ? "" : reason;
            this.policyVersion = policyVersion == null ? "" : policyVersion;
        }
    }

    private CortexAttentionJudge() {}

    public static Judgment evaluate(Context context, AttentionDecisionEngine.Candidate c, RuntimeContext runtime) {
        if (c == null) throw new IllegalArgumentException("candidate == null");
        RuntimeContext rt = runtime == null ? RuntimeContext.neutral() : runtime;
        JSONObject policy = CortexPersonalPolicy.current(context);
        String policyVersion = policy.optString("version", "local");
        double threshold = clamp01(policy.optDouble("attentionThreshold", 0.72));

        AttentionDecisionEngine.Decision structural = AttentionDecisionEngine.evaluate(c);
        String structuralReason = structural.reason == null ? "" : structural.reason.toLowerCase(Locale.ROOT);

        if (!c.unresolved || structuralReason.contains("resolved situation")) {
            return new Judgment(c, false, 0, threshold, rt.interruptionCost,
                    "resolved state is never an interruption", policyVersion);
        }
        if (c.confidence < 0.70 || structuralReason.contains("low-confidence")) {
            return new Judgment(c, false, 0, threshold, rt.interruptionCost,
                    "insufficient grounded confidence", policyVersion);
        }
        if (structuralReason.contains("technical evidence")) {
            return new Judgment(c, false, 0, threshold, rt.interruptionCost,
                    "technical evidence remains below the attention layer", policyVersion);
        }

        double freshness = clamp01(c.freshness);
        double urgency = c.urgency * freshness;
        double actionability = c.actionability * (c.linkedOpenCommitment ? Math.max(.80, freshness) : Math.max(.35, freshness));
        double risk = c.risk * Math.max(.35, freshness);
        double novelty = c.novelty * freshness;
        double deadline = deadlineScore(c.deadlineAt, c.nowAt);

        double score = 0;
        score += urgency * weight(policy, "urgency", .19);
        score += actionability * weight(policy, "actionability", .21);
        score += c.personalRelevance * weight(policy, "personalRelevance", .18);
        score += risk * weight(policy, "risk", .16);
        score += novelty * weight(policy, "novelty", .07);
        score += deadline * weight(policy, "deadline", .09);
        score += rt.contextMatch * weight(policy, "contextMatch", .10);

        if (c.explicitRequest) score += .12 * Math.max(.40, freshness);
        if (c.linkedOpenCommitment) score += .14;
        if (c.materialChange) score += .07 * freshness;
        if (c.severeContextImpact) score += .14;
        if (c.evidenceCount >= 2) score += Math.min(.05, (c.evidenceCount - 1) * .01) * Math.max(.5, freshness);

        String text = normalize(c.type + " " + c.subject + " " + c.summary);
        score += textBoost(policy, text);

        boolean hardRisk = risk >= .85 && urgency >= .60;
        boolean hardRequest = c.explicitRequest && actionability >= .65 && c.personalRelevance >= .45;
        boolean dueCommitment = c.linkedOpenCommitment && deadline >= .60;
        boolean contextualEmergency = c.severeContextImpact && (risk >= .60 || urgency >= .60);
        boolean hard = hardRisk || hardRequest || dueCommitment || contextualEmergency;

        double penaltyScale = bounded(policy.optDouble("interruptionPenaltyScale", .24), 0, .55);
        score -= rt.interruptionCost * penaltyScale * (hard ? .15 : 1.0);
        score = bounded(score, 0, 1.5);

        boolean surface = hard || score >= threshold;
        String reason;
        if (hardRisk) reason = "high-risk situation overrides ordinary interruption cost";
        else if (hardRequest) reason = "explicit personally relevant request";
        else if (dueCommitment) reason = "open commitment is due soon";
        else if (contextualEmergency) reason = "current context makes the situation urgent";
        else if (surface) reason = "expected attention value exceeds interruption cost";
        else if (rt.interruptionCost >= .70) reason = "useful but deferred because interruption cost is high";
        else reason = "attention value is below the current personal threshold";

        return new Judgment(c, surface, score, threshold, rt.interruptionCost, reason, policyVersion);
    }

    public static List<Judgment> rankForNow(Context context, List<AttentionDecisionEngine.Candidate> candidates,
                                             RuntimeContext runtime, int maxItems) {
        if (candidates == null || candidates.isEmpty() || maxItems <= 0) return Collections.emptyList();
        ArrayList<Judgment> out = new ArrayList<>();
        for (AttentionDecisionEngine.Candidate c : candidates) {
            if (c == null) continue;
            Judgment j = evaluate(context, c, runtime);
            if (j.surfaceNow) out.add(j);
        }
        Collections.sort(out, new Comparator<Judgment>() {
            @Override public int compare(Judgment a, Judgment b) {
                int s = Double.compare(b.score, a.score);
                if (s != 0) return s;
                int r = Double.compare(b.candidate.risk, a.candidate.risk);
                if (r != 0) return r;
                return Long.compare(b.candidate.lastSeenAt, a.candidate.lastSeenAt);
            }
        });
        if (out.size() > maxItems) return Collections.unmodifiableList(new ArrayList<>(out.subList(0, maxItems)));
        return Collections.unmodifiableList(out);
    }

    private static double weight(JSONObject policy, String key, double fallback) {
        JSONObject weights = policy.optJSONObject("featureWeights");
        if (weights == null) return fallback;
        return bounded(weights.optDouble(key, fallback), 0, .45);
    }

    private static double textBoost(JSONObject policy, String haystack) {
        JSONArray boosts = policy.optJSONArray("boosts");
        if (boosts == null || haystack.isEmpty()) return 0;
        double delta = 0;
        for (int i = 0; i < boosts.length(); i++) {
            JSONObject boost = boosts.optJSONObject(i);
            if (boost == null) continue;
            String match = normalize(boost.optString("match", ""));
            if (match.length() < 2 || !haystack.contains(match)) continue;
            delta += bounded(boost.optDouble("weight", 0), -.40, .40);
        }
        return bounded(delta, -.55, .55);
    }

    private static double deadlineScore(long deadlineAt, long nowAt) {
        if (deadlineAt <= 0 || nowAt <= 0) return 0;
        long delta = deadlineAt - nowAt;
        if (delta <= 0) return 1;
        long hour = 60L * 60L * 1000L;
        if (delta <= 2L * hour) return 1;
        if (delta <= 6L * hour) return .85;
        if (delta <= 24L * hour) return .70;
        if (delta <= 3L * 24L * hour) return .45;
        if (delta <= 7L * 24L * hour) return .20;
        return 0;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
    private static double clamp01(double x) { return bounded(x, 0, 1); }
    private static double bounded(double x, double min, double max) {
        if (Double.isNaN(x)) return min;
        return Math.max(min, Math.min(max, x));
    }
}
