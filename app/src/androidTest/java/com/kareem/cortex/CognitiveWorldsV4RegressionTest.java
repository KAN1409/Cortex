package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveWorldsV4RegressionTest {

    @Test public void migratedStructuredEvidenceCreatesGroundedPersonCandidate() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            seedMemory(db, "ev_person", "mem_person", "hello",
                    "{\"migrated_from\":\"raw_signals\",\"legacy_metadata\":{\"sender_name\":\"Mona\",\"contact_id\":\"contact-42\"}}", now);
            List<CognitiveWorldResolverV4.Candidate> xs = CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_person");
            assertEquals(1, xs.size());
            CognitiveWorldResolverV4.Candidate c = xs.get(0);
            assertEquals("Mona", c.canonicalName);
            assertEquals(CognitiveDomainV4.WorldTypeHint.PERSON, c.typeHint);
            assertEquals(Collections.singletonList("ev_person"), c.evidenceIds);
            assertEquals(Collections.singletonList("mem_person"), c.memoryIds);
            assertEquals(2, c.claims.size());
            assertEquals(CognitiveIdentityV4.ClaimType.EXACT_NAME, c.claims.get(0).type);
            assertEquals(CognitiveIdentityV4.ClaimType.CONTACT_ID, c.claims.get(1).type);
            assertEquals(CognitiveIdentityV4.ClaimStrength.STRONG, c.claims.get(1).strength);
        } finally { db.close(); }
    }

    @Test public void freeTextAloneDoesNotInventWorlds() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seedMemory(db, "ev_text", "mem_text", "Met Mona at Project Atlas in Cairo", "{}", System.currentTimeMillis());
            assertTrue(CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_text").isEmpty());
        } finally { db.close(); }
    }

    @Test public void sameNameAloneNeverAuthorizesAutoMerge() {
        CognitiveIdentityV4.IdentityClaim a = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.EXACT_NAME, "Ahmed", CognitiveIdentityV4.ClaimStrength.WEAK, false, "ev_a");
        CognitiveIdentityV4.IdentityClaim b = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.EXACT_NAME, "Ahmed", CognitiveIdentityV4.ClaimStrength.WEAK, false, "ev_b");
        CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                CognitiveDomainV4.WorldTypeHint.PERSON, Collections.singletonList(a),
                CognitiveDomainV4.WorldTypeHint.PERSON, Collections.singletonList(b));
        assertEquals(CognitiveIdentityV4.MatchDecision.POSSIBLE, match.decision);
        assertFalse(match.canAutoMerge());
    }

    @Test public void durablePhoneIdentityCanAutoMerge() {
        CognitiveIdentityV4.IdentityClaim a = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.PHONE_E164, "+201001112222", CognitiveIdentityV4.ClaimStrength.STRONG, false, "ev_a");
        CognitiveIdentityV4.IdentityClaim b = new CognitiveIdentityV4.IdentityClaim(
                CognitiveIdentityV4.ClaimType.PHONE_E164, "+20 100 111 2222", CognitiveIdentityV4.ClaimStrength.STRONG, false, "ev_b");
        CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                CognitiveDomainV4.WorldTypeHint.PERSON, Collections.singletonList(a),
                CognitiveDomainV4.WorldTypeHint.PERSON, Collections.singletonList(b));
        assertEquals(CognitiveIdentityV4.MatchDecision.SAME, match.decision);
        assertTrue(match.canAutoMerge());
    }

    @Test public void carrierAppPackageDoesNotBecomeOrganizationIdentity() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            seedMemory(db, "ev_org", "mem_org", "bank message",
                    "{\"organization_name\":\"CIB\"}", now);
            List<CognitiveWorldResolverV4.Candidate> xs = CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_org");
            assertEquals(1, xs.size());
            CognitiveWorldResolverV4.Candidate c = xs.get(0);
            assertEquals(CognitiveDomainV4.WorldTypeHint.ORGANIZATION, c.typeHint);
            assertEquals(1, c.claims.size());
            assertEquals(CognitiveIdentityV4.ClaimType.EXACT_NAME, c.claims.get(0).type);
            assertEquals(CognitiveIdentityV4.ClaimStrength.WEAK, c.claims.get(0).strength);
        } finally { db.close(); }
    }

    @Test public void dryRunIsReadOnlyAndSeparatesDurableFromWeakCandidates() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            seedMemory(db, "ev_person", "mem_person", "hello",
                    "{\"sender_name\":\"Mona\",\"contact_id\":\"contact-42\"}", now);
            seedMemory(db, "ev_topic", "mem_topic", "camera research",
                    "{\"topic\":\"Mirrorless Cameras\"}", now + 1);

            assertEquals(0, count(db, "v4_worlds"));
            assertEquals(0, count(db, "v4_relations"));
            assertEquals(0, count(db, "v4_world_identity_claims"));

            CognitiveWorldDryRunV4.Report report = CognitiveWorldDryRunV4.evaluate(db);
            assertEquals(2, report.memoriesScanned);
            assertEquals(2, report.memoriesWithCandidates);
            assertEquals(2, report.totalCandidates);
            assertEquals(1, report.durableIdentityCandidates);
            assertEquals(1, report.weakOnlyCandidates);
            assertEquals(Integer.valueOf(1), report.byType.get(CognitiveDomainV4.WorldTypeHint.PERSON));
            assertEquals(Integer.valueOf(1), report.byType.get(CognitiveDomainV4.WorldTypeHint.TOPIC));

            assertEquals(0, count(db, "v4_worlds"));
            assertEquals(0, count(db, "v4_relations"));
            assertEquals(0, count(db, "v4_world_identity_claims"));
        } finally { db.close(); }
    }

    @Test public void dryRunFlagsSameNameCollisionsWithoutCollapsingIdentity() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            seedMemory(db, "ev_a", "mem_a", "first Ahmed",
                    "{\"sender_name\":\"Ahmed\",\"contact_id\":\"contact-a\"}", now);
            seedMemory(db, "ev_b", "mem_b", "second Ahmed",
                    "{\"sender_name\":\"Ahmed\",\"contact_id\":\"contact-b\"}", now + 1);

            CognitiveWorldDryRunV4.Report report = CognitiveWorldDryRunV4.evaluate(db);
            assertEquals(2, report.totalCandidates);
            assertEquals(2, report.durableIdentityCandidates);
            assertEquals(1, report.sameNameCollisionGroups);
            assertEquals(2, report.sameNameCollisionCandidates);
            assertEquals(0, count(db, "v4_worlds"));
        } finally { db.close(); }
    }

    @Test public void projectionShowsCanonicalActiveWorldAndGroundedCountsOnly() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            seedWorld(db, "wo_parent", "Cortex", "PROJECT", "ACTIVE", null, now);
            seedWorld(db, "wo_child", "Cortex App", "PROJECT", "MERGED", "wo_parent", now - 1);
            alias(db, "wo_parent", "Second Brain", now);
            seedMemory(db, "ev_cortex", "mem_cortex", "Cortex work", "{}", now);
            relation(db, "re_mem", "MEMORY", "mem_cortex", "wo_parent", now);
            relation(db, "re_ev", "EVIDENCE", "ev_cortex", "wo_parent", now);
            ContentValues merge = new ContentValues();
            merge.put("child_world_id", "wo_child"); merge.put("parent_world_id", "wo_parent"); merge.put("state", "ACTIVE");
            merge.put("reason", "test"); merge.put("confidence", 1.0); merge.put("user_confirmed", 1); merge.put("created_at", now); merge.put("reverted_at", 0);
            assertTrue(db.insert("v4_world_merges", null, merge) > 0);

            List<CognitiveWorldProjectionV4.Row> rows = CognitiveWorldProjectionV4.query(db,
                    new CognitiveWorldProjectionV4.Query("Second Brain", CognitiveDomainV4.WorldTypeHint.PROJECT, 20));
            assertEquals(1, rows.size());
            CognitiveWorldProjectionV4.Row row = rows.get(0);
            assertEquals("wo_parent", row.id);
            assertEquals(1, row.aliasCount);
            assertEquals(1, row.memoryCount);
            assertEquals(1, row.evidenceCount);
            assertEquals(1, row.mergedChildCount);
        } finally { db.close(); }
    }

    @Test public void worldSearchTreatsSqlWildcardsLiterally() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seedWorld(db, "wo_plain", "Normal Project", "PROJECT", "ACTIVE", null, System.currentTimeMillis());
            assertEquals(0, CognitiveWorldProjectionV4.query(db,
                    new CognitiveWorldProjectionV4.Query("%", null, 20)).size());
            assertEquals(0, CognitiveWorldProjectionV4.query(db,
                    new CognitiveWorldProjectionV4.Query("_", null, 20)).size());
        } finally { db.close(); }
    }

    private static int count(SQLiteDatabase db, String table) {
        android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private static void seedMemory(SQLiteDatabase db, String evidenceId, String memoryId, String text, String metadata, long now) {
        ContentValues e = new ContentValues();
        e.put("id", evidenceId); e.put("identity_key", "id_" + evidenceId); e.put("source_type", "NOTIFICATION");
        e.put("source_package", "com.test"); e.put("occurred_at", now); e.put("captured_at", now); e.put("original_text", text);
        e.put("normalized_text", CognitiveIdentityV4.normalizeText(text)); e.put("content_hash", Fingerprint.text(text));
        e.put("sensitivity", "NORMAL"); e.put("retention_class", "EPISODIC_90_DAY"); e.put("expires_at", now + 86400000L);
        e.put("processing_state", "READY"); e.put("metadata_json", metadata); e.put("created_at", now); e.put("updated_at", now);
        assertTrue(db.insert("v4_evidence", null, e) > 0);

        ContentValues m = new ContentValues();
        m.put("id", memoryId); m.put("identity_key", "id_" + memoryId); m.put("kind", "MOMENT"); m.put("title", ""); m.put("body", text);
        m.put("source_package", "com.test"); m.put("started_at", now); m.put("ended_at", 0); m.put("importance", .5); m.put("pinned", 0);
        m.put("retention_class", "EPISODIC_90_DAY"); m.put("expires_at", now + 86400000L); m.put("state", "ACTIVE"); m.put("created_at", now); m.put("updated_at", now);
        assertTrue(db.insert("v4_memories", null, m) > 0);

        ContentValues link = new ContentValues();
        link.put("memory_id", memoryId); link.put("evidence_id", evidenceId); link.put("role", "supports"); link.put("ordinal", 0); link.put("created_at", now);
        assertTrue(db.insert("v4_memory_evidence", null, link) > 0);
    }

    private static void seedWorld(SQLiteDatabase db, String id, String name, String type, String status, String mergedInto, long now) {
        ContentValues w = new ContentValues();
        w.put("id", id); w.put("seed_key", "seed_" + id); w.put("canonical_name", name); w.put("type_hint", type); w.put("maturity", "EMERGING");
        w.put("status", status); if (mergedInto != null) w.put("merged_into_world_id", mergedInto); w.put("created_at", now); w.put("last_active_at", now);
        w.put("archived_at", 0); w.put("updated_at", now); assertTrue(db.insert("v4_worlds", null, w) > 0);
    }

    private static void alias(SQLiteDatabase db, String worldId, String value, long now) {
        ContentValues a = new ContentValues();
        a.put("world_id", worldId); a.put("alias", value); a.put("normalized_alias", CognitiveIdentityV4.normalizeText(value)); a.put("source", "test");
        a.put("confidence", 1.0); a.put("user_confirmed", 0); a.put("created_at", now); assertTrue(db.insert("v4_world_aliases", null, a) > 0);
    }

    private static void relation(SQLiteDatabase db, String id, String sourceType, String sourceId, String worldId, long now) {
        ContentValues r = new ContentValues();
        r.put("id", id); r.put("identity_key", "identity_" + id); r.put("source_type", sourceType); r.put("source_id", sourceId);
        r.put("target_type", "WORLD"); r.put("target_id", worldId); r.put("relation_type", "ABOUT"); r.put("grounding", "OBSERVED");
        r.put("confidence", 1.0); r.put("state", "ACTIVE"); r.put("created_at", now); r.put("updated_at", now); assertTrue(db.insert("v4_relations", null, r) > 0);
    }
}
