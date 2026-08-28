package com.kareem.cortex;

import static org.junit.Assert.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveSchemaV4RegressionTest {

    @Test public void additiveSchemaCreatesCanonicalTablesAndIsIdempotent() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            CognitiveSchemaV4.ensure(db);
            CognitiveSchemaV4.ensure(db);
            assertTrue(table(db, "v4_evidence"));
            assertTrue(table(db, "v4_episodes"));
            assertTrue(table(db, "v4_memories"));
            assertTrue(table(db, "v4_worlds"));
            assertTrue(table(db, "v4_world_identity_claims"));
            assertTrue(table(db, "v4_world_merges"));
            assertTrue(table(db, "v4_facts"));
            assertTrue(table(db, "v4_relations"));
            assertTrue(table(db, "v4_situations"));
            assertTrue(table(db, "v4_provenance"));
            assertTrue(table(db, "v4_legacy_map"));
        } finally {
            db.close();
        }
    }

    private static boolean table(SQLiteDatabase db, String name) {
        Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", new String[]{name});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }
}
