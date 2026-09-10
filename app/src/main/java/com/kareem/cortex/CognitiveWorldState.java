package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Experimental v70 world-state layer.
 *
 * This layer sits between correlated semantic situations and AttentionDecisionEngine.
 * It does not decide what the UI should show. Its job is to maintain the current truth
 * of a situation across time and attach durable context (commitments / personal links)
 * before attention is evaluated.
 *
 * v70.17 separates CURRENT truth from PEAK historical evidence. Attention reads current
 * urgency/risk/actionability; diagnostics can still see the strongest historical signal.
 */
public final class CognitiveWorldState {
    public static final String VERSION = "cognitive_world_state_002";

    private final Map<Long, Situation> situations = new LinkedHashMap<>();
    private final Map<String, Commitment> commitments = new LinkedHashMap<>();
    private final Map<String, Context> contexts = new LinkedHashMap<>();

    public static final class Observation {
        public final long situationId;
        public final String linkKey;
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
        public final long occurredAt;
        public final boolean materialChange;
        public final boolean explicitRequest;
        public final boolean severeContextImpact;

        public Observation(
                long situationId,
                String linkKey,
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
                long occurredAt,
                boolean materialChange,
                boolean explicitRequest,
                boolean severeContextImpact) {
            if (situationId <= 0) throw new IllegalArgumentException("situationId must be > 0");
            this.situationId = situationId;
            this.linkKey = n(linkKey);
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
            this.occurredAt = occurredAt;
            this.materialChange = materialChange;
            this.explicitRequest = explicitRequest;
            this.severeContextImpact = severeContextImpact;
        }
    }

    /** Durable obligation that can make an otherwise ordinary event attention-worthy. */
    public static final class Commitment {
        public final String id;
        public final String linkKey;
        public final String summary;
        public final long deadlineAt;
        public final boolean open;
        public final double confidence;

        public Commitment(String id, String linkKey, String summary, long deadlineAt, boolean open, double confidence) {
            if (n(id).isEmpty()) throw new IllegalArgumentException("commitment id is empty");
            if (n(linkKey).isEmpty()) throw new IllegalArgumentException("commitment linkKey is empty");
            this.id = n(id);
            this.linkKey = n(linkKey);
            this.summary = n(summary);
            this.deadlineAt = deadlineAt;
            this.open = open;
            this.confidence = clamp01(confidence);
        }
    }

    /** Durable non-event context such as a person/project relevance or elevated risk. */
    public static final class Context {
        public final String linkKey;
        public final double personalRelevance;
        public final double risk;
        public final double actionability;
        public final long validUntil;

        public Context(String linkKey, double personalRelevance, double risk, double actionability, long validUntil) {
            if (n(linkKey).isEmpty()) throw new IllegalArgumentException("context linkKey is empty");
            this.linkKey = n(linkKey);
            this.personalRelevance = clamp01(personalRelevance);
            this.risk = clamp01(risk);
            this.actionability = clamp01(actionability);
            this.validUntil = validUntil;
        }
    }

    public static final class Situation {
        public final long situationId;
        public String linkKey;
        public String type;
        public String state;
        public String subject;
        public String summary;

        /** Aggregate confidence across evidence; all other attention signals below are current. */
        public double confidence;
        public double urgency;
        public double actionability;
        public double personalRelevance;
        public double risk;
        public double novelty;

        /** Historical peaks are retained for diagnostics/history but never directly rank Now. */
        public double peakUrgency;
        public double peakActionability;
        public double peakPersonalRelevance;
        public double peakRisk;
        public double peakNovelty;

        public long deadlineAt;
        public long firstSeenAt;
        public long lastSeenAt;
        public int evidenceCount;
        public int repeatedCount;
        public boolean unresolved;
        public boolean materialChange;
        public boolean explicitRequest;
        public boolean severeContextImpact;

        private Situation(Observation o) {
            situationId = o.situationId;
            linkKey = o.linkKey;
            type = o.type;
            state = o.state;
            subject = o.subject;
            summary = o.summary;
            confidence = o.confidence;
            urgency = o.urgency;
            actionability = o.actionability;
            personalRelevance = o.personalRelevance;
            risk = o.risk;
            novelty = o.novelty;
            peakUrgency = o.urgency;
            peakActionability = o.actionability;
            peakPersonalRelevance = o.personalRelevance;
            peakRisk = o.risk;
            peakNovelty = o.novelty;
            deadlineAt = o.deadlineAt;
            firstSeenAt = o.occurredAt;
            lastSeenAt = o.occurredAt;
            evidenceCount = 1;
            repeatedCount = 1;
            unresolved = !isResolvedState(o.state);
            materialChange = o.materialChange;
            explicitRequest = o.explicitRequest;
            severeContextImpact = o.severeContextImpact;
        }
    }

