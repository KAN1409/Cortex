package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only Stage D diagnostic over canonical V4 Memory/Evidence.
 *
 * <p>This deliberately never writes Worlds, aliases, claims, relations, or provenance. It is a
 * release gate for candidate quality before enabling any World materialization on real data.</p>
 */
public final class CognitiveWorldDryRunV4 {
    private CognitiveWorldDryRunV4() {}

    public static final class Report {
        public final int memoriesScanned;
        public final int memoriesWithCandidates;
        public final int totalCandidates;
        public final int durableIdentityCandidates;
        public final int weakOnlyCandidates;
        public final int materializableNewCandidates;
        public final int durableButTypeUnconfirmed;
        public final int sameNameCollisionGroups;
        public final int sameNameCollisionCandidates;
        public final int classifiedEvidence;
        public final Map<CognitiveDomainV4.WorldTypeHint,Integer> byType;
        public final Map<CognitiveWorldCandidateClassifierV4.SemanticClass,Integer> bySemanticClass;

        Report(
                int memoriesScanned,
                int memoriesWithCandidates,
                int totalCandidates,
                int durableIdentityCandidates,
                int weakOnlyCandidates,
                int materializableNewCandidates,
                int durableButTypeUnconfirmed,
                int sameNameCollisionGroups,
                int sameNameCollisionCandidates,
                int classifiedEvidence,
                Map<CognitiveDomainV4.WorldTypeHint,Integer> byType,
                Map<CognitiveWorldCandidateClassifierV4.SemanticClass,Integer> bySemanticClass) {
            this.memoriesScanned = memoriesScanned;
            this.memoriesWithCandidates = memoriesWithCandidates;
            this.totalCandidates = totalCandidates;
            this.durableIdentityCandidates = durableIdentityCandidates;
            this.weakOnlyCandidates = weakOnlyCandidates;
            this.materializableNewCandidates = materializableNewCandidates;
            this.durableButTypeUnconfirmed = durableButTypeUnconfirmed;
            this.sameNameCollisionGroups = sameNameCollisionGroups;
            this.sameNameCollisionCandidates = sameNameCollisionCandidates;
            this.classifiedEvidence = classifiedEvidence;
            this.byType = Collections.unmodifiableMap(new LinkedHashMap<>(byType));
            this.bySemanticClass = Collections.unmodifiableMap(new LinkedHashMap<>(bySemanticClass));
        }

        public double candidateMemoryRate() {
            return memoriesScanned <= 0 ? 0.0 : (double) memoriesWithCandidates / (double) memoriesScanned;
        }

        @Override public String toString() {
            return "WorldDryRun{" +
                    "memoriesScanned=" + memoriesScanned +
                    ", memoriesWithCandidates=" + memoriesWithCandidates +
                    ", totalCandidates=" + totalCandidates +
                    ", durableIdentityCandidates=" + durableIdentityCandidates +
                    ", weakOnlyCandidates=" + weakOnlyCandidates +
                    ", materializableNewCandidates=" + materializableNewCandidates +
                    ", durableButTypeUnconfirmed=" + durableButTypeUnconfirmed +
                    ", sameNameCollisionGroups=" + sameNameCollisionGroups +
                    ", sameNameCollisionCandidates=" + sameNameCollisionCandidates +
                    ", classifiedEvidence=" + classifiedEvidence +
                    ", byType=" + byType +
                    ", bySemanticClass=" + bySemanticClass +
                    '}';
        }
    }

    public static Report evaluate(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return evaluate(db.getReadableDatabase());
    }

    static Report evaluate(SQLiteDatabase sql) {
        if (sql == null) throw new IllegalArgumentException("db required");

        int memories = 0;
        int memoriesWithCandidates = 0;
        int total = 0;
        int durable = 0;
        int weakOnly = 0;
        int materializable = 0;
        int durableTypeUnconfirmed = 0;
        EnumMap<CognitiveDomainV4.WorldTypeHint,Integer> byType =
                new EnumMap<>(CognitiveDomainV4.WorldTypeHint.class);
        EnumMap<CognitiveWorldCandidateClassifierV4.SemanticClass,Integer> bySemantic =
                new EnumMap<>(CognitiveWorldCandidateClassifierV4.SemanticClass.class);
        HashMap<String,Integer> byNormalizedName = new HashMap<>();

        Cursor c = sql.query(
                "v4_memories",
                new String[]{"id"},
                "state='ACTIVE'",
                null, null, null,
                "started_at ASC,id ASC");
        try {
            while (c.moveToNext()) {
                memories++;
                List<CognitiveWorldResolverV4.Candidate> candidates =
                        CognitiveWorldCandidateExtractorV4.fromMemory(sql, c.getString(0));
                if (!candidates.isEmpty()) memoriesWithCandidates++;
                for (CognitiveWorldResolverV4.Candidate candidate : candidates) {
                    total++;
                    byType.put(candidate.typeHint, byType.containsKey(candidate.typeHint)
                            ? byType.get(candidate.typeHint) + 1 : 1);
                    boolean hasDurable = hasDurableClaim(candidate.claims);
                    if (hasDurable) durable++; else weakOnly++;
                    if (CognitiveWorldResolverV4.canMaterializeWithoutReview(candidate)) materializable++;
                    else if (hasDurable && !candidate.typeMaterializationApproved) durableTypeUnconfirmed++;
                    String nameKey = candidate.typeHint.name() + "|" +
                            CognitiveIdentityV4.normalizeText(candidate.canonicalName);
                    byNormalizedName.put(nameKey, byNormalizedName.containsKey(nameKey)
                            ? byNormalizedName.get(nameKey) + 1 : 1);
                }
            }
        } finally { c.close(); }

        int collisionGroups = 0;
        int collisionCandidates = 0;
        for (Integer count : byNormalizedName.values()) {
            if (count != null && count > 1) {
                collisionGroups++;
                collisionCandidates += count;
            }
        }

        int classifiedEvidence = 0;
        Cursor evidence = sql.rawQuery(
                "SELECT DISTINCT e.id,e.source_package,e.metadata_json " +
                        "FROM v4_evidence e " +
                        "JOIN v4_memory_evidence me ON me.evidence_id=e.id " +
                        "JOIN v4_memories m ON m.id=me.memory_id AND m.state='ACTIVE' " +
                        "ORDER BY e.id ASC", null);
        try {
            while (evidence.moveToNext()) {
                CognitiveWorldCandidateClassifierV4.Decision d = CognitiveWorldCandidateClassifierV4.inspect(
                        evidence.getString(1), evidence.getString(2));
                if (d.candidateName.isEmpty()
                        && d.semanticClass == CognitiveWorldCandidateClassifierV4.SemanticClass.UNKNOWN) continue;
                classifiedEvidence++;
                bySemantic.put(d.semanticClass, bySemantic.containsKey(d.semanticClass)
                        ? bySemantic.get(d.semanticClass) + 1 : 1);
            }
        } finally { evidence.close(); }

        return new Report(memories, memoriesWithCandidates, total, durable, weakOnly,
                materializable, durableTypeUnconfirmed, collisionGroups, collisionCandidates,
                classifiedEvidence, byType, bySemantic);
    }

    private static boolean hasDurableClaim(List<CognitiveIdentityV4.IdentityClaim> claims) {
        if (claims == null) return false;
        for (CognitiveIdentityV4.IdentityClaim claim : claims) {
            if (claim != null && claim.durable()) return true;
        }
        return false;
    }
}
