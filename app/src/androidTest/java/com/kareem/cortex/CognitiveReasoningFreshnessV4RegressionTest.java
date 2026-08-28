package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveReasoningFreshnessV4RegressionTest {
    @Test public void situationAfterAppliedReasoningIsNewContext(){
        assertTrue(CognitiveReasoningFreshnessV4.isNew(2_000L,1_000L));
    }

    @Test public void situationAlreadyCoveredByAppliedReasoningIsNotNew(){
        assertFalse(CognitiveReasoningFreshnessV4.isNew(1_000L,1_000L));
        assertFalse(CognitiveReasoningFreshnessV4.isNew(900L,1_000L));
    }

    @Test public void firstReasoningPassTreatsExistingSituationAsUnreviewed(){
        assertTrue(CognitiveReasoningFreshnessV4.isNew(1_000L,0L));
    }

    @Test public void boundedAppliedRequestDoesNotFreshenOmittedSituation(){
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            CognitiveSchemaV4.ensure(db);CognitiveDeepBrainStoreV4.ensure(db);
            long changed=1_800_000_000_000L,applied=changed+1_000L;
            insertSituation(db,"si_included",changed);insertSituation(db,"si_omitted",changed);

            ContentValues r=new ContentValues();r.put("id","brq_bounded");r.put("question","What matters now?");r.put("context_json","{}");r.put("share_text_hash","h");r.put("situation_ids_json","[\"si_included\"]");r.put("memory_ids_json","[]");r.put("world_ids_json","[]");r.put("fact_ids_json","[]");r.put("state","APPLIED");r.put("created_at",changed);r.put("exported_at",changed);r.put("applied_at",applied);r.put("updated_at",applied);db.insert("v4_deep_brain_requests",null,r);

            assertFalse(CognitiveReasoningFreshnessV4.isNew(db,"si_included",changed));
            assertTrue(CognitiveReasoningFreshnessV4.isNew(db,"si_omitted",changed));
            assertEquals(1,CognitiveReasoningFreshnessV4.newOpenCount(db));
        }finally{db.close();}
    }

    private static void insertSituation(SQLiteDatabase db,String id,long changed){
        ContentValues v=new ContentValues();v.put("id",id);v.put("identity_key","identity_"+id);v.put("kind","DEADLINE");v.put("state","DETECTED");v.put("headline",id);v.put("semantic_anchor","anchor_"+id);v.put("attention_score",.7);v.put("interruption_score",.2);v.put("confidence",.8);v.put("created_at",changed);v.put("last_evaluated_at",changed);v.put("updated_at",changed);db.insert("v4_situations",null,v);
    }
}