    /**
     * Applies one correlated observation.
     *
     * Evidence/history is accumulated regardless of arrival order, but CURRENT world truth may
     * only move forward in event time. This prevents a late-arriving old observation from
     * resurrecting stale urgency/risk or overwriting a newer resolution/de-escalation.
     */
    public synchronized Situation observe(Observation o) {
        if (o == null) throw new IllegalArgumentException("observation == null");
        Situation s = situations.get(o.situationId);
        if (s == null) {
            s = new Situation(o);
            situations.put(o.situationId, s);
            return s;
        }

        s.evidenceCount++;
        s.repeatedCount++;
        s.confidence = weighted(s.confidence, o.confidence, s.evidenceCount);
        s.peakUrgency = Math.max(s.peakUrgency, o.urgency);
        s.peakActionability = Math.max(s.peakActionability, o.actionability);
        s.peakPersonalRelevance = Math.max(s.peakPersonalRelevance, o.personalRelevance);
        s.peakRisk = Math.max(s.peakRisk, o.risk);
        s.peakNovelty = Math.max(s.peakNovelty, o.novelty);

        if (s.firstSeenAt <= 0 || (o.occurredAt > 0 && o.occurredAt < s.firstSeenAt)) {
            s.firstSeenAt = o.occurredAt;
        }

        boolean newer;
        if (o.occurredAt <= 0) newer = s.lastSeenAt <= 0;
        else newer = s.lastSeenAt <= 0 || o.occurredAt >= s.lastSeenAt;
        if (!newer) return s;

        String previousState = s.state;
        boolean stateChanged = !o.state.isEmpty() && !norm(o.state).equals(norm(previousState));

        if (!o.linkKey.isEmpty()) s.linkKey = o.linkKey;
        if (!o.type.isEmpty()) s.type = o.type;
        if (!o.state.isEmpty()) s.state = o.state;
        if (!o.subject.isEmpty()) s.subject = o.subject;
        if (!o.summary.isEmpty()) s.summary = o.summary;

        // CURRENT signals follow the newest evidence. Historical maxima remain available above.
        s.urgency = o.urgency;
        s.actionability = o.actionability;
        s.personalRelevance = o.personalRelevance;
        s.risk = o.risk;
        s.novelty = o.novelty;

        // A new explicit deadline replaces an older one (for example a rescheduled commitment).
        // Missing deadline evidence does not erase a known active deadline.
        if (o.deadlineAt > 0) s.deadlineAt = o.deadlineAt;
        if (isResolvedState(s.state)) s.deadlineAt = 0L;

        if (o.occurredAt > 0) s.lastSeenAt = o.occurredAt;
        s.unresolved = !isResolvedState(s.state);
        s.materialChange = o.materialChange;

        // Supporting evidence keeps an already-established request/severe-context flag. A material
        // or lifecycle transition is allowed to replace it, so de-escalation can actually clear it.
        if (o.materialChange || stateChanged) {
            s.explicitRequest = o.explicitRequest;
            s.severeContextImpact = o.severeContextImpact;
        } else {
            s.explicitRequest = s.explicitRequest || o.explicitRequest;
            s.severeContextImpact = s.severeContextImpact || o.severeContextImpact;
        }
        return s;
    }

    public synchronized void putCommitment(Commitment commitment) {
        if (commitment == null) throw new IllegalArgumentException("commitment == null");
        commitments.put(commitment.id, commitment);
    }

    public synchronized void putContext(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        contexts.put(context.linkKey, context);
    }

    public synchronized Situation getSituation(long situationId) {
        return situations.get(situationId);
    }

    public synchronized List<AttentionDecisionEngine.Candidate> attentionCandidates(long nowAt) {
        if (situations.isEmpty()) return Collections.emptyList();
        List<AttentionDecisionEngine.Candidate> out = new ArrayList<>();
        for (Situation s : situations.values()) {
            boolean linkedCommitment = false;
            long deadline = s.deadlineAt;
            double confidence = s.confidence;
            double relevance = s.personalRelevance;
            double risk = s.risk;
            double actionability = s.actionability;

            for (Commitment c : commitments.values()) {
                if (!c.open || c.confidence < 0.70 || !sameLink(s.linkKey, c.linkKey)) continue;
                linkedCommitment = true;
                deadline = earlierPositive(deadline, c.deadlineAt);
                confidence = Math.max(confidence, c.confidence);
                relevance = Math.max(relevance, 0.80);
                actionability = Math.max(actionability, 0.65);
            }

            Context ctx = contexts.get(s.linkKey);
            if (ctx != null && (ctx.validUntil <= 0 || nowAt <= ctx.validUntil)) {
                relevance = Math.max(relevance, ctx.personalRelevance);
                risk = Math.max(risk, ctx.risk);
                actionability = Math.max(actionability, ctx.actionability);
            }

            out.add(new AttentionDecisionEngine.Candidate(
                    s.situationId,
                    s.type,
                    s.state,
                    s.subject,
                    s.summary,
                    confidence,
                    s.urgency,
                    actionability,
                    relevance,
                    risk,
                    s.novelty,
                    deadline,
                    nowAt,
                    s.lastSeenAt,
                    s.repeatedCount,
                    s.evidenceCount,
                    s.unresolved,
                    linkedCommitment,
                    s.materialChange,
                    s.explicitRequest,
                    s.severeContextImpact));
        }

        Collections.sort(out, new Comparator<AttentionDecisionEngine.Candidate>() {
            @Override public int compare(AttentionDecisionEngine.Candidate a, AttentionDecisionEngine.Candidate b) {
                return Long.compare(a.situationId, b.situationId);
            }
        });
        return Collections.unmodifiableList(out);
    }

    public synchronized List<AttentionDecisionEngine.Decision> rankNow(long nowAt, int maxItems) {
        return AttentionDecisionEngine.rankForNow(attentionCandidates(nowAt), maxItems);
    }

    private static boolean sameLink(String a, String b) {
        String x = norm(a), y = norm(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static boolean isResolvedState(String state) {
        String s = norm(state);
        return s.equals("resolved") || s.equals("closed") || s.equals("completed") || s.equals("dismissed");
    }

    private static long earlierPositive(long a, long b) {
        if (a <= 0) return b;
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    private static double weighted(double previous, double next, int countAfter) {
        int previousCount = Math.max(1, countAfter - 1);
        return clamp01(((previous * previousCount) + next) / (previousCount + 1.0));
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
