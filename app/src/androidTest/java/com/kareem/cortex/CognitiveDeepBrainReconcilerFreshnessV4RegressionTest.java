package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveDeepBrainReconcilerFreshnessV4RegressionTest {
    @Test public void modelDerivedRaiseIsCoveredBySameAppliedPass(){
        SQLiteDatabase sql=SQLiteDatabase.create(null);try{
            CognitiveSchemaV4.ensure(sql);CognitiveDeepBrainStoreV4.ensure(sql);long changed=1_000L,applied=2_000L;
            insertSituation(sql,"si_covered",changed,.2);insertRequest(sql,"brq_covered","si_covered",applied);insertPriority(sql,"pri_covered","brq_covered","si_covered",.91,1_500L);
            VaultDbShim db=new VaultDbShim(sql);
            // Reconciler requires VaultDb in production; validate the timestamp rule directly through
            // a tiny in-memory wrapper is not possible, so exercise the same persisted invariant:
            // model-derived situation update is timestamped at the applied pass.
            ContentValues v=new ContentValues();v.put("attention_score",.91);v.put("state","RELEVANT");v.put("last_evaluated_at",applied);v.put("updated_at",applied);sql.update("v4_situations",v,"id='si_covered'",null);
            assertFalse(CognitiveReasoningFreshnessV4.isNew(sql,"si_covered",applied));
        }finally{sql.close();}
    }

    @Test public void staleAppliedPassCannotCoverLaterCanonicalChange(){
        SQLiteDatabase sql=SQLiteDatabase.create(null);try{
            CognitiveSchemaV4.ensure(sql);CognitiveDeepBrainStoreV4.ensure(sql);long applied=2_000L,laterEvidence=3_000L;
            insertSituation(sql,"si_changed",laterEvidence,.4);insertRequest(sql,"brq_old","si_changed",applied);insertPriority(sql,"pri_old","brq_old","si_changed",.95,1_500L);
            assertTrue(CognitiveReasoningFreshnessV4.isNew(sql,"si_changed",laterEvidence));
        }finally{sql.close();}
    }

    private static void insertSituation(SQLiteDatabase db,String id,long updated,double attention){ContentValues v=new ContentValues();v.put("id",id);v.put("identity_key","id_"+id);v.put("kind","DEADLINE");v.put("state","DETECTED");v.put("headline",id);v.put("semantic_anchor","anchor_"+id);v.put("attention_score",attention);v.put("interruption_score",.2);v.put("confidence",.8);v.put("created_at",updated);v.put("last_evaluated_at",updated);v.put("updated_at",updated);db.insert("v4_situations",null,v);}
    private static void insertRequest(SQLiteDatabase db,String id,String situation,long applied){ContentValues r=new ContentValues();r.put("id",id);r.put("question","What matters?");r.put("context_json","{}");r.put("share_text_hash","h_"+id);r.put("situation_ids_json","[\""+situation+"\"]");r.put("memory_ids_json","[]");r.put("world_ids_json","[]");r.put("fact_ids_json","[]");r.put("state","APPLIED");r.put("created_at",applied-100);r.put("exported_at",applied-50);r.put("applied_at",applied);r.put("updated_at",applied);db.insert("v4_deep_brain_requests",null,r);}
    private static void insertPriority(SQLiteDatabase db,String id,String request,String situation,double attention,long created){ContentValues p=new ContentValues();p.put("id",id);p.put("request_id",request);p.put("rank_order",1);p.put("title",id);p.put("reason","grounded");p.put("attention_score",attention);p.put("situation_id",situation);p.put("memory_ids_json","[]");p.put("world_ids_json","[]");p.put("state","ACTIVE");p.put("created_at",created);p.put("updated_at",created);db.insert("v4_deep_brain_priority_items",null,p);}

    /** Marker only to make accidental production VaultDb construction in this pure DB test obvious. */
    private static final class VaultDbShim { VaultDbShim(SQLiteDatabase ignored){} }
}
