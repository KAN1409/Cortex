package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Read-only consolidation of weak analysis-derived World proposals.
 *
 * <p>Corroboration makes a proposal worth reviewing; it never grants merge/materialization
 * authority. Canonical Worlds still require durable identity/type evidence or explicit user input.</p>
 */
public final class CognitiveWorldProposalConsolidatorV4 {
    private CognitiveWorldProposalConsolidatorV4() {}

    public static final class Proposal {
        public final CognitiveDomainV4.WorldTypeHint type;
        public final String canonicalName;
        public final int observations;
        public final int distinctEvidence;
        public final int distinctMemories;
        public final boolean reviewEligible;
        public final List<String> aliases;

        Proposal(CognitiveDomainV4.WorldTypeHint type, String canonicalName, int observations,
                 int distinctEvidence, int distinctMemories, boolean reviewEligible, List<String> aliases) {
            this.type = type;
            this.canonicalName = canonicalName;
            this.observations = observations;
            this.distinctEvidence = distinctEvidence;
            this.distinctMemories = distinctMemories;
            this.reviewEligible = reviewEligible;
            this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
        }
    }

    public static List<Proposal> evaluate(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return evaluate(db.getReadableDatabase());
    }

    static List<Proposal> evaluate(SQLiteDatabase sql) {
        if (sql == null) throw new IllegalArgumentException("db required");
        LinkedHashMap<String,Accumulator> grouped = new LinkedHashMap<>();
        Cursor memories = sql.query("v4_memories", new String[]{"id"}, "state='ACTIVE'", null,
                null, null, "started_at ASC,id ASC");
        try {
            while (memories.moveToNext()) {
                String memoryId = memories.getString(0);
                List<CognitiveWorldResolverV4.Candidate> candidates =
                        CognitiveWorldCandidateExtractorV4.fromMemory(sql, memoryId);
                for (CognitiveWorldResolverV4.Candidate candidate : candidates) {
                    if (!analysisWeakOnly(candidate)) continue;
                    String key = candidate.typeHint.name() + "|" +
                            CognitiveIdentityV4.normalizeText(candidate.canonicalName);
                    Accumulator a = grouped.get(key);
                    if (a == null) {
                        a = new Accumulator(candidate.typeHint, candidate.canonicalName);
                        grouped.put(key, a);
                    }
                    a.observations++;
                    a.evidence.addAll(candidate.evidenceIds);
                    a.memories.addAll(candidate.memoryIds);
                    a.aliases.addAll(candidate.aliases);
                }
            }
        } finally { memories.close(); }

        ArrayList<Proposal> out = new ArrayList<>();
        for (Accumulator a : grouped.values()) {
            int evidence = a.evidence.size();
            int memoryCount = a.memories.size();
            boolean singleTokenPerson = a.type == CognitiveDomainV4.WorldTypeHint.PERSON
                    && CognitiveIdentityV4.normalizeText(a.name).split("\\s+").length == 1;
            boolean review = evidence >= (singleTokenPerson ? 3 : 2) && memoryCount >= 2;
            out.add(new Proposal(a.type, a.name, a.observations, evidence, memoryCount, review,
                    new ArrayList<>(a.aliases)));
        }
        Collections.sort(out, (a,b) -> {
            if (a.reviewEligible != b.reviewEligible) return a.reviewEligible ? -1 : 1;
            int e = Integer.compare(b.distinctEvidence, a.distinctEvidence);
            if (e != 0) return e;
            int m = Integer.compare(b.distinctMemories, a.distinctMemories);
            if (m != 0) return m;
            return a.canonicalName.compareToIgnoreCase(b.canonicalName);
        });
        return Collections.unmodifiableList(out);
    }

    private static boolean analysisWeakOnly(CognitiveWorldResolverV4.Candidate candidate) {
        if (candidate == null || candidate.claims.isEmpty()) return false;
        boolean modelAlias = false;
        for (CognitiveIdentityV4.IdentityClaim claim : candidate.claims) {
            if (claim == null) continue;
            if (claim.durable()) return false;
            if (claim.type == CognitiveIdentityV4.ClaimType.MODEL_ALIAS) modelAlias = true;
        }
        return modelAlias && !candidate.userConfirmedName;
    }

    private static final class Accumulator {
        final CognitiveDomainV4.WorldTypeHint type;
        final String name;
        int observations;
        final LinkedHashSet<String> evidence = new LinkedHashSet<>();
        final LinkedHashSet<String> memories = new LinkedHashSet<>();
        final LinkedHashSet<String> aliases = new LinkedHashSet<>();
        Accumulator(CognitiveDomainV4.WorldTypeHint type, String name) {
            this.type = type; this.name = name;
        }
    }
}
