package com.kareem.cortex;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User-facing projection. FINAL JUDGMENT remains the only attention owner. */
public final class CortexJudgedBriefProjection {
    public static final String VERSION = "cortex_judged_brief_projection_002";
    private CortexJudgedBriefProjection() {}

    public static PrimeBriefStore.Snapshot load(Context context, VaultDb db) {
        PrimeBriefStore.Snapshot base = PrimeBriefStore.load(db);
        if (context == null || db == null) return base;

        long now = System.currentTimeMillis();
        List<AttentionDecisionEngine.Candidate> candidates;
        try { candidates = CognitiveShadowStore.loadCandidates(db.getReadableDatabase(), now); }
        catch (Throwable ignored) { candidates = new ArrayList<>(); }

        /*
         * Important safety rule: absence of canonical candidates must never resurrect legacy
         * ACTION/WAITING/DECISION rows. Those compatibility rows have not passed the current
         * CortexAttentionJudge and were the source of stale phone/footer/security noise in Now.
         * Non-attention surfaces remain available below.
         */
        if (candidates == null || candidates.isEmpty()) {
            return new PrimeBriefStore.Snapshot(
                    base.recent,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    base.changes,
                    base.worthKnowing,
                    base.reviews);
        }

        CortexAttentionJudge.RuntimeContext runtime = CortexAttentionJudge.RuntimeContext.neutral();
        try {
            for (AttentionDecisionEngine.Candidate candidate : candidates) {
                CortexAttentionJudge.Judgment trace = CortexAttentionJudge.evaluate(
                        context.getApplicationContext(), candidate, runtime);
                CortexJudgmentTraceStore.record(db.getWritableDatabase(), trace);
            }
            CortexJudgmentTraceStore.prune(db.getWritableDatabase());
        } catch (Throwable ignored) {}

        List<CortexAttentionJudge.Judgment> judgments = CortexAttentionJudge.rankForNow(
                context.getApplicationContext(), candidates, runtime,
                CortexPersonalPolicy.maxNowItems(context));

        Set<Long> selectedSituations = new HashSet<>();
        for (CortexAttentionJudge.Judgment judgment : judgments) {
            if (judgment != null && judgment.surfaceNow && judgment.candidate != null)
                selectedSituations.add(judgment.candidate.situationId);
        }

        return new PrimeBriefStore.Snapshot(
                base.recent,
                filterCanonicalAttention(base.actions, selectedSituations),
                filterCanonicalAttention(base.waiting, selectedSituations),
                filterCanonicalAttention(base.decisions, selectedSituations),
                base.changes,
                base.worthKnowing,
                base.reviews);
    }

    private static ArrayList<PrimeBriefStore.Item> filterCanonicalAttention(
            List<PrimeBriefStore.Item> items, Set<Long> selectedSituations) {
        ArrayList<PrimeBriefStore.Item> out = new ArrayList<>();
        if (items == null) return out;
        for (PrimeBriefStore.Item item : items) {
            if (item == null) continue;
            boolean canonical = item.id >= UniversalEventStore.ATTENTION_COMPAT_OFFSET;
            if (canonical && item.threadId > 0 && selectedSituations.contains(item.threadId)) out.add(item);
        }
        return out;
    }
}
