package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
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
        public final int sameNameCollisionGroups;
        public final int sameNameCollisionCandidates;
        public final Map<CognitiveDomainV4.WorldTypeHint,Integer> byType;

        Report(
                int memoriesScanned,
                int memoriesWithCandidates,
                int totalCandidates,
                int durableIdentityCandidates,
                int weakOnlyCandidates,
                int sameNameCollisionGroups,
                int sameNameCollisionCandidates,
                Map<CognitiveDomainV4.WorldTypeHint,Integer> byType) {
            this.memoriesScanned = memoriesScanned;
            this.memoriesWithCandidates = memoriesWithCandidates;
            this.totalCandidates = totalCandidates;
            this.durableIdentityCandidates = durableIdentityCandidates;
            this.weakOnlyCandidates = weakOnlyCandidates;
            this.sameNameCollisionGroups = sameNameCollisionGroups;
            this.sameNameCollisionCandidates = sameNameCollisionCandidates;
            this.byType = Collections.unmodifiableMap(new LinkedHashMap<>(byType));
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
                    ", sameNameCollisionGroups=" + sameNameCollisionGroups +
                    ", sameNameCollisionCandidates=" + sameNameCollisionCandidates +
                    ", byType=" + byType +
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
        EnumMap<CognitiveDomainV4.WorldTypeHint,Integer> byType =
                new EnumMap<>(CognitiveDomainV4.WorldTypeHint.class);
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
                    if (hasDurableClaim(candidate.claims)) durable++; else weakOnly++;
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

        return new Report(memories, memoriesWithCandidates, total, durable, weakOnly,
                collisionGroups, collisionCandidates, byType);
    }

    private static boolean hasDurableClaim(List<CognitiveIdentityV4.IdentityClaim> claims) {
        if (claims == null) return false;
        for (CognitiveIdentityV4.IdentityClaim claim : claims) {
            if (claim != null && claim.durable()) return true;
        }
        return false;
    }
}
