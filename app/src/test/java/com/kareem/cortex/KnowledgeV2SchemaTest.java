package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class KnowledgeV2SchemaTest {
    private VaultDb db;

    @After public void tearDown(){
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase("cortex.db");
    }

    @Test public void additiveSchemaPreservesLegacyKnowledgeAndCreatesLedger(){
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase("cortex.db");
        db=new VaultDb(c);

        long legacy=db.insert("TEXT","test","Legacy memory","keep me","Notes","","","kv2-test-legacy","{}");
        assertTrue(legacy>0);

        KnowledgeV2Schema.ensure(db.getWritableDatabase());
        assertTrue(KnowledgeV2Schema.ready(db.getReadableDatabase()));
        assertNotNull(db.getById(legacy));

        Cursor tables=db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('kv2_evidence','kv2_facts','kv2_events','kv2_processing')",null);
        assertTrue(tables.moveToFirst());
        assertEquals(4,tables.getInt(0));
        tables.close();
    }
}
