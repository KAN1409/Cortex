package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure shadow-mode comparator. It never changes Now.
 *
 * The legacy side represents the current v69 projection answer for each situation.
 * The cognitive side first evaluates every world-state candidate, then applies global Now
 * capacity. Keeping those stages separate is critical diagnostics: "not eligible" and
 * "eligible but outside the top-K" are different failures and must never be conflated.
 */
public final class AttentionShadowComparator {
    public static final String VERSION = "attention_shadow_002";

    private AttentionShadowComparator() {}

    public static final class LegacyDecision {
        public final long situationId;
        public final boolean surfaceNow;
        public final String reason;

        public LegacyDecision(long situationId, boolean surfaceNow, String reason) {
            if (situationId <= 0) throw new IllegalArgumentException("situationId must be > 0");
            this.situationId = situationId;
            this.surfaceNow = surfaceNow;
            this.reason = n(reason);
        }
    }

    public enum Delta {
        AGREES_SURFACE,
        AGREES_SUPPRESS,
        NEW_SURFACES_LEGACY_MISSED,
        NEW_SUPPRESSES_LEGACY_NOISE
    }

    public static final class Comparison {
        public final long situationId;
        public final boolean legacySurface;
        /** Pure per-candidate decision before global capacity is applied. */
        public final boolean cognitiveEligible;
        /** Final cognitive Now selection after global ranking and maxItems. */
        public final boolean cognitiveSurface;
        public final int cognitiveRank;
        public final double cognitiveScore;
        public final Delta delta;
        public final String legacyReason;
        public final String cognitiveReason;

        Comparison(long situationId,
                   boolean legacySurface,
                   boolean cognitiveEligible,
                   boolean cognitiveSurface,
                   int cognitiveRank,
                   double cognitiveScore,
                   Delta delta,
                   String legacyReason,
                   String cognitiveReason) {
            this.situationId = situationId;
            this.legacySurface = legacySurface;
            this.cognitiveEligible = cognitiveEligible;
            this.cognitiveSurface = cognitiveSurface;
            this.cognitiveRank = cognitiveRank;
            this.cognitiveScore = cognitiveScore;
            this.delta = delta;
            this.legacyReason = n(legacyReason);
            this.cognitiveReason = n(cognitiveReason);
        }
    }

    public static final class Report {
        public final List<Comparison> comparisons;
        public final int agreements;
        public final int cognitiveRecoveries;
        public final int cognitiveNoiseSuppressions;
        public final int cognitiveEligibleCount;
        public final int cognitiveSelectedCount;
        public final int topKExcludedCount;

        Report(List<Comparison> comparisons,
               int agreements,
               int recoveries,
               int suppressions,
               int eligible,
               int selected,
               int topKExcluded) {
            this.comparisons = Collections.unmodifiableList(comparisons);
            this.agreements = agreements;
            this.cognitiveRecoveries = recoveries;
            this.cognitiveNoiseSuppressions = suppressions;
            this.cognitiveEligibleCount = eligible;
            this.cognitiveSelectedCount = selected;
            this.topKExcludedCount = topKExcluded;
        }
    }

    public static Report compare(List<LegacyDecision> legacy,
                                 List<AttentionDecisionEngine.Candidate> cognitiveCandidates,
                                 int maxNowItems) {
        Map<Long, LegacyDecision> oldBySituation = new HashMap<>();
        if (legacy != null) {
            for (LegacyDecision x : legacy) {
                if (x != null) oldBySituation.put(x.situationId, x);
            }
        }

        Map<Long, AttentionDecisionEngine.Decision> evaluatedBySituation = new HashMap<>();
        int eligibleCount = 0;
        if (cognitiveCandidates != null) {
            for (AttentionDecisionEngine.Candidate c : cognitiveCandidates) {
                if (c == null) continue;
                AttentionDecisionEngine.Decision d = AttentionDecisionEngine.evaluate(c);
                evaluatedBySituation.put(c.situationId, d);
                if (d.surfaceNow) eligibleCount++;
            }
        }

        List<AttentionDecisionEngine.Decision> ranked =
                AttentionDecisionEngine.rankForNow(cognitiveCandidates, maxNowItems);
        Map<Long, AttentionDecisionEngine.Decision> selectedBySituation = new HashMap<>();
        Map<Long, Integer> rankBySituation = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            AttentionDecisionEngine.Decision d = ranked.get(i);
            selectedBySituation.put(d.candidate.situationId, d);
            rankBySituation.put(d.candidate.situationId, i + 1);
        }

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(oldBySituation.keySet());
        allIds.addAll(evaluatedBySituation.keySet());

        List<Long> orderedIds = new ArrayList<>(allIds);
        Collections.sort(orderedIds);
        List<Comparison> out = new ArrayList<>();
        int agreements = 0, recoveries = 0, suppressions = 0;

        for (Long id : orderedIds) {
            LegacyDecision old = oldBySituation.get(id);
            AttentionDecisionEngine.Decision evaluated = evaluatedBySituation.get(id);
            AttentionDecisionEngine.Decision selected = selectedBySituation.get(id);
            boolean oldSurface = old != null && old.surfaceNow;
            boolean cognitiveEligible = evaluated != null && evaluated.surfaceNow;
            boolean cognitiveSurface = selected != null;

            Delta delta;
            if (oldSurface && cognitiveSurface) {
                delta = Delta.AGREES_SURFACE;
                agreements++;
            } else if (!oldSurface && !cognitiveSurface) {
                delta = Delta.AGREES_SUPPRESS;
                agreements++;
            } else if (!oldSurface) {
                delta = Delta.NEW_SURFACES_LEGACY_MISSED;
                recoveries++;
            } else {
                delta = Delta.NEW_SUPPRESSES_LEGACY_NOISE;
                suppressions++;
            }

            String cognitiveReason;
            if (evaluated == null) {
                cognitiveReason = "no cognitive candidate";
            } else if (cognitiveEligible && !cognitiveSurface) {
                cognitiveReason = "eligible but outside global Now capacity: " + evaluated.reason;
            } else {
                cognitiveReason = evaluated.reason;
            }

            out.add(new Comparison(
                    id,
                    oldSurface,
                    cognitiveEligible,
                    cognitiveSurface,
                    rankBySituation.containsKey(id) ? rankBySituation.get(id) : 0,
                    evaluated == null ? 0.0 : evaluated.score,
                    delta,
                    old == null ? "no legacy Now projection" : old.reason,
                    cognitiveReason));
        }

        int selectedCount = ranked.size();
        return new Report(out, agreements, recoveries, suppressions,
                eligibleCount, selectedCount, Math.max(0, eligibleCount - selectedCount));
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }
}
