package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveMemoryEquivalenceV4RegressionTest {

    @Test public void unmappedEligibleLegacyRowsBlockCutover() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            legacyTables(db);
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            insertLegacyRaw(db, 1, now, "hello from notification");
            insertLegacyMemory(db, 11, now, "Proposal", "latest project proposal");

            CognitiveMemoryEquivalenceV4.Report r = CognitiveMemoryEquivalenceV4.evaluate(db, now);
            assertEquals(1, r.pendingRawEvidence());
            assertEquals(1, r.pendingMemories());
            assertFalse(r.migrationComplete());
            assertFalse(r.cutoverReady());
        } finally {
            db.close();
        }
    }

    @Test public void mappedGroundedRowsCanPassEquivalenceGate() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            legacyTables(db);
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            insertLegacyRaw(db, 1, now, "hello from notification");
            insertLegacyMemory(db, 11, now, "Proposal", "latest project proposal");

            String ev = "ev_test";
            ContentValues e = new ContentValues();
            e.put("id", ev); e.put("identity_key", "evidence-test"); e.put("source_type", "NOTIFICATION");
            e.put("source_package", "com.test"); e.put("occurred_at", now); e.put("captured_at", now);
            e.put("original_text", "latest project proposal"); e.put("normalized_text", "latest project proposal");
            e.put("content_hash", Fingerprint.text("latest project proposal")); e.put("sensitivity", "NORMAL");
            e.put("retention_class", "EPISODIC_90_DAY"); e.put("expires_at", now + CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS);
            e.put("processing_state", "READY"); e.put("created_at", now); e.put("updated_at", now);
            db.insertOrThrow("v4_evidence", null, e);

            String mem = "mem_test";
            ContentValues m = new ContentValues();
            m.put("id", mem); m.put("identity_key", "memory-test"); m.put("kind", "MOMENT");
            m.put("title", "Proposal"); m.put("body", "latest project proposal"); m.put("started_at", now);
            m.put("importance", .5); m.put("pinned", 0); m.put("retention_class", "EPISODIC_90_DAY");
            m.put("expires_at", now + CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS); m.put("state", "ACTIVE");
            m.put("created_at", now); m.put("updated_at", now);
            db.insertOrThrow("v4_memories", null, m);

            ContentValues me = new ContentValues();
            me.put("memory_id", mem); me.put("evidence_id", ev); me.put("role", "supports"); me.put("ordinal", 0); me.put("created_at", now);
            db.insertOrThrow("v4_memory_evidence", null, me);

            map(db, "raw_signals", "1", "EVIDENCE", ev, now);
            map(db, "knowledge_items", "11", "MEMORY", mem, now);

            CognitiveMemoryEquivalenceV4.Report r = CognitiveMemoryEquivalenceV4.evaluate(db, now);
            assertEquals(0, r.pendingRawEvidence());
            assertEquals(0, r.pendingMemories());
            assertTrue(r.integrityClean());
            assertTrue(r.migrationComplete());
            assertTrue(r.cutoverReady());
        } finally {
            db.close();
        }
    }

    @Test public void orphanMemoryIsReleaseBlocking() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            legacyTables(db);
            CognitiveSchemaV4.ensure(db);
            long now = System.currentTimeMillis();
            ContentValues m = new ContentValues();
            m.put("id", "mem_orphan"); m.put("identity_key", "orphan"); m.put("kind", "MOMENT");
            m.put("body", "orphan"); m.put("started_at", now); m.put("importance", .5); m.put("pinned", 0);
            m.put("retention_class", "EPISODIC_90_DAY"); m.put("expires_at", now + CognitiveMemoryBackfillV4.EPISODIC_WINDOW_MS);
            m.put("state", "ACTIVE"); m.put("created_at", now); m.put("updated_at", now);
            db.insertOrThrow("v4_memories", null, m);

            CognitiveMemoryEquivalenceV4.Report r = CognitiveMemoryEquivalenceV4.evaluate(db, now);
            assertEquals(1, r.memoryWithoutEvidence);
            assertFalse(r.integrityClean());
            assertFalse(r.cutoverReady());
        } finally {
            db.close();
        }
    }

    @Test public void forwardBridgeExtractsNotificationIdentityFromNestedMetadata() {
        String json = "{\"source_metadata\":{\"notification_key\":\"0|com.whatsapp|42\"}}";
        assertEquals("0|com.whatsapp|42", CognitiveMemoryForwardBridgeV4.externalId(json));
    }

    private static void legacyTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE raw_signals(id INTEGER PRIMARY KEY,kind TEXT,source TEXT,title TEXT,body TEXT,metadata_json TEXT,content_hash TEXT,occurred_at INTEGER,created_at INTEGER,retention_until INTEGER,state TEXT,thread_id INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE signal_threads(id INTEGER PRIMARY KEY,kind TEXT,source TEXT,external_key TEXT,state TEXT,started_at INTEGER,last_event_at INTEGER)");
        db.execSQL("CREATE TABLE knowledge_items(id INTEGER PRIMARY KEY,type TEXT,source TEXT,title TEXT,raw_text TEXT,extracted_text TEXT,summary TEXT,attachment_path TEXT,status TEXT,fingerprint TEXT,metadata_json TEXT,created_at INTEGER,updated_at INTEGER)");
    }

    private static void insertLegacyRaw(SQLiteDatabase db, long id, long now, String body) {
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("kind", "notification"); v.put("source", "com.test"); v.put("title", "Test");
        v.put("body", body); v.put("metadata_json", "{}"); v.put("content_hash", Fingerprint.text(body));
        v.put("occurred_at", now); v.put("created_at", now); v.put("retention_until", now + 1000); v.put("state", "filtered");
        db.insertOrThrow("raw_signals", null, v);
    }

    private static void insertLegacyMemory(SQLiteDatabase db, long id, long now, String title, String body) {
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("type", "NOTE"); v.put("source", "manual"); v.put("title", title); v.put("raw_text", body);
        v.put("extracted_text", ""); v.put("summary", ""); v.put("attachment_path", ""); v.put("status", "analyzed");
        v.put("fingerprint", Fingerprint.text(body)); v.put("metadata_json", "{}"); v.put("created_at", now); v.put("updated_at", now);
        db.insertOrThrow("knowledge_items", null, v);
    }

    private static void map(SQLiteDatabase db, String table, String legacyId, String type, String objectId, long now) {
        ContentValues v = new ContentValues();
        v.put("legacy_table", table); v.put("legacy_id", legacyId); v.put("object_type", type); v.put("object_id", objectId);
        v.put("migration_state", "MAPPED"); v.put("created_at", now); v.put("updated_at", now);
        db.insertOrThrow("v4_legacy_map", null, v);
    }
}
