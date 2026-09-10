package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Experimental v70 attention layer.
 *
 * Capture is evidence. This engine receives already-correlated situation candidates and
 * ranks them globally for Now. It deliberately does not read or mutate UI state.
 *
 * v70.18 makes time an explicit attention input. Historical importance is not the same as
 * current interruption value: stale urgency/actionability/risk/novelty decay unless a durable
 * open commitment or approaching deadline independently keeps the situation actionable.
 */
public final class AttentionDecisionEngine {
    public static final String VERSION = "attention_decision_engine_003";

    private AttentionDecisionEngine() {}

    public static final class Candidate {
        public final long situationId;
        public final String type;
        public final String state;
        public final String subject;
        public final String summary;
        public final double confidence;
        public final double urgency;
        public final double actionability;
        public final double personalRelevance;
        public final double risk;
        public final double novelty;
        public final long deadlineAt;
        public final long nowAt;
        public final long lastSeenAt;
        public final double freshness;
        public final int repeatedCount;
        public final int evidenceCount;
        public final boolean unresolved;
        public final boolean linkedOpenCommitment;
        public final boolean materialChange;
        public final boolean explicitRequest;
        public final boolean severeContextImpact;

        /** Compatibility constructor: callers without event-time data are treated as current. */
        public Candidate(
                long situationId,
                String type,
                String state,
                String subject,
                String summary,
                double confidence,
                double urgency,
                double actionability,
                double personalRelevance,
                double risk,
                double novelty,
                long deadlineAt,
                long nowAt,
                int repeatedCount,
                int evidenceCount,
                boolean unresolved,
                boolean linkedOpenCommitment,
                boolean materialChange,
                boolean explicitRequest,
                boolean severeContextImpact) {
            this(situationId, type, state, subject, summary, confidence, urgency, actionability,
                    personalRelevance, risk, novelty, deadlineAt, nowAt, nowAt, repeatedCount,
                    evidenceCount, unresolved, linkedOpenCommitment, materialChange,
                    explicitRequest, severeContextImpact);
        }

        /** Full constructor used by the persisted world-state path. */
        public Candidate(
                long situationId,
                String type,
                String state,
                String subject,
                String summary,
                double confidence,
                double urgency,
                double actionability,
                double personalRelevance,
                double risk,
                double novelty,
                long deadlineAt,
                long nowAt,
                long lastSeenAt,
                int repeatedCount,
                int evidenceCount,
                boolean unresolved,
                boolean linkedOpenCommitment,
                boolean materialChange,
                boolean explicitRequest,
                boolean severeContextImpact) {
            this.situationId = situationId;
            this.type = n(type);
            this.state = n(state);
            this.subject = n(subject);
            this.summary = n(summary);
            this.confidence = clamp01(confidence);
            this.urgency = clamp01(urgency);
            this.actionability = clamp01(actionability);
            this.personalRelevance = clamp01(personalRelevance);
            this.risk = clamp01(risk);
            this.novelty = clamp01(novelty);
            this.deadlineAt = deadlineAt;
            this.nowAt = nowAt;
            this.lastSeenAt = lastSeenAt;
            this.freshness = freshnessScore(lastSeenAt, nowAt);
            this.repeatedCount = Math.max(0, repeatedCount);
            this.evidenceCount = Math.max(1, evidenceCount);
            this.unresolved = unresolved;
            this.linkedOpenCommitment = linkedOpenCommitment;
            this.materialChange = materialChange;
            this.explicitRequest = explicitRequest;
            this.severeContextImpact = severeContextImpact;
        }
    }

    public static final class Decision {
        public final Candidate candidate;
        public final boolean surfaceNow;
        public final double score;
        public final String reason;

        Decision(Candidate candidate, boolean surfaceNow, double score, String reason) {
            this.candidate = candidate;
            this.surfaceNow = surfaceNow;
            this.score = score;
            this.reason = n(reason);
        }
    }

