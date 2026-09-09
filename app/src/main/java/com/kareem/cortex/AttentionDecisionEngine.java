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
 */
public final class AttentionDecisionEngine {
    public static final String VERSION = "attention_decision_engine_002";

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
        public final int repeatedCount;
        public final int evidenceCount;
        public final boolean unresolved;
        public final boolean linkedOpenCommitment;
        public final boolean materialChange;
        public final boolean explicitRequest;
        public final boolean severeContextImpact;

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
        final boolean routineWeather = isRoutineWeather(c.type, all) && !c.severeContextImpact;
        final boolean social = isSocial(c.type, all);
        final boolean technical = isTechnical(c.type, all);
        final boolean ordinaryMessage = isOrdinaryMessage(c.type, all)
                && !c.explicitRequest
                && !c.linkedOpenCommitment
                && c.actionability < 0.55
                && c.risk < 0.55;

        if (technical) return new Decision(c, false, 0.0, "technical evidence is Capture-only");
        if (social && !c.linkedOpenCommitment && c.risk < 0.70 && c.actionability < 0.70) {
            return new Decision(c, false, 0.0, "routine social activity has low attention value");
        }
        if (routineWeather) return new Decision(c, false, 0.0, "routine weather has no current contextual impact");
        if (ordinaryMessage) return new Decision(c, false, 0.0, "ordinary message has no obligation or material context");

        double score = 0.0;
        score += c.urgency * 0.22;
        score += c.actionability * 0.24;
        score += c.personalRelevance * 0.18;
        score += c.risk * 0.18;
        score += c.novelty * 0.08;
        score += deadlineScore(c.deadlineAt, c.nowAt) * 0.10;

        if (c.explicitRequest) score += 0.18;
        if (c.linkedOpenCommitment) score += 0.16;
        if (c.materialChange) score += 0.08;
        if (c.severeContextImpact) score += 0.14;

        // Repetition is supporting evidence, not meaning by itself.
        if (c.repeatedCount >= 2) score += 0.04;
        if (c.repeatedCount >= 2 && c.linkedOpenCommitment) score += 0.08;
        if (c.evidenceCount >= 2) score += Math.min(0.05, (c.evidenceCount - 1) * 0.01);

        final boolean obviousSecurity = isSecurity(all) && c.risk >= 0.65 && c.actionability >= 0.55;
        final boolean hardAction = c.explicitRequest && c.actionability >= 0.55;
        final boolean dueCommitment = c.linkedOpenCommitment && deadlineScore(c.deadlineAt, c.nowAt) >= 0.60;
        final boolean contextualEmergency = c.severeContextImpact && (c.urgency >= 0.60 || c.risk >= 0.60);

        boolean surface = obviousSecurity || hardAction || dueCommitment || contextualEmergency || score >= 0.68;

        String reason;
        if (obviousSecurity) reason = "high-risk actionable security situation";
        else if (hardAction) reason = "explicit unresolved request requires action";
        else if (dueCommitment) reason = "open commitment is approaching its deadline";
        else if (contextualEmergency) reason = "contextual impact makes the situation urgent";
        else if (surface) reason = "globally relevant unresolved situation";
        else reason = "insufficient current attention value";

        return new Decision(c, surface, score, reason);
    }

    /**
     * Returns a sparse, globally ranked Now list. Duplicate evidence must already be
     * correlated into one situation before it reaches this layer.
     *
     * Ranking is lexicographic: attention class first, continuous score second. This
     * prevents a pile of small bonuses (deadline + commitment + repetition) from
     * outranking a genuinely critical high-risk actionable situation.
     */
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
                int byRisk = Double.compare(b.candidate.risk, a.candidate.risk);
                if (byRisk != 0) return byRisk;
                int byUrgency = Double.compare(b.candidate.urgency, a.candidate.urgency);
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
        if (c.risk >= 0.80 && c.urgency >= 0.70 && c.actionability >= 0.60) return 4;
        if (c.severeContextImpact && (c.risk >= 0.60 || c.urgency >= 0.75)) return 4;
        if (c.explicitRequest && c.actionability >= 0.65) return 3;
        if (c.linkedOpenCommitment && deadlineScore(c.deadlineAt, c.nowAt) >= 0.60) return 2;
        return 1;
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
