package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;

/**
 * Read-only migration/equivalence diagnostics for the V4 Memory projection.
 *
 * <p>No repair or deletion happens here. This class exists so a future UI cut-over is based on
 * measured coverage and provenance integrity rather than on the presence of V4 tables.</p>
 */
public final class CognitiveMemoryEquivalenceV4 {
    private CognitiveMemoryEquivalenceV4() {}

    public static final class Report {
        public final int eligibleRawSignals;
        public final int mappedRawEvidence;
        public final int eligibleThreads;
        public final int mappedEpisodes;
        public final int eligibleKnowledgeItems;
        public final int mappedMemories;
        public final int memoryWithoutEvidence;
        public final int brokenMemoryEvidenceLinks;
        public final int brokenLegacyMappings;
        public final int contentMismatches;

        Report(
                int eligibleRawSignals,
                int mappedRawEvidence,
                int eligibleThreads,
                int mappedEpisodes,
                int eligibleKnowledgeItems,
                int mappedMemories,
                int memoryWithoutEvidence,
                int brokenMemoryEvidenceLinks,
                int brokenLegacyMappings,
                int contentMismatches) {
            this.eligibleRawSignals = eligibleRawSignals;
            this.mappedRawEvidence = mappedRawEvidence;
            this.eligibleThreads = eligibleThreads;
            this.mappedEpisodes = mappedEpisodes;
            this.eligibleKnowledgeItems = eligibleKnowledgeItems;
            this.mappedMemories = mappedMemories;
            this.memoryWithoutEvidence = memoryWithoutEvidence;
            this.brokenMemoryEvidenceLinks = brokenMemoryEvidenceLinks;
            this.brokenLegacyMappings = brokenLegacyMappings;
            this.contentMismatches = contentMismatches;
        }

        public int pendingRawEvidence() { return Math.max(0, eligibleRawSignals - mappedRawEvidence); }
        public int pendingEpisodes() { return Math.max(0, eligibleThreads - mappedEpisodes); }
        public int pendingMemories() { return Math.max(0, eligibleKnowledgeItems - mappedMemories); }

        public boolean integrityClean() {
            return memoryWithoutEvidence == 0
                    && brokenMemoryEvidenceLinks == 0
                    && brokenLegacyMappings == 0
                    && contentMismatches == 0;
        }

        public boolean migrationComplete() {
            return pendingRawEvidence() == 0 && pendingEpisodes() == 0 && pendingMemories() == 0;
        }

        /** A cut-over requires both complete coverage and clean canonical provenance. */
        public boolean cutoverReady() { return migrationComplete() && integrityClean(); }

