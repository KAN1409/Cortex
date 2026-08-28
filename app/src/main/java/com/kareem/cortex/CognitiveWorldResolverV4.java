package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Conservative, provenance-first resolver for V4 Worlds. */
public final class CognitiveWorldResolverV4 {
    private CognitiveWorldResolverV4() {}

    public enum ResolutionKind {
        CREATED,
        REUSED_DURABLE_IDENTITY,
        DEFERRED_WEAK_IDENTITY,
        AMBIGUOUS
    }

    public static final class Candidate {
        public final String canonicalName;
        public final CognitiveDomainV4.WorldTypeHint typeHint;
        public final List<String> aliases;
        public final List<CognitiveIdentityV4.IdentityClaim> claims;
        public final List<String> evidenceIds;
        public final List<String> memoryIds;
        public final long observedAt;
        public final boolean userConfirmedName;

        public Candidate(
                String canonicalName,
                CognitiveDomainV4.WorldTypeHint typeHint,
                List<String> aliases,
                List<CognitiveIdentityV4.IdentityClaim> claims,
                List<String> evidenceIds,
                List<String> memoryIds,
                long observedAt,
                boolean userConfirmedName) {
            if (canonicalName == null || canonicalName.trim().isEmpty()) throw new IllegalArgumentException("canonicalName required");
            if (typeHint == null) throw new IllegalArgumentException("typeHint required");
            this.canonicalName = canonicalName.trim();
            this.typeHint = typeHint;
            this.aliases = strings(aliases);
            this.claims = claims == null ? Collections.<CognitiveIdentityV4.IdentityClaim>emptyList() : Collections.unmodifiableList(new ArrayList<>(claims));
            this.evidenceIds = ids(evidenceIds);
            this.memoryIds = ids(memoryIds);
            this.observedAt = observedAt;
            this.userConfirmedName = userConfirmedName;
            if (this.evidenceIds.isEmpty() && this.memoryIds.isEmpty()) {
                throw new IllegalArgumentException("World candidate requires Evidence or Memory provenance");
            }
        }
    }

    public static final class Resolution {
        public final ResolutionKind kind;
        public final String worldId;
        public final double confidence;
        public final String reason;

        Resolution(ResolutionKind kind, String worldId, double confidence, String reason) {
            this.kind = kind;
            this.worldId = worldId == null ? "" : worldId;
            this.confidence = confidence;
            this.reason = reason == null ? "" : reason;
        }
    }

    /** Weak name-only candidates are review material, not canonical Worlds. */
    static boolean canMaterializeWithoutReview(Candidate candidate) {
        return candidate != null && (candidate.userConfirmedName || hasDurableClaim(candidate.claims));
    }

    public static Resolution resolve(VaultDb db, Candidate candidate) {
        if (db == null) throw new IllegalArgumentException("db required");
        if (candidate == null) throw new IllegalArgumentException("candidate required");
        CognitiveStoreV4.ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        requireExistingProvenance(sql, candidate);

        ArrayList<MatchRow> durableMatches = new ArrayList<>();
        Cursor worlds = sql.query(
                "v4_worlds",
                new String[]{"id", "canonical_name", "type_hint"},
                "status='ACTIVE' AND type_hint=?",
                new String[]{candidate.typeHint.name()}, null, null, "id ASC");
        try {
            while (worlds.moveToNext()) {
                String worldId = worlds.getString(0);
                List<CognitiveIdentityV4.IdentityClaim> existingClaims = claimsForWorld(sql, worldId);
                CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                        candidate.typeHint, candidate.claims, candidate.typeHint, existingClaims);
                if (match.canAutoMerge()) durableMatches.add(new MatchRow(worldId, match));
            }
        } finally { worlds.close(); }

        if (durableMatches.size() > 1) {
            durableMatches.sort(Comparator.comparing(a -> a.worldId));
            return new Resolution(ResolutionKind.AMBIGUOUS, "", durableMatches.get(0).match.confidence,
                    "multiple durable World identities match; user resolution required");
        }

        if (durableMatches.size() == 1) {
            MatchRow row = durableMatches.get(0);
            attachCandidate(db, row.worldId, candidate, false);
            if (candidate.userConfirmedName) applyUserCorrection(db, row.worldId, candidate.canonicalName, candidate.typeHint, candidate.aliases);
            else CognitiveStoreV4.addWorldAlias(db, row.worldId, candidate.canonicalName, "candidate", row.match.confidence, false);
            touch(sql, row.worldId, candidate.observedAt);
            return new Resolution(ResolutionKind.REUSED_DURABLE_IDENTITY, row.worldId, row.match.confidence, row.match.reason);
        }