    /** Evaluates one already-correlated situation. This is not the final ordering step. */
    public static Decision evaluate(Candidate c) {
        if (c == null) throw new IllegalArgumentException("candidate == null");

        if (!c.unresolved || isResolved(c.state)) {
            return new Decision(c, false, 0.0, "resolved situation stays out of Now");
        }
        if (c.confidence < 0.70) {
            return new Decision(c, false, 0.0, "low-confidence inference stays in Capture");
        }

        final String all = norm(c.type + " " + c.subject + " " + c.summary);
        final double effectiveUrgency = effectiveUrgency(c);
        final double effectiveActionability = effectiveActionability(c);
        final double effectiveRisk = effectiveRisk(c, all);
        final double effectiveNovelty = c.novelty * c.freshness;

        final boolean routineWeather = isRoutineWeather(c.type, all) && !c.severeContextImpact;
        final boolean social = isSocial(c.type, all);
        final boolean technical = isTechnical(c.type, all);
        final boolean ordinaryMessage = isOrdinaryMessage(c.type, all)
                && !c.explicitRequest
                && !c.linkedOpenCommitment
                && effectiveActionability < 0.55
                && effectiveRisk < 0.55;

        if (technical) return new Decision(c, false, 0.0, "technical evidence is Capture-only");
        if (social && !c.linkedOpenCommitment && effectiveRisk < 0.70 && effectiveActionability < 0.70) {
            return new Decision(c, false, 0.0, "routine social activity has low attention value");
        }
        if (routineWeather) return new Decision(c, false, 0.0, "routine weather has no current contextual impact");
        if (ordinaryMessage) return new Decision(c, false, 0.0, "ordinary message has no obligation or material context");

        double score = 0.0;
        score += effectiveUrgency * 0.22;
        score += effectiveActionability * 0.24;
        score += c.personalRelevance * 0.18;
        score += effectiveRisk * 0.18;
        score += effectiveNovelty * 0.08;
        score += deadlineScore(c.deadlineAt, c.nowAt) * 0.10;

        if (c.explicitRequest) score += 0.18 * Math.max(0.30, c.freshness);
        if (c.linkedOpenCommitment) score += 0.16;
        if (c.materialChange) score += 0.08 * c.freshness;
        if (c.severeContextImpact) score += 0.14 * Math.max(0.50, c.freshness);

        // Repetition is supporting evidence, not meaning by itself, and old repetition fades.
        double evidenceFreshness = Math.max(0.50, c.freshness);
        if (c.repeatedCount >= 2) score += 0.04 * evidenceFreshness;
        if (c.repeatedCount >= 2 && c.linkedOpenCommitment) score += 0.08;
        if (c.evidenceCount >= 2) score += Math.min(0.05, (c.evidenceCount - 1) * 0.01) * evidenceFreshness;

        final boolean obviousSecurity = isSecurity(all) && effectiveRisk >= 0.65 && effectiveActionability >= 0.55;
        final boolean hardAction = c.explicitRequest && effectiveActionability >= 0.55;
        final boolean dueCommitment = c.linkedOpenCommitment && deadlineScore(c.deadlineAt, c.nowAt) >= 0.60;
        final boolean contextualEmergency = c.severeContextImpact && (effectiveUrgency >= 0.60 || effectiveRisk >= 0.60);

        boolean surface = obviousSecurity || hardAction || dueCommitment || contextualEmergency || score >= 0.68;

        String reason;
        if (obviousSecurity) reason = "high-risk actionable security situation";
        else if (hardAction) reason = "explicit unresolved request requires action";
        else if (dueCommitment) reason = "open commitment is approaching its deadline";
        else if (contextualEmergency) reason = "contextual impact makes the situation urgent";
        else if (surface) reason = "globally relevant unresolved situation";
        else if (c.freshness < 0.50 && (c.urgency >= 0.55 || c.actionability >= 0.55 || c.risk >= 0.55)) {
            reason = "stale situation attention value decayed without fresh evidence or a due commitment";
        } else reason = "insufficient current attention value";

        return new Decision(c, surface, score, reason);
    }