        public String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("eligibleRawSignals", eligibleRawSignals);
                o.put("mappedRawEvidence", mappedRawEvidence);
                o.put("pendingRawEvidence", pendingRawEvidence());
                o.put("eligibleThreads", eligibleThreads);
                o.put("mappedEpisodes", mappedEpisodes);
                o.put("pendingEpisodes", pendingEpisodes());
                o.put("eligibleKnowledgeItems", eligibleKnowledgeItems);
                o.put("mappedMemories", mappedMemories);
                o.put("pendingMemories", pendingMemories());
                o.put("memoryWithoutEvidence", memoryWithoutEvidence);
                o.put("brokenMemoryEvidenceLinks", brokenMemoryEvidenceLinks);
                o.put("brokenLegacyMappings", brokenLegacyMappings);
                o.put("contentMismatches", contentMismatches);
                o.put("integrityClean", integrityClean());
                o.put("migrationComplete", migrationComplete());
                o.put("cutoverReady", cutoverReady());
                return o.toString();
            } catch (Throwable ignored) {
                return "{}";
            }
        }
    }

    public static Report evaluate(VaultDb db) {
        if (db == null) throw new IllegalArgumentException("db required");
        CognitiveStoreV4.ensure(db);
        return evaluate(db.getReadableDatabase(), System.currentTimeMillis());
    }

    static Report evaluate(SQLiteDatabase db, long now) {
        if (db == null) throw new IllegalArgumentException("db required");
        long cutoff = now - CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS;

        int eligibleRaw = scalar(db,
                "SELECT COUNT(*) FROM raw_signals WHERE occurred_at>=?",
                String.valueOf(cutoff));
        int mappedRaw = scalar(db,
                "SELECT COUNT(*) FROM v4_legacy_map m JOIN raw_signals r ON CAST(r.id AS TEXT)=m.legacy_id " +
                        "WHERE m.legacy_table='raw_signals' AND m.object_type='EVIDENCE' AND r.occurred_at>=?",
                String.valueOf(cutoff));

        // A legacy thread is only an Episode migration candidate when it still owns at least one
        // raw signal inside the V4 episodic window. Empty/stale thread shells cannot produce a
        // grounded Episode and therefore must not block the cut-over gate.
        int eligibleThreads = scalar(db,
                "SELECT COUNT(*) FROM signal_threads t WHERE t.last_event_at>=? " +
                        "AND EXISTS (SELECT 1 FROM raw_signals r WHERE r.thread_id=t.id AND r.occurred_at>=?)",
                String.valueOf(cutoff),
                String.valueOf(cutoff));
        int mappedEpisodes = scalar(db,
                "SELECT COUNT(*) FROM v4_legacy_map m JOIN signal_threads t ON CAST(t.id AS TEXT)=m.legacy_id " +
                        "WHERE m.legacy_table='signal_threads' AND m.object_type='EPISODE' AND t.last_event_at>=? " +
                        "AND EXISTS (SELECT 1 FROM raw_signals r WHERE r.thread_id=t.id AND r.occurred_at>=?)",
                String.valueOf(cutoff),
                String.valueOf(cutoff));

        int eligibleItems = scalar(db,
                "SELECT COUNT(*) FROM knowledge_items WHERE created_at>=?",
                String.valueOf(cutoff));
        int mappedMemories = scalar(db,
                "SELECT COUNT(*) FROM v4_legacy_map m JOIN knowledge_items k ON CAST(k.id AS TEXT)=m.legacy_id " +
                        "WHERE m.legacy_table='knowledge_items' AND m.object_type='MEMORY' AND k.created_at>=?",
                String.valueOf(cutoff));

        int memoryWithoutEvidence = scalar(db,
                "SELECT COUNT(*) FROM v4_memories m WHERE m.state='ACTIVE' " +
                        "AND NOT EXISTS (SELECT 1 FROM v4_memory_evidence me WHERE me.memory_id=m.id)");
        int brokenMemoryEvidence = scalar(db,
                "SELECT COUNT(*) FROM v4_memory_evidence me " +
                        "LEFT JOIN v4_memories m ON m.id=me.memory_id " +
                        "LEFT JOIN v4_evidence e ON e.id=me.evidence_id " +
                        "WHERE m.id IS NULL OR e.id IS NULL");
        int brokenMappings = scalar(db,
                "SELECT COUNT(*) FROM v4_legacy_map lm WHERE " +
                        "(lm.object_type='EVIDENCE' AND NOT EXISTS (SELECT 1 FROM v4_evidence e WHERE e.id=lm.object_id)) OR " +
                        "(lm.object_type='EPISODE' AND NOT EXISTS (SELECT 1 FROM v4_episodes ep WHERE ep.id=lm.object_id)) OR " +
                        "(lm.object_type='MEMORY' AND NOT EXISTS (SELECT 1 FROM v4_memories mm WHERE mm.id=lm.object_id)) OR " +
                        "(lm.object_type='WORLD' AND NOT EXISTS (SELECT 1 FROM v4_worlds w WHERE w.id=lm.object_id)) OR " +
                        "(lm.object_type='FACT' AND NOT EXISTS (SELECT 1 FROM v4_facts f WHERE f.id=lm.object_id)) OR " +
                        "(lm.object_type='SITUATION' AND NOT EXISTS (SELECT 1 FROM v4_situations s WHERE s.id=lm.object_id))");

        // For every migrated legacy knowledge item with user-visible source text, at least one of
        // Memory body/title, original Evidence, or additive analysis output must still contain it.
        int contentMismatches = scalar(db,
                "SELECT COUNT(*) FROM knowledge_items k " +
                        "JOIN v4_legacy_map lm ON lm.legacy_table='knowledge_items' " +
                        "AND lm.legacy_id=CAST(k.id AS TEXT) AND lm.object_type='MEMORY' " +
                        "JOIN v4_memories m ON m.id=lm.object_id " +
                        "WHERE k.created_at>=? AND LENGTH(TRIM(COALESCE(k.raw_text,k.extracted_text,k.summary,k.title,'')))>0 " +
                        "AND NOT EXISTS (SELECT 1 FROM v4_memory_evidence me JOIN v4_evidence e ON e.id=me.evidence_id " +
                        "WHERE me.memory_id=m.id AND (" +
                        "COALESCE(m.body,'')<>'' OR COALESCE(m.title,'')<>'' OR COALESCE(e.original_text,'')<>'' OR " +
                        "EXISTS (SELECT 1 FROM v4_evidence_analysis a WHERE a.evidence_id=e.id " +
                        "AND (COALESCE(a.output_text,'')<>'' OR COALESCE(a.output_json,'')<>''))))",
                String.valueOf(cutoff));

        return new Report(
                eligibleRaw,
                mappedRaw,
                eligibleThreads,
                mappedEpisodes,
                eligibleItems,
                mappedMemories,
                memoryWithoutEvidence,
                brokenMemoryEvidence,
                brokenMappings,
                contentMismatches);
    }

    private static int scalar(SQLiteDatabase db, String sql, String... args) {
        Cursor c = db.rawQuery(sql, args == null || args.length == 0 ? null : args);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }
}
