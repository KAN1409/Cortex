package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveDeepBrainV4RegressionTest {

    @Test public void protocolParsesMarkerAndJsonEvenInsideExtraText() {
        String raw="Here is the result\nCORTEX_RESPONSE_V1\n```json\n{\"request_id\":\"brq_1\",\"answer\":\"Do the urgent item first\"}\n```\nDone";
        CognitiveDeepBrainProtocolV4.ParsedResponse r=CognitiveDeepBrainProtocolV4.parseResponse(raw);
        assertEquals("brq_1",r.requestId);assertEquals("Do the urgent item first",r.answer);
    }

    @Test public void groundedRankedPriorityIsStoredButUngroundedPriorityIsRejected() {
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            seed(db,"brq_rank");
            String raw="CORTEX_RESPONSE_V1\n{\"request_id\":\"brq_rank\",\"priority_items\":[{\"rank\":1,\"title\":\"Review the captured commitment\",\"reason\":\"Recent Memory supports it\",\"attention_score\":0.91,\"memory_ids\":[\"mem_allowed\"]},{\"rank\":2,\"title\":\"Invented priority\",\"attention_score\":0.8}]}";
            CognitiveDeepBrainApplyV4.Result r=CognitiveDeepBrainApplyV4.apply(db,CognitiveDeepBrainProtocolV4.parseResponse(raw));
            assertEquals(1,r.rankedPrioritiesStored);assertTrue(r.skipped>=1);
            Cursor c=db.rawQuery("SELECT rank_order,title,attention_score,state,memory_ids_json FROM v4_deep_brain_priority_items WHERE state='ACTIVE'",null);assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));assertEquals("Review the captured commitment",c.getString(1));assertEquals(.91,c.getDouble(2),.0001);assertEquals("ACTIVE",c.getString(3));assertTrue(c.getString(4).contains("mem_allowed"));assertFalse(c.moveToNext());c.close();
        }finally{db.close();}
    }

    @Test public void validPriorityUpdateIsAppliedAndExternalActionRequiresConfirmation() {
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            seed(db,"brq_ok");
            String raw="CORTEX_RESPONSE_V1\n{\"request_id\":\"brq_ok\",\"answer\":\"Focus on the commitment\",\"priority_updates\":[{\"situation_id\":\"si_allowed\",\"attention_score\":0.95,\"interruption_score\":0.6,\"state\":\"RELEVANT\"}],\"suggested_actions\":[{\"situation_id\":\"si_allowed\",\"type\":\"SEND\",\"label\":\"Send the revised drawing\",\"risk\":\"SAFE\"}]}";
            CognitiveDeepBrainApplyV4.Result r=CognitiveDeepBrainApplyV4.apply(db,CognitiveDeepBrainProtocolV4.parseResponse(raw));
            assertEquals(1,r.priorityUpdatesApplied);assertEquals(1,r.actionsCreated);assertFalse(r.alreadyApplied);
            Cursor c=db.rawQuery("SELECT state,attention_score,interruption_score FROM v4_situations WHERE id='si_allowed'",null);assertTrue(c.moveToFirst());assertEquals("RELEVANT",c.getString(0));assertEquals(.95,c.getDouble(1),.0001);assertEquals(.6,c.getDouble(2),.0001);c.close();
            c=db.rawQuery("SELECT risk,state FROM v4_action_proposals LIMIT 1",null);assertTrue(c.moveToFirst());assertEquals("CONFIRMATION_REQUIRED",c.getString(0));assertEquals("PROPOSED",c.getString(1));c.close();
        }finally{db.close();}
    }

    @Test public void deepBrainCannotResolveSituationOrTouchUnlistedSituation() {
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            seed(db,"brq_block");
            String raw="CORTEX_RESPONSE_V1\n{\"request_id\":\"brq_block\",\"priority_updates\":[{\"situation_id\":\"si_allowed\",\"attention_score\":1,\"state\":\"RESOLVED\"},{\"situation_id\":\"si_other\",\"attention_score\":1,\"state\":\"RELEVANT\"}]}";
            CognitiveDeepBrainApplyV4.Result r=CognitiveDeepBrainApplyV4.apply(db,CognitiveDeepBrainProtocolV4.parseResponse(raw));
            assertEquals(0,r.priorityUpdatesApplied);assertTrue(r.skipped>=2);
            Cursor c=db.rawQuery("SELECT state,attention_score FROM v4_situations WHERE id='si_allowed'",null);assertTrue(c.moveToFirst());assertEquals("DETECTED",c.getString(0));assertEquals(.2,c.getDouble(1),.0001);c.close();
            c=db.rawQuery("SELECT state,attention_score FROM v4_situations WHERE id='si_other'",null);assertTrue(c.moveToFirst());assertEquals("DETECTED",c.getString(0));assertEquals(.1,c.getDouble(1),.0001);c.close();
        }finally{db.close();}
    }

    @Test public void applyDoesNotRewriteEvidenceOrFactsAndReplayIsIdempotent() {
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            seed(db,"brq_replay");int evidenceBefore=count(db,"v4_evidence"),factsBefore=count(db,"v4_facts");
            String raw="CORTEX_RESPONSE_V1\n{\"request_id\":\"brq_replay\",\"answer\":\"Review now\",\"priority_updates\":[{\"situation_id\":\"si_allowed\",\"attention_score\":0.8,\"state\":\"SURFACED\"}]}";
            CognitiveDeepBrainProtocolV4.ParsedResponse p=CognitiveDeepBrainProtocolV4.parseResponse(raw);
            CognitiveDeepBrainApplyV4.Result first=CognitiveDeepBrainApplyV4.apply(db,p);assertEquals(1,first.priorityUpdatesApplied);
            CognitiveDeepBrainApplyV4.Result second=CognitiveDeepBrainApplyV4.apply(db,p);assertTrue(second.alreadyApplied);assertEquals(0,second.priorityUpdatesApplied);
            assertEquals(evidenceBefore,count(db,"v4_evidence"));assertEquals(factsBefore,count(db,"v4_facts"));
        }finally{db.close();}
    }

    private static void seed(SQLiteDatabase db,String requestId){
        CognitiveSchemaV4.ensure(db);CognitiveDeepBrainStoreV4.ensure(db);long now=System.currentTimeMillis();
        insertSituation(db,"si_allowed",.2,now);insertSituation(db,"si_other",.1,now);insertMemory(db,"mem_allowed",now);
        CognitiveDeepBrainPacketBuilderV4.Packet packet=new CognitiveDeepBrainPacketBuilderV4.Packet(requestId,"What matters?","{}","share",Collections.singletonList("si_allowed"),Collections.singletonList("mem_allowed"),Collections.<String>emptyList(),Collections.<String>emptyList());
        CognitiveDeepBrainStoreV4.saveRequest(db,packet);
        ContentValues e=new ContentValues();e.put("id","ev_keep");e.put("identity_key","ev_keep_key");e.put("source_type","NOTE");e.put("occurred_at",now);e.put("captured_at",now);e.put("sensitivity","NORMAL");e.put("retention_class","EPISODIC_90_DAY");e.put("processing_state","READY");e.put("created_at",now);e.put("updated_at",now);db.insert("v4_evidence",null,e);
        ContentValues f=new ContentValues();f.put("id","fa_keep");f.put("slot_key","slot");f.put("version_key","version");f.put("predicate","status");f.put("value","kept");f.put("grounding","INFERRED");f.put("confidence",.8);f.put("status","ACTIVE");f.put("created_at",now);f.put("updated_at",now);db.insert("v4_facts",null,f);
    }
    private static void insertSituation(SQLiteDatabase db,String id,double score,long now){ContentValues v=new ContentValues();v.put("id",id);v.put("identity_key","identity_"+id);v.put("kind","FOLLOW_UP");v.put("state","DETECTED");v.put("headline",id);v.put("semantic_anchor","anchor_"+id);v.put("attention_score",score);v.put("interruption_score",.1);v.put("confidence",.8);v.put("created_at",now);v.put("last_evaluated_at",now);v.put("updated_at",now);db.insert("v4_situations",null,v);}
    private static void insertMemory(SQLiteDatabase db,String id,long now){ContentValues v=new ContentValues();v.put("id",id);v.put("identity_key","identity_"+id);v.put("kind","MOMENT");v.put("title","Grounded memory");v.put("body","A real captured memory");v.put("started_at",now);v.put("importance",.5);v.put("pinned",0);v.put("retention_class","EPISODIC_90_DAY");v.put("state","ACTIVE");v.put("created_at",now);v.put("updated_at",now);db.insert("v4_memories",null,v);}
    private static int count(SQLiteDatabase db,String table){Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
}
