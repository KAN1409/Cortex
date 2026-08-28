package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveWorldSafetyV4RegressionTest {

    @Test public void personHintCreatesWeakCandidateButCannotMaterializeAutomatically() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_hint", "mem_hint", "com.whatsapp", "{\"person_hint\":\"Mona\",\"communication\":true}");
            List<CognitiveWorldResolverV4.Candidate> xs = CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_hint");
            assertEquals(1, xs.size());
            CognitiveWorldResolverV4.Candidate c = xs.get(0);
            assertEquals("Mona", c.canonicalName);
            assertEquals(CognitiveDomainV4.WorldTypeHint.PERSON, c.typeHint);
            assertEquals(1, c.claims.size());
            assertEquals(CognitiveIdentityV4.ClaimType.EXACT_NAME, c.claims.get(0).type);
            assertEquals(CognitiveIdentityV4.ClaimStrength.WEAK, c.claims.get(0).strength);
            assertFalse(CognitiveWorldResolverV4.canMaterializeWithoutReview(c));
        } finally { db.close(); }
    }

    @Test public void groupConversationTitleDoesNotBecomePersonWithoutActualParticipant() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_group", "mem_group", "com.whatsapp", "{\"person_hint\":\"Family Group\",\"group_conversation\":true}");
            assertTrue(CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_group").isEmpty());
        } finally { db.close(); }
    }

    @Test public void participantKeyCreatesSourceScopedDurableIdentity() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            seed(db, "ev_sender", "mem_sender", "com.whatsapp", "{\"participant_name\":\"Mona\",\"participant_key\":\"sender-42\",\"group_conversation\":true}");
            List<CognitiveWorldResolverV4.Candidate> xs = CognitiveWorldCandidateExtractorV4.fromMemory(db, "mem_sender");
            assertEquals(1, xs.size());
            CognitiveWorldResolverV4.Candidate c = xs.get(0);
            assertEquals(2, c.claims.size());
            assertEquals(CognitiveIdentityV4.ClaimType.ACCOUNT_ID, c.claims.get(1).type);
            assertEquals("com.whatsapp|key:sender-42", c.claims.get(1).normalizedValue);
            assertEquals(CognitiveIdentityV4.ClaimStrength.STRONG, c.claims.get(1).strength);
            assertTrue(CognitiveWorldResolverV4.canMaterializeWithoutReview(c));
        } finally { db.close(); }
    }

    @Test public void sameParticipantKeyInDifferentAppsDoesNotCollide() {
        SQLiteDatabase a = SQLiteDatabase.create(null);
        SQLiteDatabase b = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(a); CognitiveSchemaV4.ensure(b);
            seed(a, "ev_a", "mem_a", "com.whatsapp", "{\"participant_name\":\"Mona\",\"participant_key\":\"42\"}");
            seed(b, "ev_b", "mem_b", "org.telegram.messenger", "{\"participant_name\":\"Mona\",\"participant_key\":\"42\"}");
            CognitiveWorldResolverV4.Candidate ca = CognitiveWorldCandidateExtractorV4.fromMemory(a, "mem_a").get(0);
            CognitiveWorldResolverV4.Candidate cb = CognitiveWorldCandidateExtractorV4.fromMemory(b, "mem_b").get(0);
            CognitiveIdentityV4.Match match = CognitiveIdentityV4.matchWorlds(
                    ca.typeHint, ca.claims, cb.typeHint, cb.claims);
            assertFalse(match.canAutoMerge());
        } finally { a.close(); b.close(); }
    }

    private static void seed(SQLiteDatabase db, String evidenceId, String memoryId, String source, String metadata) {
        long now = System.currentTimeMillis();
        ContentValues e = new ContentValues();
        e.put("id", evidenceId); e.put("identity_key", "identity_" + evidenceId); e.put("source_type", "NOTIFICATION");
        e.put("source_package", source); e.put("occurred_at", now); e.put("captured_at", now); e.put("original_text", "message");
        e.put("normalized_text", "message"); e.put("content_hash", Fingerprint.text(evidenceId)); e.put("sensitivity", "NORMAL");
        e.put("retention_class", "EPISODIC_90_DAY"); e.put("expires_at", now + 86400000L); e.put("processing_state", "READY");
        e.put("metadata_json", metadata); e.put("created_at", now); e.put("updated_at", now);
        assertTrue(db.insert("v4_evidence", null, e) > 0);

        ContentValues m = new ContentValues();
        m.put("id", memoryId); m.put("identity_key", "identity_" + memoryId); m.put("kind", "MOMENT"); m.put("title", "");
        m.put("body", "message"); m.put("source_package", source); m.put("started_at", now); m.put("ended_at", 0); m.put("importance", .5);
        m.put("pinned", 0); m.put("retention_class", "EPISODIC_90_DAY"); m.put("expires_at", now + 86400000L); m.put("state", "ACTIVE");
        m.put("created_at", now); m.put("updated_at", now);
        assertTrue(db.insert("v4_memories", null, m) > 0);

        ContentValues link = new ContentValues();
        link.put("memory_id", memoryId); link.put("evidence_id", evidenceId); link.put("role", "supports"); link.put("ordinal", 0); link.put("created_at", now);
        assertTrue(db.insert("v4_memory_evidence", null, link) > 0);
    }
}
