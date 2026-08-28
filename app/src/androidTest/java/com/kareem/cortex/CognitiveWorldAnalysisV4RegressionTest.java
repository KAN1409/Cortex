package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveWorldAnalysisV4RegressionTest {

    @Test public void analysisPersonEntityIsGroundedWeakAndDeferred() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_1", "mem_1");
            analysis(db, "an_1", "ev_1",
                    "{\"entities\":[{\"kind\":\"PERSON\",\"value\":\"Dr Mona\",\"confidence\":0.88}]}");

            List<CognitiveWorldResolverV4.Candidate> xs =
                    CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_1");
            assertEquals(1, xs.size());
            CognitiveWorldResolverV4.Candidate c = xs.get(0);
            assertEquals(CognitiveDomainV4.WorldTypeHint.PERSON, c.typeHint);
            assertEquals("Dr Mona", c.canonicalName);
            assertEquals("ev_1", c.evidenceIds.get(0));
            assertEquals("mem_1", c.memoryIds.get(0));
            assertEquals(CognitiveIdentityV4.ClaimType.MODEL_ALIAS, c.claims.get(0).type);
            assertEquals(CognitiveIdentityV4.ClaimStrength.WEAK, c.claims.get(0).strength);
            assertFalse(c.typeMaterializationApproved);
            assertFalse(CognitiveWorldResolverV4.canMaterializeWithoutReview(c));
        } finally { db.close(); }
    }

    @Test public void analysisProjectEntityKeepsTypeButCannotMaterializeWithoutIdentity() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_2", "mem_2");
            analysis(db, "an_2", "ev_2",
                    "{\"entities\":[{\"kind\":\"PROJECT\",\"value\":\"Cortex\",\"confidence\":0.91}]}");

            CognitiveWorldResolverV4.Candidate c =
                    CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_2").get(0);
            assertEquals(CognitiveDomainV4.WorldTypeHint.PROJECT, c.typeHint);
            assertTrue(c.typeMaterializationApproved);
            assertFalse(CognitiveWorldResolverV4.canMaterializeWithoutReview(c));
        } finally { db.close(); }
    }

    @Test public void analysisGenericSystemLabelIsRejected() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_3", "mem_3");
            analysis(db, "an_3", "ev_3",
                    "{\"entities\":[{\"kind\":\"PERSON\",\"value\":\"Backup in progress\",\"confidence\":0.99}]}");
            assertTrue(CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_3").isEmpty());
        } finally { db.close(); }
    }

    private static void seed(SQLiteDatabase db, String evidenceId, String memoryId) {
        long now = System.currentTimeMillis();
        ContentValues e = new ContentValues();
        e.put("id", evidenceId); e.put("identity_key", "identity_" + evidenceId); e.put("source_type", "NOTE");
        e.put("source_package", "test"); e.put("occurred_at", now); e.put("captured_at", now); e.put("original_text", "text");
        e.put("normalized_text", "text"); e.put("content_hash", Fingerprint.text(evidenceId)); e.put("sensitivity", "NORMAL");
        e.put("retention_class", "EPISODIC_90_DAY"); e.put("expires_at", now + 86400000L); e.put("processing_state", "READY");
        e.put("metadata_json", "{}"); e.put("created_at", now); e.put("updated_at", now);
        assertTrue(db.insert("v4_evidence", null, e) > 0);

        ContentValues m = new ContentValues();
        m.put("id", memoryId); m.put("identity_key", "identity_" + memoryId); m.put("kind", "NOTE"); m.put("title", "");
        m.put("body", "text"); m.put("source_package", "test"); m.put("started_at", now); m.put("ended_at", 0); m.put("importance", .5);
        m.put("pinned", 0); m.put("retention_class", "EPISODIC_90_DAY"); m.put("expires_at", now + 86400000L); m.put("state", "ACTIVE");
        m.put("created_at", now); m.put("updated_at", now);
        assertTrue(db.insert("v4_memories", null, m) > 0);

        ContentValues link = new ContentValues();
        link.put("memory_id", memoryId); link.put("evidence_id", evidenceId); link.put("role", "supports"); link.put("ordinal", 0); link.put("created_at", now);
        assertTrue(db.insert("v4_memory_evidence", null, link) > 0);
    }

    private static void analysis(SQLiteDatabase db, String id, String evidenceId, String json) {
        ContentValues a = new ContentValues();
        a.put("id", id); a.put("evidence_id", evidenceId); a.put("analysis_kind", "LEGACY_ANALYSIS");
        a.put("engine", "local_rules"); a.put("version", "1"); a.put("output_text", ""); a.put("output_json", json);
        a.put("content_hash", Fingerprint.text(json)); a.put("created_at", System.currentTimeMillis());
        assertTrue(db.insert("v4_evidence_analysis", null, a) > 0);
    }
}
