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
 * The legacy side represents the current v69 projection answer for each semantic event.
 * The cognitive side represents globally ranked situation-level decisions. The result is
 * intentionally explainable so real-device traces can be audited before cutover.
 */
public final class AttentionShadowComparator {
    public static final String VERSION = "attention_shadow_001";

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
        public final boolean cognitiveSurface;
        public final int cognitiveRank;
        public final double cognitiveScore;
        public final Delta delta;
        public final String legacyReason;
        public final String cognitiveReason;

        Comparison(long situationId,
                   boolean legacySurface,
                   boolean cognitiveSurface,
                   int cognitiveRank,
                   double cognitiveScore,
                   Delta delta,
                   String legacyReason,
                   String cognitiveReason) {
            this.situationId = situationId;
            this.legacySurface = legacySurface;
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

        Report(List<Comparison> comparisons, int agreements, int recoveries, int suppressions) {
            this.comparisons = Collections.unmodifiableList(comparisons);
            this.agreements = agreements;
            this.cognitiveRecoveries = recoveries;
            this.cognitiveNoiseSuppressions = suppressions;
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

        List<AttentionDecisionEngine.Decision> ranked =
                AttentionDecisionEngine.rankForNow(cognitiveCandidates, maxNowItems);
        Map<Long, AttentionDecisionEngine.Decision> newBySituation = new HashMap<>();
        Map<Long, Integer> rankBySituation = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            AttentionDecisionEngine.Decision d = ranked.get(i);
            newBySituation.put(d.candidate.situationId, d);
            rankBySituation.put(d.candidate.situationId, i + 1);
        }

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(oldBySituation.keySet());
        if (cognitiveCandidates != null) {
            for (AttentionDecisionEngine.Candidate c : cognitiveCandidates) {
                if (c != null) allIds.add(c.situationId);
            }
        }

        List<Long> orderedIds = new ArrayList<>(allIds);
        Collections.sort(orderedIds);
        List<Comparison> out = new ArrayList<>();
        int agreements = 0, recoveries = 0, suppressions = 0;

        for (Long id : orderedIds) {
            LegacyDecision old = oldBySituation.get(id);
            AttentionDecisionEngine.Decision fresh = newBySituation.get(id);
            boolean oldSurface = old != null && old.surfaceNow;
            boolean newSurface = fresh != null;
            Delta delta;
            if (oldSurface && newSurface) {
                delta = Delta.AGREES_SURFACE;
                agreements++;
            } else if (!oldSurface && !newSurface) {
                delta = Delta.AGREES_SUPPRESS;
                agreements++;
            } else if (!oldSurface) {
                delta = Delta.NEW_SURFACES_LEGACY_MISSED;
                recoveries++;
            } else {
                delta = Delta.NEW_SUPPRESSES_LEGACY_NOISE;
                suppressions++;
            }
            out.add(new Comparison(
                    id,
                    oldSurface,
                    newSurface,
                    rankBySituation.containsKey(id) ? rankBySituation.get(id) : 0,
                    fresh == null ? 0.0 : fresh.score,
                    delta,
                    old == null ? "no legacy Now projection" : old.reason,
                    fresh == null ? "not selected by cognitive ranker" : fresh.reason));
        }

        return new Report(out, agreements, recoveries, suppressions);
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }
}
