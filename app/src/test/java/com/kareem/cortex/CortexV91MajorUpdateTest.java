package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** One end-to-end v91 release gate covering migration, authority, judgment, policy and read models. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class CortexV91MajorUpdateTest {
    private Context context;
    private VaultDb vault;
    private SQLiteDatabase db;

    @Before public void setUp(){
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("cortex.db");
        CortexPersonalPolicy.clear(context);CortexPolicyLifecycle.clearForTests(context);
        context.getSharedPreferences("cortex_policy_promotion",Context.MODE_PRIVATE).edit().clear().commit();
        vault=new VaultDb(context);db=vault.getWritableDatabase();
        // VaultDb onCreate establishes schema without consuming the explicit v91 migration marker.
        UniversalEventStore.ensure(db);StatefulMeaningStore.ensure(db);CanonicalStateStore.ensure(db);CortexJudgmentTraceStore.ensure(db);
    }

    @After public void tearDown(){if(vault!=null)try{vault.close();}catch(Throwable ignored){}CortexPersonalPolicy.clear(context);CortexPolicyLifecycle.clearForTests(context);context.deleteDatabase("cortex.db");}

    @Test public void v91OneUpdateClosesEveryKnownAuthorityAndNoiseLeak() throws Exception {
        long now=System.currentTimeMillis();

        // Historical visual-derived obligations that v90 and earlier could leave live.
        long visualLegacy=derived("ACTION","No explicit follow-up or action was detected.","No explicit follow-up or action was detected.","open",.80,78,"picbrain",0,0,"ACTION","{\"intentional\":false}");
        long nonIntentionalProject=derived("PROJECT_CANDIDATE","Fake Screenshot Project","Passive OCR inference","pending",.82,60,"screenshot-folder",0,0,"PROJECT_CANDIDATE","{\"intentional\":false}");
        long legacyFallback=derived("ACTION","Legacy fallback trap","This must never enter Now without a final judgment","open",.99,99,"legacy",0,0,"ACTION","{}");
        assertTrue(visualLegacy>0&&nonIntentionalProject>0&&legacyFallback>0);

        Seed visual=seed("visual_evidence","picbrain","ocr_evidence","action_request","request","Bank screenshot","Please check your credit card details.",.94,now-4000);
        long visualSituation=StatefulMeaningStore.correlate(db,visual.semanticId,0,"picbrain","action_request","Bank screenshot","Please check your credit card details.",.94,now-4000);
        long visualAttention=UniversalEventStore.attention(db,visual.semanticId,visualSituation,"ACTION","Bank screenshot","Please check your credit card details.",95,.94,"picbrain","legacy direct visual projection");
        assertTrue(visualSituation>0&&visualAttention>0);

        Seed technical=seed("notification","com.android.providers.downloads","download_progress","technical_state","status","app-debug.apk","app-debug.apk 343.89 KB / ?",.98,now-3000);
        long technicalSituation=StatefulMeaningStore.correlate(db,technical.semanticId,0,"com.android.providers.downloads","technical_state","app-debug.apk","app-debug.apk 343.89 KB / ?",.98,now-3000);
        assertTrue(technicalSituation>0);

        Seed request=seed("notification","com.whatsapp","conversation_notification","action_request","confirm","J&T Express Egypt","Kindly confirm: Is this your shipment? Yes, it is mine. No, it is not mine.",.92,now-2000);
        long requestSituation=StatefulMeaningStore.correlate(db,request.semanticId,0,"com.whatsapp","action_request","J&T Express Egypt","Kindly confirm: Is this your shipment? Yes, it is mine. No, it is not mine.",.92,now-2000);
        assertTrue(requestSituation>0);

        Seed ordinary=seed("notification","com.whatsapp","conversation_notification","conversation_message","message","Mohamed Hammad","وياريت في ميدان عام عشان كلوا يتعظ",.86,now-1000);
        long ordinarySituation=StatefulMeaningStore.correlate(db,ordinary.semanticId,0,"com.whatsapp","conversation_message","Mohamed Hammad","وياريت في ميدان عام عشان كلوا يتعظ",.86,now-1000);
        long unauthorized=UniversalEventStore.attention(db,ordinary.semanticId,ordinarySituation,"ACTION","Legacy direct action","Should not survive",90,.86,"com.whatsapp","legacy direct projection");
        assertTrue(ordinarySituation>0&&unauthorized>0);

        // Prove the stronger v91 source/quality boundaries independently.
        assertFalse(CortexProvenanceGate.evaluate("visual_evidence","picbrain","derived","ocr_evidence","J&T","Please confirm shipment","action_request","request").attentionEligible);
        assertFalse(CanonicalSemanticQualityGate.evaluate("visual_evidence","ocr_evidence","action_request","request","J&T","Please confirm shipment",.95).eligible);
        assertFalse(CanonicalSemanticQualityGate.evaluate("notification","download_progress","technical_state","status","app-debug.apk","343 KB / ?",.98).eligible);
        assertTrue(CanonicalSemanticQualityGate.evaluate("notification","conversation_notification","action_request","confirm","J&T Express Egypt","Kindly confirm: Is this your shipment?",.92).eligible);

        CortexV91Authority.CleanupResult cleanup=CortexV91Authority.migrate(db);
        assertTrue(cleanup.legacyVisual>=1);assertTrue(cleanup.nonIntentionalProjects>=1);assertTrue(cleanup.invalidSituations>=2);assertTrue(cleanup.unauthorizedAttention>=1);
        assertEquals("filtered",state("derived_items",visualLegacy));
        assertEquals("filtered",state("derived_items",nonIntentionalProject));
        assertEquals("open",state("derived_items",legacyFallback)); // preserved, but no longer a Now source.
        assertEquals("quarantined_noise",state("ue_situations",visualSituation));
        assertEquals("quarantined_noise",state("ue_situations",technicalSituation));
        assertTrue("quarantine must preserve provenance membership",count("SELECT COUNT(*) FROM ue_situation_members_v2 WHERE situation_id=?",String.valueOf(visualSituation))>0);
        assertEquals(0,count("SELECT COUNT(*) FROM ue_attention_items WHERE state='open' AND situation_id=?",String.valueOf(visualSituation)));
        assertEquals(0,count("SELECT COUNT(*) FROM ue_attention_items WHERE state='open' AND id=?",String.valueOf(unauthorized)));

        // Future passive project inference must stay impossible after migration as well.
        int projectBefore=count("SELECT COUNT(*) FROM derived_items WHERE kind='PROJECT_CANDIDATE' AND state IN ('open','pending')");
        KnowledgeItem passive=new KnowledgeItem(991,"NOTE","web_import","Passive","Negma Project reference","Negma Project reference","","Notes","","","analyzed","","","",now,now);
        AnalysisResult passiveAnalysis=new AnalysisResult();passiveAnalysis.extractedText="Negma Project reference";passiveAnalysis.entities.add(new AnalysisResult.Entity("PROJECT","Negma Project",.96));
        IntentionalCognitiveBridge.afterAnalysis(vault,passive,passiveAnalysis);
        assertEquals(projectBefore,count("SELECT COUNT(*) FROM derived_items WHERE kind='PROJECT_CANDIDATE' AND state IN ('open','pending')"));

        CanonicalAttentionMaterializer.Result materialized=CanonicalAttentionMaterializer.run(context,vault);
        assertTrue(materialized.candidates>=1);assertTrue(materialized.selectedSituations.contains(requestSituation));
        assertEquals(1,count("SELECT COUNT(*) FROM ue_attention_items WHERE situation_id=? AND kind='ACTION' AND state='open' AND reason LIKE 'FINAL_JUDGE:%'",String.valueOf(requestSituation)));
        assertEquals("NOW",latestDecision(requestSituation));
        assertEquals(0,count("SELECT COUNT(*) FROM ue_attention_items WHERE situation_id=? AND state='open'",String.valueOf(ordinarySituation)));
        assertEquals(0,count("SELECT COUNT(*) FROM ue_attention_items WHERE semantic_event_id=? AND state='open'",String.valueOf(visual.semanticId)));

        // Both Now read paths must consume only the same canonical FINAL-JUDGE ledger.
        PrimeBriefStore.Snapshot snapshot=PrimeBriefStore.load(vault);
        assertTrue(hasTitle(snapshot.actions,"J&T Express Egypt"));
        assertFalse(hasTitle(snapshot.actions,"Legacy fallback trap"));
        List<PrimeBriefStore.Item> cognitiveNow=CognitiveNowReadModel.load(db,12);
        assertTrue(hasTitle(cognitiveNow,"J&T Express Egypt"));
        assertFalse(hasTitle(cognitiveNow,"Legacy fallback trap"));
        for(PrimeBriefStore.Item x:cognitiveNow)assertTrue(x.source.startsWith("final_judge|"));
        assertFalse("presentation must never veto a final judgment",NowQualityPolicy.suppress("ACTION","final_judge|com.whatsapp","J&T Express Egypt","download complete"));
        assertFalse("presentation noise guard must never act as a second judge",AttentionNoisePolicy.suppress("final_judge|com.whatsapp","J&T Express Egypt","special offer","action_request","request"));

        // Decision classification is structured, not driven by explanation wording.
        AttentionDecisionEngine.Candidate low=new AttentionDecisionEngine.Candidate(7001,"message","open","FYI","Useful context",.92,.30,.35,.45,.10,.20,0,now,now,1,1,true,false,false,false,false);
        CortexAttentionJudge.Judgment a=new CortexAttentionJudge.Judgment(low,false,.22,.76,.25,"resolved state is never an interruption","policy-x");
        CortexAttentionJudge.Judgment b=new CortexAttentionJudge.Judgment(low,false,.22,.76,.25,"expected attention value exceeds interruption cost","policy-x");
        assertEquals(CortexJudgmentTraceStore.finalDecision(a,false),CortexJudgmentTraceStore.finalDecision(b,false));

        // Teacher policy activation must synchronously re-judge/materialize under the new policy.
        JSONObject teacher=new JSONObject().put("version","chatgpt-teacher-v91-integration").put("ttlMs",604800000L).put("attentionThreshold",.76).put("maxNowItems",5).put("interruptionPenaltyScale",.29)
                .put("featureWeights",new JSONObject().put("urgency",.14).put("actionability",.18).put("personalRelevance",.12).put("risk",.14).put("novelty",.05).put("deadline",.17).put("contextMatch",.10).put("explicitRequest",.22).put("openCommitment",.20).put("recency",.08))
                .put("boosts",new JSONArray().put(new JSONObject().put("feature","explicitRequest").put("weight",.24)).put(new JSONObject().put("feature","duplicate").put("weight",-.42)).put(new JSONObject().put("feature","lowInformation").put("weight",-.46))).put("teacherNotes","v91 integration gate");
        JSONObject impact=new JSONObject().put("candidateCount",2).put("beforeNow",1).put("afterNow",1).put("promoted",0).put("deferred",0).put("averageScoreDelta",0.01);
        CortexPolicyPromotion.Result promotion=CortexPolicyPromotion.evaluateAndPromote(context,teacher,impact);
        assertTrue("teacher canary should pass v91 invariants: "+promotion.reason,promotion.promoted);assertEquals("CANARY",promotion.state);
        assertEquals("chatgpt-teacher-v91-integration",CortexPersonalPolicy.version(context));
        CortexJudgmentTraceStore.Snapshot latest=CortexJudgmentTraceStore.latest(db,requestSituation);assertNotNull(latest);assertEquals("chatgpt-teacher-v91-integration",latest.policyVersion);assertEquals("NOW",latest.finalDecision);

        CortexV91Authority.Validation validation=CortexV91Authority.validate(db,CortexPersonalPolicy.version(context),CortexPersonalPolicy.maxNowItems(context));
        assertTrue("v91 authority invariants failed: "+validation.summary(),validation.ok);
        assertEquals(0,validation.openVisualLegacyActions);assertEquals(0,validation.nonIntentionalProjectCandidates);assertEquals(0,validation.invalidOpenSituations);assertEquals(0,validation.unauthorizedOpenAttention);assertEquals(0,validation.visualOpenAttention);assertEquals(0,validation.policyMismatches);assertEquals(0,validation.mirrorMismatches);

        Cursor decisions=db.rawQuery("SELECT DISTINCT final_decision FROM cortex_judgment_trace",null);try{while(decisions.moveToNext()){String d=decisions.getString(0);assertTrue("unexpected judgment disposition "+d,"NOW".equals(d)||"LATER".equals(d)||"SILENT".equals(d)||"SUPPRESS".equals(d));}}finally{decisions.close();}
        Cursor quick=db.rawQuery("PRAGMA quick_check(1)",null);try{assertTrue(quick.moveToFirst());assertEquals("ok",quick.getString(0).toLowerCase());}finally{quick.close();}
    }

    private Seed seed(String sourceType,String sourceKey,String technical,String semanticType,String intent,String subject,String summary,double confidence,long at)throws Exception{
        JSONObject meta=new JSONObject().put("test","v91");long raw=UniversalEventStore.appendRaw(db,sourceType,sourceKey,"v91-"+sourceKey+"-"+at,"posted","android",technical,subject,summary,meta,at);
        long stream=UniversalEventStore.upsertStream(db,sourceType,sourceKey+"|"+raw,"active",Fingerprint.text(subject+summary),subject,summary,"android",technical,at,true,meta);
        long semantic=UniversalEventStore.insertSemantic(db,raw,stream,1,semanticType,intent,subject,summary,confidence,"complete",true,"v91_test","grounded integration fixture",at);
        CanonicalStateStore.set(db,semantic,"supported",confidence,"v91 test fixture");return new Seed(raw,semantic);
    }

    private long derived(String kind,String title,String body,String state,double confidence,int importance,String source,long thread,long anchor,String candidate,String metadata){long at=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind",kind);v.put("title",title);v.put("body",body);v.put("state",state);v.put("confidence",confidence);v.put("importance",importance);v.put("fingerprint",Fingerprint.text("v91-test|"+kind+"|"+title+"|"+at));v.put("metadata_json",metadata);v.put("created_at",at);v.put("updated_at",at);v.put("source_key",source);v.put("thread_id",thread);v.put("anchor_signal_id",anchor);v.put("candidate_kind",candidate);v.put("semantic_key","");return db.insertOrThrow("derived_items",null,v);}
    private String state(String table,long id){Cursor c=db.rawQuery("SELECT state FROM "+table+" WHERE id=?",new String[]{String.valueOf(id)});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    private int count(String sql,String...args){Cursor c=db.rawQuery(sql,args==null||args.length==0?null:args);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private String latestDecision(long situation){Cursor c=db.rawQuery("SELECT final_decision FROM cortex_judgment_trace WHERE situation_id=? ORDER BY id DESC LIMIT 1",new String[]{String.valueOf(situation)});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    private boolean hasTitle(List<PrimeBriefStore.Item> xs,String needle){if(xs==null)return false;for(PrimeBriefStore.Item x:xs)if(x!=null&&x.title!=null&&x.title.contains(needle))return true;return false;}
    private static final class Seed{final long rawId,semanticId;Seed(long r,long s){rawId=r;semanticId=s;}}
}
