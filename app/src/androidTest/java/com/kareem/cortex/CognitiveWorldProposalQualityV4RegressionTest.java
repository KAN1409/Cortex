package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveWorldProposalQualityV4RegressionTest {

    @Test public void rejectsObservedNoiseFromRealHistoricalShape() {
        assertFalse(CognitiveWorldProposalQualityV4.inspect(CognitiveDomainV4.WorldTypeHint.PERSON, "ال").accepted);
        assertFalse(CognitiveWorldProposalQualityV4.inspect(CognitiveDomainV4.WorldTypeHint.PERSON, "Self-test").accepted);
        assertFalse(CognitiveWorldProposalQualityV4.inspect(CognitiveDomainV4.WorldTypeHint.PERSON, "من خمس دقايق كنت").accepted);
    }

    @Test public void stripsGenericPhoneColumnSuffixButKeepsRawAlias() {
        CognitiveWorldProposalQualityV4.Result q = CognitiveWorldProposalQualityV4.inspect(
                CognitiveDomainV4.WorldTypeHint.PERSON, "Ahmed Shoeib Phone");
        assertTrue(q.accepted);
        assertEquals("Ahmed Shoeib", q.canonicalName);

        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev1", "mem1", "Ahmed Shoeib Phone", .88);
            List<CognitiveWorldResolverV4.Candidate> xs = CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem1");
            assertEquals(1, xs.size());
            assertEquals("Ahmed Shoeib", xs.get(0).canonicalName);
            assertTrue(xs.get(0).aliases.contains("Ahmed Shoeib Phone"));
            assertFalse(CognitiveWorldResolverV4.canMaterializeWithoutReview(xs.get(0)));
        } finally { db.close(); }
    }

    @Test public void corroborationOnlyMakesWeakProposalReviewEligible() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev1", "mem1", "Ahmed Shoeib Phone", .88);
            seed(db, "ev2", "mem2", "Ahmed Shoeib Phone", .88);
            seed(db, "ev3", "mem3", "Ragaey", .88);
            seed(db, "ev4", "mem4", "Ragaey", .88);
            seed(db, "ev5", "mem5", "Ragaey", .88);

            List<CognitiveWorldProposalConsolidatorV4.Proposal> ps = CognitiveWorldProposalConsolidatorV4.evaluate(db);
            CognitiveWorldProposalConsolidatorV4.Proposal ahmed = find(ps, "Ahmed Shoeib");
            CognitiveWorldProposalConsolidatorV4.Proposal ragaey = find(ps, "Ragaey");
            assertNotNull(ahmed); assertTrue(ahmed.reviewEligible); assertEquals(2, ahmed.distinctEvidence);
            assertNotNull(ragaey); assertTrue(ragaey.reviewEligible); assertEquals(3, ragaey.distinctEvidence);
        } finally { db.close(); }
    }

    @Test public void singleTokenPersonNeedsThreeIndependentEvidenceRows() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev1", "mem1", "Mona", .88);
            seed(db, "ev2", "mem2", "Mona", .88);
            CognitiveWorldProposalConsolidatorV4.Proposal p = find(
                    CognitiveWorldProposalConsolidatorV4.evaluate(db), "Mona");
            assertNotNull(p);
            assertFalse(p.reviewEligible);
        } finally { db.close(); }
    }

    private static CognitiveWorldProposalConsolidatorV4.Proposal find(
            List<CognitiveWorldProposalConsolidatorV4.Proposal> xs, String name) {
        for (CognitiveWorldProposalConsolidatorV4.Proposal x : xs) if (name.equals(x.canonicalName)) return x;
        return null;
    }

    private static void seed(SQLiteDatabase db, String evidenceId, String memoryId, String value, double confidence) {
        long now = System.currentTimeMillis();
        ContentValues e = new ContentValues();
        e.put("id", evidenceId); e.put("identity_key", "id_" + evidenceId); e.put("source_type", "NOTE");
        e.put("source_package", "test"); e.put("occurred_at", now); e.put("captured_at", now); e.put("original_text", value);
        e.put("normalized_text", value.toLowerCase()); e.put("content_hash", Fingerprint.text(evidenceId)); e.put("sensitivity", "NORMAL");
        e.put("retention_class", "EPISODIC_90_DAY"); e.put("expires_at", now + 86400000L); e.put("processing_state", "READY");
        e.put("metadata_json", "{}"); e.put("created_at", now); e.put("updated_at", now);
        assertTrue(db.insert("v4_evidence", null, e) > 0);

        ContentValues m = new ContentValues();
        m.put("id", memoryId); m.put("identity_key", "id_" + memoryId); m.put("kind", "MOMENT"); m.put("title", "");
        m.put("body", value); m.put("source_package", "test"); m.put("started_at", now); m.put("ended_at", 0); m.put("importance", .5);
        m.put("pinned", 0); m.put("retention_class", "EPISODIC_90_DAY"); m.put("expires_at", now + 86400000L); m.put("state", "ACTIVE");
        m.put("created_at", now); m.put("updated_at", now);
        assertTrue(db.insert("v4_memories", null, m) > 0);

        ContentValues link = new ContentValues();
        link.put("memory_id", memoryId); link.put("evidence_id", evidenceId); link.put("role", "supports"); link.put("ordinal", 0); link.put("created_at", now);
        assertTrue(db.insert("v4_memory_evidence", null, link) > 0);

        ContentValues a = new ContentValues();
        a.put("id", "an_" + evidenceId); a.put("evidence_id", evidenceId); a.put("analysis_kind", "LEGACY_ANALYSIS");
        a.put("engine", "local_rules"); a.put("version", "1");
        a.put("output_json", "{\"entities\":[{\"kind\":\"PERSON\",\"value\":\"" + value.replace("\"", "\\\"") + "\",\"confidence\":" + confidence + "}]}");
        a.put("content_hash", Fingerprint.text(evidenceId + value)); a.put("created_at", now);
        assertTrue(db.insert("v4_evidence_analysis", null, a) > 0);
    }
}