    /** Returns a sparse globally ranked Now list after per-candidate eligibility. */
    public static List<Decision> rankForNow(List<Candidate> candidates, int maxItems) {
        if (candidates == null || candidates.isEmpty() || maxItems <= 0) return Collections.emptyList();

        List<Decision> eligible = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate == null) continue;
            Decision decision = evaluate(candidate);
            if (decision.surfaceNow) eligible.add(decision);
        }

        Collections.sort(eligible, new Comparator<Decision>() {
            @Override
            public int compare(Decision a, Decision b) {
                int byClass = Integer.compare(attentionClass(b), attentionClass(a));
                if (byClass != 0) return byClass;
                int byScore = Double.compare(b.score, a.score);
                if (byScore != 0) return byScore;
                int byRisk = Double.compare(effectiveRisk(b.candidate, norm(b.candidate.type + " " + b.candidate.subject + " " + b.candidate.summary)),
                        effectiveRisk(a.candidate, norm(a.candidate.type + " " + a.candidate.subject + " " + a.candidate.summary)));
                if (byRisk != 0) return byRisk;
                int byFreshness = Double.compare(b.candidate.freshness, a.candidate.freshness);
                if (byFreshness != 0) return byFreshness;
                int byUrgency = Double.compare(effectiveUrgency(b.candidate), effectiveUrgency(a.candidate));
                if (byUrgency != 0) return byUrgency;
                return Long.compare(a.candidate.situationId, b.candidate.situationId);
            }
        });

        if (eligible.size() <= maxItems) return Collections.unmodifiableList(eligible);
        return Collections.unmodifiableList(new ArrayList<>(eligible.subList(0, maxItems)));
    }

    /** Broad semantic priority classes; intentionally not tied to app/package names. */
    private static int attentionClass(Decision d) {
        Candidate c = d.candidate;
        String all = norm(c.type + " " + c.subject + " " + c.summary);
        double risk = effectiveRisk(c, all);
        double urgency = effectiveUrgency(c);
        double actionability = effectiveActionability(c);
        if (risk >= 0.80 && urgency >= 0.70 && actionability >= 0.60) return 4;
        if (c.severeContextImpact && (risk >= 0.60 || urgency >= 0.75)) return 4;
        if (c.explicitRequest && actionability >= 0.65) return 3;
        if (c.linkedOpenCommitment && deadlineScore(c.deadlineAt, c.nowAt) >= 0.60) return 2;
        return 1;
    }

    static double freshnessScore(long lastSeenAt, long nowAt) {
        if (lastSeenAt <= 0 || nowAt <= 0 || lastSeenAt >= nowAt) return 1.0;
        long age = nowAt - lastSeenAt;
        long hour = 60L * 60L * 1000L;
        long day = 24L * hour;
        if (age <= 2L * hour) return 1.0;
        if (age <= 8L * hour) return 0.92;
        if (age <= day) return 0.80;
        if (age <= 3L * day) return 0.60;
        if (age <= 7L * day) return 0.40;
        if (age <= 14L * day) return 0.25;
        return 0.15;
    }

    private static double effectiveUrgency(Candidate c) {
        return c.urgency * c.freshness;
    }

    private static double effectiveActionability(Candidate c) {
        double retention = c.linkedOpenCommitment ? Math.max(0.80, c.freshness) : Math.max(0.35, c.freshness);
        return c.actionability * retention;
    }

    private static double effectiveRisk(Candidate c, String all) {
        double floor = isSecurity(all) ? 0.55 : 0.35;
        return c.risk * Math.max(floor, c.freshness);
    }

    private static double deadlineScore(long deadlineAt, long nowAt) {
        if (deadlineAt <= 0 || nowAt <= 0) return 0.0;
        long delta = deadlineAt - nowAt;
        if (delta <= 0) return 1.0;
        long hour = 60L * 60L * 1000L;
        if (delta <= 2L * hour) return 1.0;
        if (delta <= 6L * hour) return 0.85;
        if (delta <= 24L * hour) return 0.70;
        if (delta <= 3L * 24L * hour) return 0.45;
        if (delta <= 7L * 24L * hour) return 0.20;
        return 0.0;
    }

    private static boolean isResolved(String state) {
        String s = norm(state);
        return s.equals("resolved") || s.equals("closed") || s.equals("completed") || s.equals("dismissed");
    }

    private static boolean isSecurity(String text) {
        return any(text, "security alert", "critical security", "password", "compromised", "breach", "unauthorized login", "تنبيه أمني", "كلمة مرور", "تسريب");
    }

    private static boolean isRoutineWeather(String type, String text) {
        String s = norm(type + " " + text);
        return s.contains("weather") || s.contains("temperature") || s.matches(".*\\b-?\\d{1,2}°.*");
    }

    private static boolean isSocial(String type, String text) {
        String s = norm(type + " " + text);
        return any(s, "story", "liked", "reaction", "followed", "follows you", "added you", "new story", "instagram", "snapchat");
    }

    private static boolean isTechnical(String type, String text) {
        String s = norm(type + " " + text);
        return any(s, "screenshot saved", "mediaongoingactivity", "equalizer", "technical_event", "service_state", "foreground service");
    }

    private static boolean isOrdinaryMessage(String type, String text) {
        String s = norm(type + " " + text);
        return s.contains("conversation_message") || s.contains("ordinary message") || s.contains("message_event");
    }

    private static boolean any(String s, String... xs) {
        for (String x : xs) if (s.contains(x)) return true;
        return false;
    }

    private static String norm(String s) {
        return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
