package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/**
 * Canonical V4 write boundary.
 *
 * <p>This store is intentionally not used by the current product surfaces yet. It provides the
 * additive persistence path required for incremental backfill and later projection cut-over.</p>
 */
public final class CognitiveStoreV4 {
    private CognitiveStoreV4() {}

    public static void ensure(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveSchemaV4.ensure(db.getWritableDatabase());
    }

    public static String putEvidence(
            VaultDb db,
            CognitiveDomainV4.Evidence evidence,
            String metadataJson,
            long expiresAt) {
        if (evidence == null) throw new IllegalArgumentException("evidence required");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String identityKey = CognitiveIdentityV4.evidenceKey(
                evidence.sourceType,
                evidence.sourcePackage,
                evidence.externalId,
                evidence.contentHash,
                evidence.normalizedText,
                evidence.occurredAt);
        long now = System.currentTimeMillis();

        ContentValues v = new ContentValues();
        v.put("id", evidence.id);
        v.put("identity_key", identityKey);
        v.put("source_type", evidence.sourceType.name());
        put(v, "source_package", evidence.sourcePackage);
        put(v, "external_id", evidence.externalId);
        v.put("occurred_at", evidence.occurredAt);
        v.put("captured_at", evidence.capturedAt);
        put(v, "original_text", evidence.originalText);
        put(v, "normalized_text", evidence.normalizedText);
        put(v, "content_hash", evidence.contentHash);
        put(v, "asset_ref", evidence.assetRef);
        v.put("sensitivity", evidence.sensitivity.name());
        v.put("retention_class", evidence.retentionClass.name());
        v.put("expires_at", expiresAt);
        v.put("processing_state", evidence.processingState.name());
        put(v, "metadata_json", metadataJson);
        v.put("created_at", now);
        v.put("updated_at", now);

        long inserted = sql.insertWithOnConflict("v4_evidence", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (inserted >= 0) return evidence.id;

        String existing = idByIdentity(sql, "v4_evidence", identityKey);
        if (existing.isEmpty()) throw new IllegalStateException("Evidence identity conflict without existing row");

        // Immutable capture fields are intentionally not replaced on duplicate callbacks.
        ContentValues touch = new ContentValues();
        touch.put("processing_state", evidence.processingState.name());
        touch.put("updated_at", now);
        sql.update("v4_evidence", touch, "id=?", new String[]{existing});
        return existing;
    }

    public static String appendEvidenceAnalysis(
            VaultDb db,
            String evidenceId,
            String analysisKind,
            String engine,
            String version,
            String outputText,
            String outputJson) {
        ensure(db);
        String contentHash = Fingerprint.text((outputText == null ? "" : outputText) + "\n" + (outputJson == null ? "" : outputJson));
        String identity = "analysis|" + n(evidenceId) + "|" + n(analysisKind) + "|" + n(engine) + "|" + n(version) + "|" + contentHash;
        String id = CognitiveIdentityV4.objectId("an", identity);
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("evidence_id", require(evidenceId, "evidenceId"));
        v.put("analysis_kind", require(analysisKind, "analysisKind"));
        v.put("engine", require(engine, "engine"));
        v.put("version", require(version, "version"));
        put(v, "output_text", outputText);
        put(v, "output_json", outputJson);
        v.put("content_hash", contentHash);
        v.put("created_at", System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict("v4_evidence_analysis", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return id;
    }

    public static String putEpisode(
            VaultDb db,
            CognitiveDomainV4.Episode episode,
            String durableContextKey) {
        if (episode == null) throw new IllegalArgumentException("episode required");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String identity = CognitiveIdentityV4.episodeIdentityKey(
                episode.kind,
                episode.primarySourcePackage,
                durableContextKey,
                episode.startedAt);
        long now = System.currentTimeMillis();

        ContentValues v = new ContentValues();
        v.put("id", episode.id);
        v.put("identity_key", identity);
        v.put("kind", episode.kind.name());
        v.put("state", episode.state.name());
        put(v, "primary_source_package", episode.primarySourcePackage);
        put(v, "durable_context_key", durableContextKey);
        v.put("started_at", episode.startedAt);
        v.put("ended_at", episode.endedAt == null ? 0 : episode.endedAt.longValue());
        v.put("created_at", now);
        v.put("updated_at", now);
        long inserted = sql.insertWithOnConflict("v4_episodes", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        String id = inserted >= 0 ? episode.id : idByIdentity(sql, "v4_episodes", identity);
        if (id.isEmpty()) throw new IllegalStateException("Episode identity conflict without row");

        ContentValues update = new ContentValues();
        update.put("state", episode.state.name());
        update.put("ended_at", episode.endedAt == null ? 0 : episode.endedAt.longValue());
        update.put("updated_at", now);
        sql.update("v4_episodes", update, "id=?", new String[]{id});

        for (String evidenceId : episode.evidenceIds) linkEpisodeEvidence(sql, id, evidenceId, "member", true);
        return id;
    }

    public static String putMemory(
            VaultDb db,
            CognitiveDomainV4.Memory memory,
            String stableSemanticAnchor,
            long expiresAt) {
        if (memory == null) throw new IllegalArgumentException("memory required");
        if (memory.evidenceIds.isEmpty()) throw new IllegalArgumentException("Memory requires Evidence provenance");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String identity = CognitiveIdentityV4.memoryIdentityKey(
                memory.kind,
                memory.episodeId,
                memory.evidenceIds,
                stableSemanticAnchor);
        long now = System.currentTimeMillis();

        ContentValues v = new ContentValues();
        v.put("id", memory.id);
        v.put("identity_key", identity);
        v.put("kind", memory.kind.name());
        put(v, "title", memory.title);
        v.put("body", memory.body);
        put(v, "episode_id", memory.episodeId);
        put(v, "source_package", memory.sourcePackage);
        v.put("started_at", memory.startedAt);
        v.put("ended_at", memory.endedAt == null ? 0 : memory.endedAt.longValue());
        v.put("importance", memory.importance);
        v.put("pinned", memory.pinned ? 1 : 0);
        v.put("retention_class", memory.retentionClass.name());
        v.put("expires_at", expiresAt);
        v.put("state", "ACTIVE");
        v.put("created_at", now);
        v.put("updated_at", now);
        long inserted = sql.insertWithOnConflict("v4_memories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        String id = inserted >= 0 ? memory.id : idByIdentity(sql, "v4_memories", identity);
        if (id.isEmpty()) throw new IllegalStateException("Memory identity conflict without row");

        // Memory presentation is derived and may improve while its Evidence identity stays fixed.
        ContentValues update = new ContentValues();
        put(update, "title", memory.title);
        update.put("body", memory.body);
        update.put("importance", memory.importance);
        update.put("pinned", memory.pinned ? 1 : 0);
        update.put("retention_class", memory.retentionClass.name());
        update.put("expires_at", expiresAt);
        update.put("updated_at", now);
        sql.update("v4_memories", update, "id=?", new String[]{id});

        int ordinal = 0;
        for (String evidenceId : memory.evidenceIds) {
            ContentValues link = new ContentValues();
            link.put("memory_id", id);
            link.put("evidence_id", evidenceId);
            link.put("role", "supports");
            link.put("ordinal", ordinal++);
            link.put("created_at", now);
            sql.insertWithOnConflict("v4_memory_evidence", null, link, SQLiteDatabase.CONFLICT_IGNORE);
            addProvenance(sql, "MEMORY", id, "EVIDENCE", evidenceId, "supports", 1.0);
        }
        return id;
    }

    public static String putWorld(
            VaultDb db,
            CognitiveDomainV4.World world,
            String seedKey) {
        if (world == null) throw new IllegalArgumentException("world required");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", world.id);
        v.put("seed_key", require(seedKey, "seedKey"));
        v.put("canonical_name", world.canonicalName);
        v.put("type_hint", world.typeHint.name());
        v.put("maturity", world.maturity.name());
        put(v, "summary", world.summary);
        v.put("status", "ACTIVE");
        v.put("created_at", world.createdAt > 0 ? world.createdAt : now);
        v.put("last_active_at", world.lastActiveAt > 0 ? world.lastActiveAt : now);
        v.put("archived_at", world.archivedAt == null ? 0 : world.archivedAt.longValue());
        v.put("updated_at", now);
        sql.insertWithOnConflict("v4_worlds", null, v, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues update = new ContentValues();
        update.put("canonical_name", world.canonicalName);
        update.put("type_hint", world.typeHint.name());
        update.put("maturity", world.maturity.name());
        put(update, "summary", world.summary);
        update.put("last_active_at", world.lastActiveAt > 0 ? world.lastActiveAt : now);
        update.put("archived_at", world.archivedAt == null ? 0 : world.archivedAt.longValue());
        update.put("updated_at", now);
        sql.update("v4_worlds", update, "id=?", new String[]{world.id});

        for (String alias : world.aliases) addWorldAlias(sql, world.id, alias, "domain", 1.0, false);
        return world.id;
    }

    public static void addWorldAlias(
            VaultDb db,
            String worldId,
            String alias,
            String source,
            double confidence,
            boolean userConfirmed) {
        ensure(db);
        addWorldAlias(db.getWritableDatabase(), worldId, alias, source, confidence, userConfirmed);
    }

    public static void addWorldIdentityClaim(
            VaultDb db,
            String worldId,
            CognitiveIdentityV4.IdentityClaim claim) {
        if (claim == null) throw new IllegalArgumentException("claim required");
        ensure(db);
        ContentValues v = new ContentValues();
        v.put("world_id", require(worldId, "worldId"));
        v.put("claim_type", claim.type.name());
        v.put("claim_value", claim.value);
        v.put("normalized_value", claim.normalizedValue);
        v.put("strength", claim.strength.name());
        v.put("user_confirmed", claim.userConfirmed ? 1 : 0);
        put(v, "evidence_id", claim.evidenceId);
        long now = System.currentTimeMillis();
        v.put("created_at", now);
        v.put("updated_at", now);
        db.getWritableDatabase().insertWithOnConflict("v4_world_identity_claims", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (!claim.evidenceId.isEmpty()) {
            addProvenance(db.getWritableDatabase(), "WORLD", worldId, "EVIDENCE", claim.evidenceId, "identity", 1.0);
        }
    }

    public static boolean mergeWorlds(
            VaultDb db,
            String childWorldId,
            String parentWorldId,
            CognitiveIdentityV4.Match match,
            boolean userConfirmed) {
        require(childWorldId, "childWorldId");
        require(parentWorldId, "parentWorldId");
        if (childWorldId.equals(parentWorldId)) return false;
        if (!userConfirmed && (match == null || !match.canAutoMerge())) {
            throw new IllegalArgumentException("World merge requires strong identity or user confirmation");
        }
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        if (hasActiveMerge(sql, childWorldId)) return false;
        long now = System.currentTimeMillis();
        ContentValues m = new ContentValues();
        m.put("child_world_id", childWorldId);
        m.put("parent_world_id", parentWorldId);
        m.put("state", "ACTIVE");
        m.put("reason", match == null ? "user confirmed merge" : match.reason);
        m.put("confidence", match == null ? 1.0 : match.confidence);
        m.put("user_confirmed", userConfirmed ? 1 : 0);
        m.put("created_at", now);
        m.put("reverted_at", 0);
        long row = sql.insert("v4_world_merges", null, m);
        if (row < 0) return false;
        ContentValues u = new ContentValues();
        u.put("status", "MERGED");
        u.put("merged_into_world_id", parentWorldId);
        u.put("updated_at", now);
        sql.update("v4_worlds", u, "id=?", new String[]{childWorldId});
        return true;
    }

    public static boolean revertWorldMerge(VaultDb db, String childWorldId) {
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues m = new ContentValues();
        m.put("state", "REVERTED");
        m.put("reverted_at", now);
        int changed = sql.update("v4_world_merges", m, "child_world_id=? AND state='ACTIVE'", new String[]{childWorldId});
        if (changed <= 0) return false;
        ContentValues u = new ContentValues();
        u.put("status", "ACTIVE");
        u.putNull("merged_into_world_id");
        u.put("updated_at", now);
        sql.update("v4_worlds", u, "id=?", new String[]{childWorldId});
        return true;
    }

    public static String canonicalWorldId(VaultDb db, String worldId) {
        ensure(db);
        SQLiteDatabase sql = db.getReadableDatabase();
        String current = require(worldId, "worldId");
        for (int i = 0; i < 8; i++) {
            Cursor c = sql.query("v4_worlds", new String[]{"status", "merged_into_world_id"}, "id=?", new String[]{current}, null, null, null, "1");
            String next = "";
            if (c.moveToFirst() && "MERGED".equals(c.getString(0))) next = n(c.getString(1));
            c.close();
            if (next.isEmpty() || next.equals(current)) return current;
            current = next;
        }
        return current;
    }

    public static String putFact(VaultDb db, CognitiveDomainV4.Fact fact) {
        if (fact == null) throw new IllegalArgumentException("fact required");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String slot = CognitiveIdentityV4.factSlotKey(fact.subjectWorldId, fact.predicate);
        String version = CognitiveIdentityV4.factVersionKey(fact.subjectWorldId, fact.predicate, fact.value, fact.validFrom);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", fact.id);
        v.put("slot_key", slot);
        v.put("version_key", version);
        put(v, "subject_world_id", fact.subjectWorldId);
        v.put("predicate", fact.predicate);
        v.put("value", fact.value);
        v.put("grounding", fact.grounding.name());
        v.put("confidence", fact.confidence);
        v.put("valid_from", fact.validFrom == null ? 0 : fact.validFrom.longValue());
        v.put("valid_until", fact.validUntil == null ? 0 : fact.validUntil.longValue());
        put(v, "supersedes_fact_id", fact.supersedesFactId);
        v.put("status", fact.status.name());
        v.put("created_at", now);
        v.put("updated_at", now);
        sql.insertWithOnConflict("v4_facts", null, v, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues update = new ContentValues();
        update.put("confidence", fact.confidence);
        update.put("status", fact.status.name());
        update.put("valid_until", fact.validUntil == null ? 0 : fact.validUntil.longValue());
        update.put("updated_at", now);
        sql.update("v4_facts", update, "id=?", new String[]{fact.id});

        for (String evidenceId : fact.evidenceIds) addProvenance(sql, "FACT", fact.id, "EVIDENCE", evidenceId, "supports", 1.0);
        for (String memoryId : fact.memoryIds) addProvenance(sql, "FACT", fact.id, "MEMORY", memoryId, "supports", 1.0);
        return fact.id;
    }

    public static String putRelation(VaultDb db, CognitiveDomainV4.Relation relation) {
        if (relation == null) throw new IllegalArgumentException("relation required");
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String identity = CognitiveIdentityV4.relationIdentityKey(
                relation.sourceType,
                relation.sourceId,
                relation.relationType,
                relation.targetType,
                relation.targetId);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", relation.id);
        v.put("identity_key", identity);
        v.put("source_type", relation.sourceType.name());
        v.put("source_id", relation.sourceId);
        v.put("target_type", relation.targetType.name());
        v.put("target_id", relation.targetId);
        v.put("relation_type", relation.relationType.name());
        v.put("grounding", relation.grounding.name());
        v.put("confidence", relation.confidence);
        v.put("state", "ACTIVE");
        v.put("created_at", now);
        v.put("updated_at", now);
        long inserted = sql.insertWithOnConflict("v4_relations", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        String id = inserted >= 0 ? relation.id : idByIdentity(sql, "v4_relations", identity);
        if (id.isEmpty()) throw new IllegalStateException("Relation identity conflict without row");
        ContentValues update = new ContentValues();
        update.put("grounding", relation.grounding.name());
        update.put("confidence", relation.confidence);
        update.put("updated_at", now);
        sql.update("v4_relations", update, "id=?", new String[]{id});
        for (String evidenceId : relation.evidenceIds) addProvenance(sql, "RELATION", id, "EVIDENCE", evidenceId, "supports", 1.0);
        return id;
    }

    public static String putSituation(
            VaultDb db,
            CognitiveDomainV4.Situation situation,
            String primaryWorldId,
            String semanticAnchor,
            String occurrenceKey) {
        if (situation == null) throw new IllegalArgumentException("situation required");
        if (situation.evidenceIds.isEmpty() && situation.memoryIds.isEmpty() && situation.factIds.isEmpty()) {
            throw new IllegalArgumentException("Situation requires canonical support");
        }
        ensure(db);
        SQLiteDatabase sql = db.getWritableDatabase();
        String identity = CognitiveIdentityV4.situationIdentityKey(
                situation.kind,
                primaryWorldId,
                semanticAnchor,
                occurrenceKey);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", situation.id);
        v.put("identity_key", identity);
        v.put("kind", situation.kind.name());
        v.put("state", situation.state.name());
        v.put("headline", situation.headline);
        put(v, "explanation", situation.explanation);
        put(v, "primary_world_id", primaryWorldId);
        v.put("semantic_anchor", require(semanticAnchor, "semanticAnchor"));
        put(v, "occurrence_key", occurrenceKey);
        v.put("relevant_from", situation.relevantFrom == null ? 0 : situation.relevantFrom.longValue());
        v.put("relevant_until", situation.relevantUntil == null ? 0 : situation.relevantUntil.longValue());
        v.put("attention_score", situation.attentionScore);
        v.put("interruption_score", situation.interruptionScore);
        v.put("confidence", situation.confidence);
        v.put("created_at", situation.createdAt > 0 ? situation.createdAt : now);
        v.put("last_evaluated_at", situation.lastEvaluatedAt);
        v.put("updated_at", now);
        v.put("resolved_at", situation.isResolved() ? now : 0);
        long inserted = sql.insertWithOnConflict("v4_situations", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        String id = inserted >= 0 ? situation.id : idByIdentity(sql, "v4_situations", identity);
        if (id.isEmpty()) throw new IllegalStateException("Situation identity conflict without row");

        ContentValues update = new ContentValues();
        update.put("state", situation.state.name());
        update.put("headline", situation.headline);
        put(update, "explanation", situation.explanation);
        update.put("attention_score", situation.attentionScore);
        update.put("interruption_score", situation.interruptionScore);
        update.put("confidence", situation.confidence);
        update.put("last_evaluated_at", situation.lastEvaluatedAt);
        update.put("updated_at", now);
        update.put("resolved_at", situation.isResolved() ? now : 0);
        sql.update("v4_situations", update, "id=?", new String[]{id});

        for (String evidenceId : situation.evidenceIds) addProvenance(sql, "SITUATION", id, "EVIDENCE", evidenceId, "supports", 1.0);
        for (String memoryId : situation.memoryIds) addProvenance(sql, "SITUATION", id, "MEMORY", memoryId, "supports", 1.0);
        for (String factId : situation.factIds) addProvenance(sql, "SITUATION", id, "FACT", factId, "supports", 1.0);
        return id;
    }

    public static String putActionProposal(VaultDb db, CognitiveDomainV4.ActionProposal action) {
        if (action == null) throw new IllegalArgumentException("action required");
        ensure(db);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", action.id);
        put(v, "situation_id", action.situationId);
        put(v, "world_id", action.worldId);
        v.put("action_type", action.type.name());
        v.put("label", action.label);
        v.put("risk", action.risk.name());
        put(v, "payload_json", action.payloadJson);
        v.put("state", action.state.name());
        v.put("created_at", now);
        v.put("updated_at", now);
        db.getWritableDatabase().insertWithOnConflict("v4_action_proposals", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues update = new ContentValues();
        update.put("state", action.state.name());
        update.put("updated_at", now);
        db.getWritableDatabase().update("v4_action_proposals", update, "id=?", new String[]{action.id});
        return action.id;
    }

    public static void mapLegacy(
            VaultDb db,
            String legacyTable,
            String legacyId,
            CognitiveDomainV4.CanonicalObjectType objectType,
            String objectId,
            String state) {
        ensure(db);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("legacy_table", require(legacyTable, "legacyTable"));
        v.put("legacy_id", require(legacyId, "legacyId"));
        v.put("object_type", objectType.name());
        v.put("object_id", require(objectId, "objectId"));
        v.put("migration_state", n(state).isEmpty() ? "MAPPED" : state);
        v.put("created_at", now);
        v.put("updated_at", now);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.insertWithOnConflict("v4_legacy_map", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues update = new ContentValues();
        update.put("object_id", objectId);
        update.put("migration_state", n(state).isEmpty() ? "MAPPED" : state);
        update.put("updated_at", now);
        sql.update("v4_legacy_map", update,
                "legacy_table=? AND legacy_id=? AND object_type=?",
                new String[]{legacyTable, legacyId, objectType.name()});
    }

    public static void addProvenance(
            VaultDb db,
            CognitiveDomainV4.CanonicalObjectType objectType,
            String objectId,
            CognitiveDomainV4.CanonicalObjectType sourceType,
            String sourceId,
            String role,
            double confidence) {
        ensure(db);
        addProvenance(db.getWritableDatabase(), objectType.name(), objectId, sourceType.name(), sourceId, role, confidence);
    }

    private static void linkEpisodeEvidence(SQLiteDatabase sql, String episodeId, String evidenceId, String role, boolean primary) {
        ContentValues link = new ContentValues();
        link.put("episode_id", episodeId);
        link.put("evidence_id", require(evidenceId, "evidenceId"));
        link.put("role", role);
        link.put("is_primary", primary ? 1 : 0);
        link.put("created_at", System.currentTimeMillis());
        sql.insertWithOnConflict("v4_episode_evidence", null, link, SQLiteDatabase.CONFLICT_IGNORE);
        addProvenance(sql, "EPISODE", episodeId, "EVIDENCE", evidenceId, role, 1.0);
    }

    private static void addWorldAlias(
            SQLiteDatabase sql,
            String worldId,
            String alias,
            String source,
            double confidence,
            boolean userConfirmed) {
        String normalized = CognitiveIdentityV4.normalizeText(alias);
        if (normalized.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("world_id", require(worldId, "worldId"));
        v.put("alias", alias.trim());
        v.put("normalized_alias", normalized);
        put(v, "source", source);
        v.put("confidence", clamp01(confidence));
        v.put("user_confirmed", userConfirmed ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        sql.insertWithOnConflict("v4_world_aliases", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void addProvenance(
            SQLiteDatabase sql,
            String objectType,
            String objectId,
            String sourceType,
            String sourceId,
            String role,
            double confidence) {
        ContentValues v = new ContentValues();
        v.put("object_type", require(objectType, "objectType"));
        v.put("object_id", require(objectId, "objectId"));
        v.put("source_type", require(sourceType, "sourceType"));
        v.put("source_id", require(sourceId, "sourceId"));
        v.put("role", require(role, "role"));
        v.put("confidence", clamp01(confidence));
        v.put("created_at", System.currentTimeMillis());
        sql.insertWithOnConflict("v4_provenance", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static boolean hasActiveMerge(SQLiteDatabase sql, String childWorldId) {
        Cursor c = sql.rawQuery("SELECT 1 FROM v4_world_merges WHERE child_world_id=? AND state='ACTIVE' LIMIT 1", new String[]{childWorldId});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }

    private static String idByIdentity(SQLiteDatabase sql, String table, String identityKey) {
        Cursor c = sql.query(table, new String[]{"id"}, "identity_key=?", new String[]{identityKey}, null, null, null, "1");
        String id = c.moveToFirst() ? n(c.getString(0)) : "";
        c.close();
        return id;
    }

    private static void put(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static String require(String value, String name) {
        String x = n(value);
        if (x.isEmpty()) throw new IllegalArgumentException(name + " required");
        return x;
    }

    private static String n(String value) {
        return value == null ? "" : value.trim();
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
