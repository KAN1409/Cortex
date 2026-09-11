package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
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

    @Test public void completionPipelineAccountsForEligibleBlockedAndNoTextEvidence(){
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase("cortex.db");
        db=new VaultDb(c);
        SQLiteDatabase s=db.getWritableDatabase();
        KnowledgeV2Schema.ensure(s);

        insertEvidence(s,"eligible","hello",1,0.0);
        insertEvidence(s,"blocked","self screenshot",0,1.0);
        insertEvidence(s,"blank","",1,0.0);

        KnowledgeV2Maintenance.prepare(s);

        Cursor states=s.rawQuery(
                "SELECT state,COUNT(*) FROM kv2_processing WHERE stage=? AND pipeline_version=? GROUP BY state",
                new String[]{KnowledgeV2Store.STAGE_EXTRACTION,String.valueOf(KnowledgeV2Schema.PIPELINE_VERSION)});
        int pending=0,blocked=0,skipped=0;
        while(states.moveToNext()){
            String state=states.getString(0);int count=states.getInt(1);
            if("PENDING".equals(state))pending=count;
            else if("BLOCKED".equals(state))blocked=count;
            else if("SKIPPED".equals(state))skipped=count;
        }
        states.close();
        assertEquals(1,pending);
        assertEquals(1,blocked);
        assertEquals(1,skipped);
    }

    private static void insertEvidence(SQLiteDatabase s,String key,String raw,int eligible,double selfScore){
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("source_type","PICBRAIN_SCREENSHOT");v.put("source_key",key);v.put("source_uri","content://test/"+key);v.put("source_media_id",key.hashCode());
        v.put("raw_text",raw);v.put("content_hash",key);v.put("origin","EXTERNAL");v.put("self_reference_score",selfScore);v.put("derivation_depth",0);v.put("knowledge_eligible",eligible);
        v.put("provenance_reason","");v.put("captured_at",now);v.put("observed_at",now);v.put("created_at",now);v.put("updated_at",now);
        assertTrue(s.insert("kv2_evidence",null,v)>0);
    }
}
