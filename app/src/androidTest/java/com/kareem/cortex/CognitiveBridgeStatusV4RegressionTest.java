package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveBridgeStatusV4RegressionTest {
    @Test public void reportsSecondBrainEnrichmentAndChatGptFreshnessWithoutMutatingTruth(){
        SQLiteDatabase db=SQLiteDatabase.create(null);try{
            CognitiveSchemaV4.ensure(db);CognitiveDeepBrainStoreV4.ensure(db);CortexLocalBusStoreV1.ensure(db);
            long base=1_800_000_000_000L,brainAt=base+1_000L,newSituationAt=base+2_000L;

            ContentValues client=new ContentValues();client.put("connector_id","second_brain");client.put("package_name","com.kareem.secondbrain");client.put("capabilities_json","[\"NOTIFICATIONS\"]");client.put("source_priority",100);client.put("last_seen_at",base);client.put("last_event_at",base);client.put("accepted_events",7);client.put("rejected_events",1);client.put("updated_at",base);db.insert("connector_clients",null,client);
            ContentValues event=new ContentValues();event.put("event_id","sb_e2e");event.put("connector_id","second_brain");event.put("connector_package","com.kareem.secondbrain");event.put("source_type","NOTIFICATION");event.put("source_package","com.whatsapp");event.put("occurred_at",base);event.put("received_at",base+10);event.put("state","ACCEPTED");event.put("signal_id",42);event.put("detail","");event.put("updated_at",base+10);db.insert("connector_ingest_events",null,event);

            ContentValues evidence=new ContentValues();evidence.put("id","ev_sb");evidence.put("identity_key","ev_sb_key");evidence.put("source_type","NOTIFICATION");evidence.put("source_package","com.whatsapp");evidence.put("occurred_at",base);evidence.put("captured_at",base);evidence.put("sensitivity","NORMAL");evidence.put("retention_class","EPISODIC_90_DAY");evidence.put("processing_state","READY");evidence.put("created_at",base);evidence.put("updated_at",base);db.insert("v4_evidence",null,evidence);
            ContentValues analysis=new ContentValues();analysis.put("id","ea_sb");analysis.put("evidence_id","ev_sb");analysis.put("analysis_kind","CONNECTOR_ENRICHMENT");analysis.put("engine","local_bus:second_brain");analysis.put("version","1");analysis.put("output_text","CORTEX_E2E_001 before 5");analysis.put("content_hash","hash");analysis.put("created_at",base);db.insert("v4_evidence_analysis",null,analysis);
            ContentValues memory=new ContentValues();memory.put("id","mem_sb");memory.put("identity_key","mem_sb_key");memory.put("kind","MOMENT");memory.put("body","Send the design before 5");memory.put("source_package","com.whatsapp");memory.put("started_at",base);memory.put("importance",.8);memory.put("pinned",0);memory.put("retention_class","EPISODIC_90_DAY");memory.put("state","ACTIVE");memory.put("created_at",base);memory.put("updated_at",base);db.insert("v4_memories",null,memory);
            ContentValues me=new ContentValues();me.put("memory_id","mem_sb");me.put("evidence_id","ev_sb");me.put("role","supports");me.put("ordinal",0);me.put("created_at",base);db.insert("v4_memory_evidence",null,me);
            insertSituation(db,"si_old",base);
            insertSituation(db,"si_sb",newSituationAt);
            ContentValues p=new ContentValues();p.put("object_type","SITUATION");p.put("object_id","si_sb");p.put("source_type","MEMORY");p.put("source_id","mem_sb");p.put("role","grounded_by");p.put("confidence",1.0);p.put("created_at",newSituationAt);db.insert("v4_provenance",null,p);

            ContentValues req=new ContentValues();req.put("id","brq_applied");req.put("question","What matters?");req.put("context_json","{}");req.put("share_text_hash","h");req.put("situation_ids_json","[\"si_old\"]");req.put("memory_ids_json","[]");req.put("world_ids_json","[]");req.put("fact_ids_json","[]");req.put("state","APPLIED");req.put("created_at",base);req.put("exported_at",base);req.put("applied_at",brainAt);req.put("updated_at",brainAt);db.insert("v4_deep_brain_requests",null,req);
            ContentValues pri=new ContentValues();pri.put("id","pri_1");pri.put("request_id","brq_applied");pri.put("rank_order",1);pri.put("title","Old grounded priority");pri.put("attention_score",.9);pri.put("situation_id","si_old");pri.put("memory_ids_json","[]");pri.put("world_ids_json","[]");pri.put("state","ACTIVE");pri.put("created_at",brainAt);pri.put("updated_at",brainAt);db.insert("v4_deep_brain_priority_items",null,pri);
            ContentValues action=new ContentValues();action.put("id","act_1");action.put("situation_id","si_old");action.put("action_type","CUSTOM");action.put("label","Review it");action.put("risk","CONFIRMATION_REQUIRED");action.put("payload_json","{\"origin\":\"chatgpt_plus_share\"}");action.put("state","PROPOSED");action.put("created_at",brainAt);action.put("updated_at",brainAt);db.insert("v4_action_proposals",null,action);

            CognitiveBridgeStatusV4.Snapshot s=CognitiveBridgeStatusV4.read(db);
            assertTrue(s.secondBrainSeen);assertEquals(7,s.secondBrainAccepted);assertEquals(1,s.secondBrainRejected);
            assertEquals("com.whatsapp",s.latestSourcePackage);assertEquals(42,s.latestSignalId);
            assertEquals(1,s.connectorEnrichedEvidence);assertEquals(1,s.connectorEnrichedSituations);
            assertEquals(brainAt,s.latestChatGptAppliedAt);assertEquals(1,s.activeChatGptPriorities);assertEquals(1,s.activeChatGptActions);
            assertEquals(1,s.newSinceChatGpt);assertTrue(s.hasAnythingToShow());
        }finally{db.close();}
    }

    private static void insertSituation(SQLiteDatabase db,String id,long updatedAt){ContentValues v=new ContentValues();v.put("id",id);v.put("identity_key","identity_"+id);v.put("kind","DEADLINE");v.put("state","DETECTED");v.put("headline",id);v.put("semantic_anchor","anchor_"+id);v.put("attention_score",.6);v.put("interruption_score",.2);v.put("confidence",.8);v.put("created_at",updatedAt);v.put("last_evaluated_at",updatedAt);v.put("updated_at",updatedAt);db.insert("v4_situations",null,v);}
}