        // A name or semantic hint can be useful for review/search without being a safe identity boundary.
        // Do not create provenance-scoped duplicate Worlds just because a weak hint appeared in Evidence.
        if (!canMaterializeWithoutReview(candidate)) {
            return new Resolution(ResolutionKind.DEFERRED_WEAK_IDENTITY, "", 0.0,
                    "weak-only World candidate requires durable identity or explicit user confirmation");
        }

        String seed = seedKey(candidate);
        String worldId = CognitiveIdentityV4.objectId("wo", seed);
        long now = candidate.observedAt > 0 ? candidate.observedAt : System.currentTimeMillis();
        CognitiveDomainV4.World world = new CognitiveDomainV4.World(
                worldId,
                candidate.canonicalName,
                candidate.typeHint,
                candidate.userConfirmedName ? CognitiveDomainV4.WorldMaturity.ESTABLISHED : CognitiveDomainV4.WorldMaturity.EMERGING,
                null,
                candidate.aliases,
                now,
                now,
                null);
        CognitiveStoreV4.putWorld(db, world, seed);
        attachCandidate(db, worldId, candidate, true);
        return new Resolution(ResolutionKind.CREATED, worldId, 1.0,
                candidate.userConfirmedName ? "user-confirmed World identity" : "new durable World identity");
    }

    /** Explicit user correction is authoritative for display identity but preserves the old name as an alias. */
    public static void applyUserCorrection(
            VaultDb db,
            String worldId,
            String canonicalName,
            CognitiveDomainV4.WorldTypeHint typeHint,
            List<String> aliases) {
        if (db == null) throw new IllegalArgumentException("db required");
        if (canonicalName == null || canonicalName.trim().isEmpty()) throw new IllegalArgumentException("canonicalName required");
        if (typeHint == null) throw new IllegalArgumentException("typeHint required");
        String canonicalId = CognitiveStoreV4.canonicalWorldId(db, worldId);
        SQLiteDatabase sql = db.getWritableDatabase();
        Cursor c = sql.query("v4_worlds", new String[]{"canonical_name"}, "id=?", new String[]{canonicalId}, null, null, null, "1");
        String oldName = "";
        try { if (c.moveToFirst()) oldName = n(c.getString(0)); } finally { c.close(); }
        if (!oldName.isEmpty() && !oldName.equals(canonicalName.trim())) {
            CognitiveStoreV4.addWorldAlias(db, canonicalId, oldName, "user-correction-history", 1.0, true);
        }
        ContentValues v = new ContentValues();
        v.put("canonical_name", canonicalName.trim());
        v.put("type_hint", typeHint.name());
        v.put("maturity", CognitiveDomainV4.WorldMaturity.ESTABLISHED.name());
        v.put("updated_at", System.currentTimeMillis());
        sql.update("v4_worlds", v, "id=?", new String[]{canonicalId});
        for (String alias : strings(aliases)) CognitiveStoreV4.addWorldAlias(db, canonicalId, alias, "user", 1.0, true);
    }

    private static void attachCandidate(VaultDb db, String worldId, Candidate c, boolean includeCanonicalAlias) {
        if (includeCanonicalAlias) CognitiveStoreV4.addWorldAlias(db, worldId, c.canonicalName, "candidate", 1.0, c.userConfirmedName);
        for (String alias : c.aliases) CognitiveStoreV4.addWorldAlias(db, worldId, alias, "candidate", 1.0, c.userConfirmedName);
        for (CognitiveIdentityV4.IdentityClaim claim : c.claims) CognitiveStoreV4.addWorldIdentityClaim(db, worldId, claim);

        SQLiteDatabase sql = db.getReadableDatabase();
        for (String evidenceId : c.evidenceIds) {
            putAboutRelation(db, CognitiveDomainV4.CanonicalObjectType.EVIDENCE, evidenceId, worldId,
                    Collections.singletonList(evidenceId));
        }
        for (String memoryId : c.memoryIds) {
            List<String> memoryEvidence = evidenceForMemory(sql, memoryId);
            if (!memoryEvidence.isEmpty()) putAboutRelation(db, CognitiveDomainV4.CanonicalObjectType.MEMORY, memoryId, worldId, memoryEvidence);
        }
    }

    private static void putAboutRelation(VaultDb db, CognitiveDomainV4.CanonicalObjectType sourceType,
                                         String sourceId, String worldId, List<String> evidenceIds) {
        String identity = CognitiveIdentityV4.relationIdentityKey(sourceType, sourceId,
                CognitiveDomainV4.RelationType.ABOUT, CognitiveDomainV4.CanonicalObjectType.WORLD, worldId);
        CognitiveDomainV4.Relation relation = new CognitiveDomainV4.Relation(
                CognitiveIdentityV4.objectId("re", identity), sourceType, sourceId,
                CognitiveDomainV4.CanonicalObjectType.WORLD, worldId,
                CognitiveDomainV4.RelationType.ABOUT, CognitiveDomainV4.GroundingKind.OBSERVED,
                1.0, evidenceIds);
        CognitiveStoreV4.putRelation(db, relation);
    }

    private static String seedKey(Candidate c) {
        String base = CognitiveIdentityV4.worldSeedKey(c.typeHint, c.canonicalName, c.claims);
        if (hasDurableClaim(c.claims) || c.userConfirmedName) return base;
        ArrayList<String> anchors = new ArrayList<>();
        anchors.addAll(c.memoryIds);
        anchors.addAll(c.evidenceIds);
        Collections.sort(anchors);
        return base + "|origin:" + anchors.get(0);
    }

    private static boolean hasDurableClaim(List<CognitiveIdentityV4.IdentityClaim> claims) {
        if (claims == null) return false;
        for (CognitiveIdentityV4.IdentityClaim c : claims) if (c != null && c.durable()) return true;
        return false;
    }

    private static void requireExistingProvenance(SQLiteDatabase db, Candidate c) {
        for (String id : c.evidenceIds) if (!exists(db, "v4_evidence", id)) throw new IllegalArgumentException("unknown evidenceId: " + id);
        for (String id : c.memoryIds) if (!exists(db, "v4_memories", id)) throw new IllegalArgumentException("unknown memoryId: " + id);
    }

    private static boolean exists(SQLiteDatabase db, String table, String id) {
        Cursor c = db.rawQuery("SELECT 1 FROM " + table + " WHERE id=? LIMIT 1", new String[]{id});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    private static List<String> evidenceForMemory(SQLiteDatabase db, String memoryId) {
        ArrayList<String> out = new ArrayList<>();
        Cursor c = db.query("v4_memory_evidence", new String[]{"evidence_id"}, "memory_id=?", new String[]{memoryId}, null, null, "ordinal ASC,evidence_id ASC");
        try { while (c.moveToNext()) out.add(c.getString(0)); } finally { c.close(); }
        return out;
    }

    private static List<CognitiveIdentityV4.IdentityClaim> claimsForWorld(SQLiteDatabase db, String worldId) {
        ArrayList<CognitiveIdentityV4.IdentityClaim> out = new ArrayList<>();
        Cursor c = db.query("v4_world_identity_claims",
                new String[]{"claim_type","claim_value","strength","user_confirmed","evidence_id"},
                "world_id=?", new String[]{worldId}, null, null, "id ASC");
        try {
            while (c.moveToNext()) {
                try {
                    out.add(new CognitiveIdentityV4.IdentityClaim(
                            CognitiveIdentityV4.ClaimType.valueOf(c.getString(0)), c.getString(1),
                            CognitiveIdentityV4.ClaimStrength.valueOf(c.getString(2)), c.getInt(3) != 0, c.getString(4)));
                } catch (Throwable ignored) {}
            }
        } finally { c.close(); }
        return out;
    }

    private static void touch(SQLiteDatabase sql, String worldId, long observedAt) {
        long at = observedAt > 0 ? observedAt : System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("last_active_at", at);
        v.put("updated_at", System.currentTimeMillis());
        sql.update("v4_worlds", v, "id=?", new String[]{worldId});
    }

    private static List<String> ids(List<String> xs) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (xs != null) for (String x : xs) if (x != null && !x.trim().isEmpty()) out.add(x.trim());
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    private static List<String> strings(List<String> xs) { return ids(xs); }
    private static String n(String s) { return s == null ? "" : s.trim(); }

    private static final class MatchRow {
        final String worldId;
        final CognitiveIdentityV4.Match match;
        MatchRow(String worldId, CognitiveIdentityV4.Match match) { this.worldId = worldId; this.match = match; }
    }
}
