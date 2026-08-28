package com.kareem.cortex;

import android.database.sqlite.SQLiteDatabase;

/**
 * Additive persistence schema for Cognitive Architecture V4.
 *
 * <p>All tables are namespaced with {@code v4_}. This schema does not replace or delete legacy
 * Cortex tables. Backfill can therefore be incremental and reversible while the current product
 * remains operational.</p>
 */
public final class CognitiveSchemaV4 {
    public static final String REVISION = "cognitive_v4_001";

    private CognitiveSchemaV4() {}

    public static void ensure(SQLiteDatabase db) {
        if (db == null) throw new IllegalArgumentException("db required");
        createEvidence(db);
        createEpisodes(db);
        createMemories(db);
        createWorlds(db);
        createFacts(db);
        createRelations(db);
        createSituations(db);
        createActions(db);
        createProvenance(db);
        createLegacyBridge(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS schema_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("INSERT OR REPLACE INTO schema_meta(key,value,updated_at) VALUES('cognitive_schema_v4','" + REVISION + "',strftime('%s','now')*1000)");
    }

    private static void createEvidence(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_evidence(" +
                "id TEXT PRIMARY KEY," +
                "identity_key TEXT NOT NULL UNIQUE," +
                "source_type TEXT NOT NULL," +
                "source_package TEXT," +
                "external_id TEXT," +
                "occurred_at INTEGER NOT NULL," +
                "captured_at INTEGER NOT NULL," +
                "original_text TEXT," +
                "normalized_text TEXT," +
                "content_hash TEXT," +
                "asset_ref TEXT," +
                "sensitivity TEXT NOT NULL DEFAULT 'NORMAL'," +
                "retention_class TEXT NOT NULL DEFAULT 'EPISODIC_90_DAY'," +
                "expires_at INTEGER DEFAULT 0," +
                "processing_state TEXT NOT NULL DEFAULT 'CAPTURED'," +
                "metadata_json TEXT," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_evidence_time ON v4_evidence(occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_evidence_source ON v4_evidence(source_package,occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_evidence_type ON v4_evidence(source_type,occurred_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_evidence_expiry ON v4_evidence(expires_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_evidence_hash ON v4_evidence(content_hash)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_evidence_analysis(" +
                "id TEXT PRIMARY KEY," +
                "evidence_id TEXT NOT NULL," +
                "analysis_kind TEXT NOT NULL," +
                "engine TEXT NOT NULL," +
                "version TEXT NOT NULL," +
                "output_text TEXT," +
                "output_json TEXT," +
                "content_hash TEXT," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(evidence_id,analysis_kind,engine,version,content_hash))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_analysis_evidence ON v4_evidence_analysis(evidence_id,analysis_kind,created_at DESC)");
    }

    private static void createEpisodes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_episodes(" +
                "id TEXT PRIMARY KEY," +
                "identity_key TEXT NOT NULL UNIQUE," +
                "kind TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'OPEN'," +
                "primary_source_package TEXT," +
                "durable_context_key TEXT," +
                "started_at INTEGER NOT NULL," +
                "ended_at INTEGER DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_episode_time ON v4_episodes(started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_episode_state ON v4_episodes(state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_episode_source ON v4_episodes(primary_source_package,started_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_episode_evidence(" +
                "episode_id TEXT NOT NULL," +
                "evidence_id TEXT NOT NULL," +
                "role TEXT NOT NULL DEFAULT 'member'," +
                "is_primary INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL," +
                "PRIMARY KEY(episode_id,evidence_id,role))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_episode_evidence_reverse ON v4_episode_evidence(evidence_id,episode_id)");
    }

    private static void createMemories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_memories(" +
                "id TEXT PRIMARY KEY," +
                "identity_key TEXT NOT NULL UNIQUE," +
                "kind TEXT NOT NULL," +
                "title TEXT," +
                "body TEXT NOT NULL," +
                "episode_id TEXT," +
                "source_package TEXT," +
                "started_at INTEGER NOT NULL," +
                "ended_at INTEGER DEFAULT 0," +
                "importance REAL NOT NULL DEFAULT 0," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "retention_class TEXT NOT NULL DEFAULT 'EPISODIC_90_DAY'," +
                "expires_at INTEGER DEFAULT 0," +
                "state TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_memory_time ON v4_memories(started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_memory_episode ON v4_memories(episode_id,started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_memory_source ON v4_memories(source_package,started_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_memory_retention ON v4_memories(expires_at,pinned,state)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_memory_evidence(" +
                "memory_id TEXT NOT NULL," +
                "evidence_id TEXT NOT NULL," +
                "role TEXT NOT NULL DEFAULT 'supports'," +
                "ordinal INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "PRIMARY KEY(memory_id,evidence_id,role))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_memory_evidence_reverse ON v4_memory_evidence(evidence_id,memory_id)");
    }

    private static void createWorlds(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_worlds(" +
                "id TEXT PRIMARY KEY," +
                "seed_key TEXT NOT NULL," +
                "canonical_name TEXT NOT NULL," +
                "type_hint TEXT NOT NULL," +
                "maturity TEXT NOT NULL DEFAULT 'EMERGING'," +
                "summary TEXT," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "merged_into_world_id TEXT," +
                "created_at INTEGER NOT NULL," +
                "last_active_at INTEGER NOT NULL," +
                "archived_at INTEGER DEFAULT 0," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_name ON v4_worlds(canonical_name,type_hint)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_activity ON v4_worlds(status,maturity,last_active_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_seed ON v4_worlds(seed_key)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_world_aliases(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world_id TEXT NOT NULL," +
                "alias TEXT NOT NULL," +
                "normalized_alias TEXT NOT NULL," +
                "source TEXT," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "user_confirmed INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(world_id,normalized_alias,source))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_alias_lookup ON v4_world_aliases(normalized_alias,world_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_world_identity_claims(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world_id TEXT NOT NULL," +
                "claim_type TEXT NOT NULL," +
                "claim_value TEXT NOT NULL," +
                "normalized_value TEXT NOT NULL," +
                "strength TEXT NOT NULL," +
                "user_confirmed INTEGER NOT NULL DEFAULT 0," +
                "evidence_id TEXT," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(world_id,claim_type,normalized_value))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_claim_lookup ON v4_world_identity_claims(claim_type,normalized_value,strength)");

        db.execSQL("CREATE TABLE IF NOT EXISTS v4_world_merges(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "child_world_id TEXT NOT NULL," +
                "parent_world_id TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "reason TEXT," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "user_confirmed INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "reverted_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_merge_child ON v4_world_merges(child_world_id,state,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_world_merge_parent ON v4_world_merges(parent_world_id,state,created_at DESC)");
    }

    private static void createFacts(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_facts(" +
                "id TEXT PRIMARY KEY," +
                "slot_key TEXT NOT NULL," +
                "version_key TEXT NOT NULL UNIQUE," +
                "subject_world_id TEXT," +
                "predicate TEXT NOT NULL," +
                "value TEXT NOT NULL," +
                "grounding TEXT NOT NULL," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "valid_from INTEGER DEFAULT 0," +
                "valid_until INTEGER DEFAULT 0," +
                "supersedes_fact_id TEXT," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_fact_slot ON v4_facts(slot_key,status,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_fact_subject ON v4_facts(subject_world_id,predicate,status)");
    }

    private static void createRelations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_relations(" +
                "id TEXT PRIMARY KEY," +
                "identity_key TEXT NOT NULL UNIQUE," +
                "source_type TEXT NOT NULL," +
                "source_id TEXT NOT NULL," +
                "target_type TEXT NOT NULL," +
                "target_id TEXT NOT NULL," +
                "relation_type TEXT NOT NULL," +
                "grounding TEXT NOT NULL," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "state TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_relation_source ON v4_relations(source_type,source_id,relation_type,state)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_relation_target ON v4_relations(target_type,target_id,relation_type,state)");
    }

    private static void createSituations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_situations(" +
                "id TEXT PRIMARY KEY," +
                "identity_key TEXT NOT NULL UNIQUE," +
                "kind TEXT NOT NULL," +
                "state TEXT NOT NULL DEFAULT 'DETECTED'," +
                "headline TEXT NOT NULL," +
                "explanation TEXT," +
                "primary_world_id TEXT," +
                "semantic_anchor TEXT NOT NULL," +
                "occurrence_key TEXT," +
                "relevant_from INTEGER DEFAULT 0," +
                "relevant_until INTEGER DEFAULT 0," +
                "attention_score REAL NOT NULL DEFAULT 0," +
                "interruption_score REAL NOT NULL DEFAULT 0," +
                "confidence REAL NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "last_evaluated_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "resolved_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_situation_state ON v4_situations(state,attention_score DESC,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_situation_world ON v4_situations(primary_world_id,state,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_situation_relevance ON v4_situations(relevant_from,relevant_until,state)");
    }

    private static void createActions(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_action_proposals(" +
                "id TEXT PRIMARY KEY," +
                "situation_id TEXT," +
                "world_id TEXT," +
                "action_type TEXT NOT NULL," +
                "label TEXT NOT NULL," +
                "risk TEXT NOT NULL," +
                "payload_json TEXT," +
                "state TEXT NOT NULL DEFAULT 'PROPOSED'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_action_situation ON v4_action_proposals(situation_id,state,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_action_world ON v4_action_proposals(world_id,state,created_at DESC)");
    }

    private static void createProvenance(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_provenance(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "object_type TEXT NOT NULL," +
                "object_id TEXT NOT NULL," +
                "source_type TEXT NOT NULL," +
                "source_id TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "confidence REAL NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(object_type,object_id,source_type,source_id,role))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_provenance_object ON v4_provenance(object_type,object_id,role)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_provenance_source ON v4_provenance(source_type,source_id,object_type)");
    }

    private static void createLegacyBridge(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS v4_legacy_map(" +
                "legacy_table TEXT NOT NULL," +
                "legacy_id TEXT NOT NULL," +
                "object_type TEXT NOT NULL," +
                "object_id TEXT NOT NULL," +
                "migration_state TEXT NOT NULL DEFAULT 'MAPPED'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(legacy_table,legacy_id,object_type))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_v4_legacy_object ON v4_legacy_map(object_type,object_id)");
    }
}
