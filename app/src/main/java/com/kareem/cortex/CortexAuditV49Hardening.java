package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Locale;

/** v49 final audit pass: separates technical existence from real feature readiness and adds warm-path checks. */
public final class CortexAuditV49Hardening {
    private CortexAuditV49Hardening(){}

    public static void apply(Context ctx,VaultDb db,long runId,JSONObject finalMetrics){
        try{InteractionTelemetry.ensure(db);}catch(Exception ignored){}
        extendedPrivacy(db,runId);
        featureReadiness(db,runId);
        warmLocalQwen(ctx,db,runId);
        interactionCoverage(db,runId);
        try{CortexAuditStore.log(db,runId,"info","audit_v49","hardening_pass","v49 readiness, privacy and warm-latency checks applied before final summary",finalMetrics);}catch(Exception ignored){}
    }

    private static void extendedPrivacy(VaultDb db,long runId){
        try{
            VisualTriage.Result auth=VisualTriage.evaluate(fake("GitHub login • First copy your one-time device code: D4BD-304E"));
            VisualTriage.Result tax=VisualTriage.evaluate(fake("جمهورية مصر العربية وزارة المالية مصلحة الضرائب المصرية بطاقة ضريبية كود النشاط 410 2730406266812906"));
            VisualTriage.Result chat=VisualTriage.evaluate(fake("WhatsApp chat screen • Elham Salah • Message • مساء الخير"));
            VisualTriage.Result normal=VisualTriage.evaluate(fake("Modern walnut lounge chair design reference • price 1200 EGP"));
            boolean ok=auth.sensitive&&tax.sensitive&&chat.sensitive&&!normal.sensitive;
            JSONObject e=new JSONObject().put("auth_code_blocked",auth.sensitive).put("government_document_blocked",tax.sensitive).put("private_chat_blocked",chat.sensitive).put("normal_design_blocked",normal.sensitive).put("policy","fail_closed_before_cloud");
            update(db,runId,"privacy_guard_synthetic",ok?"pass":"fail",ok?"ok":"error",ok?"Privacy Guard v2 blocked synthetic auth code, government document and private chat before cloud while allowing a normal design reference.":"Privacy Guard v2 failed one or more realistic local-only cases.",e);
        }catch(Exception ignored){}
    }

    private static void featureReadiness(VaultDb db,long runId){
        try{
            SQLiteDatabase s=db.getReadableDatabase();
            long rel=count(s,"SELECT COUNT(*) FROM relations"),fac=count(s,"SELECT COUNT(*) FROM memory_facets"),packs=count(s,"SELECT COUNT(*) FROM context_packs");
            if(rel==0&&fac==0&&packs==0)update(db,runId,"memory_graph","warn","warning","Graph tables are healthy, but Memory Connections is not functionally populated yet: relations=0 • facets=0 • context packs=0.",new JSONObject().put("technical_health","pass").put("feature_readiness","not_ready").put("relations",rel).put("facets",fac).put("context_packs",packs));

            long people=table(s,"entities")?count(s,"SELECT COUNT(*) FROM entities WHERE upper(kind) IN ('PERSON','PEOPLE','CONTACT')"):0;
            long personFacets=table(s,"memory_facets")?count(s,"SELECT COUNT(*) FROM memory_facets WHERE upper(facet_type) LIKE '%PERSON%'"):0;
            if(personFacets==0)update(db,runId,"people_memory","warn","warning","People entities exist ("+people+") but person-memory facets/relationships are still 0, so People Memory is data-present but not fully ready.",new JSONObject().put("technical_health","pass").put("feature_readiness","partial").put("person_entities",people).put("person_facets",personFacets));

            long projectEntities=table(s,"entities")?count(s,"SELECT COUNT(*) FROM entities WHERE upper(kind)='PROJECT'"):0;
            long packItems=table(s,"context_pack_items")?count(s,"SELECT COUNT(*) FROM context_pack_items"):0;
            if(packs==0)update(db,runId,"projects_context","warn","warning","Project entities exist ("+projectEntities+") but context packs are 0, so automatic project context is not ready yet.",new JSONObject().put("technical_health","pass").put("feature_readiness","partial").put("project_entities",projectEntities).put("context_packs",packs).put("context_pack_items",packItems));

            long prompts=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE lower(COALESCE(tags,'')) LIKE '%prompt%' OR lower(COALESCE(category,'')) LIKE '%ai%'");
            long examples=table(s,"examples")?count(s,"SELECT COUNT(*) FROM examples"):0;
            if(prompts>0&&examples==0)update(db,runId,"prompt_library","warn","warning",prompts+" prompt/AI memories are queryable, but example/rating rows are 0; capture works while reusable prompt-result learning is not ready.",new JSONObject().put("technical_health","pass").put("feature_readiness","partial").put("prompt_memories",prompts).put("examples",examples));

            long audio=table(s,"audio_info")?count(s,"SELECT COUNT(*) FROM audio_info"):0;
            if(audio>0)update(db,runId,"audio_asr","warn","warning",audio+" audio record(s) exist and the pipeline is readable, but this audit did not compare transcript text against source audio. ASR quality is therefore NOT certified by this run.",new JSONObject().put("technical_health","pass").put("quality_certified",false).put("audio_records",audio));
        }catch(Exception ignored){}
    }

    private static void warmLocalQwen(Context ctx,VaultDb db,long runId){
        try{
            if(!LocalModelManager.installed(ctx)){insert(db,runId,"local_qwen_warm_cache","Local Brain","Warm Qwen reuse","auto","not_run","info","Local Qwen is not ready, so warm-cache latency could not be measured.",new JSONObject());return;}
            long t=SystemClock.elapsedRealtime();LocalLlmBridge.CompletionResult r=LocalLlmBridge.completeCached(LocalModelManager.modelFile(ctx).getAbsolutePath(),"Reply with exactly CORTEX_WARM_OK and nothing else. /no_think","Output exactly CORTEX_WARM_OK. No explanation. /no_think",24);long wall=SystemClock.elapsedRealtime()-t;
            String text=r.getText()==null?"":r.getText().replaceAll("(?s)<think>.*?</think>","").trim();boolean correct=text.toUpperCase(Locale.ROOT).contains("CORTEX_WARM_OK");boolean warm=r.getCacheHit()&&r.getModelLoadMs()==0;String st=correct&&warm&&wall<15000?"pass":"warn";JSONObject e=new JSONObject().put("cache_hit",r.getCacheHit()).put("model_load_ms",r.getModelLoadMs()).put("generation_ms",r.getGenerationMs()).put("wall_latency_ms",wall).put("tokens",r.getTokensGenerated()).put("tokens_per_second",r.getTokensPerSecond()).put("response_ok",correct).put("model_cache_age_ms",LocalLlmBridge.cachedModelAgeMs());
            insert(db,runId,"local_qwen_warm_cache","Local Brain","Warm Qwen reuse","auto",st,"pass".equals(st)?"ok":"warning",warm?"Warm local Qwen reused the already-loaded model in "+wall+" ms.":"Local Qwen answered, but the warm-cache path was not proven; model load="+r.getModelLoadMs()+" ms • wall="+wall+" ms.",e);
        }catch(Throwable e){try{insert(db,runId,"local_qwen_warm_cache","Local Brain","Warm Qwen reuse","auto","warn","warning","Warm-cache diagnostic failed: "+e.getClass().getSimpleName()+": "+nz(e.getMessage()),new JSONObject().put("exception",e.toString()));}catch(Exception ignored){}}
    }

    private static void interactionCoverage(VaultDb db,long runId){
        try{
            InteractionTelemetry.ensure(db);long rows=count(db.getReadableDatabase(),"SELECT COUNT(*) FROM interaction_telemetry");JSONObject latest=InteractionTelemetry.latest(db,"ask_cortex","complete");String st=rows>0?"pass":"warn";insert(db,runId,"interaction_telemetry","Diagnostics","Interaction latency telemetry","auto",st,"pass".equals(st)?"ok":"warning",rows+" interaction timing event(s) are stored. Debug export can now separate retrieval, model load and generation latency instead of reporting only total slowness.",new JSONObject().put("rows",rows).put("latest_ask_complete",latest));
        }catch(Exception ignored){}
    }

    private static KnowledgeItem fake(String text){long n=System.currentTimeMillis();return new KnowledgeItem(-1,"SCREENSHOT","audit","Audit synthetic", "",text,"","","","","analyzed","audit","","{}",n,n);}

    private static void update(VaultDb db,long runId,String key,String status,String severity,String detail,JSONObject evidence){ContentValues v=new ContentValues();v.put("status",status);v.put("passed","pass".equals(status)?1:("fail".equals(status)?0:-1));v.put("severity",severity);v.put("detail",detail);v.put("evidence_json",evidence==null?"{}":evidence.toString());v.put("finished_at",System.currentTimeMillis());db.getWritableDatabase().update("cortex_audit_tests",v,"run_id=? AND test_key=?",new String[]{String.valueOf(runId),key});}
    private static void insert(VaultDb db,long runId,String key,String feature,String title,String mode,String status,String severity,String detail,JSONObject evidence){ContentValues v=new ContentValues();v.put("run_id",runId);v.put("test_key",key);v.put("feature",feature);v.put("title",title);v.put("mode",mode);v.put("status",status);v.put("passed","pass".equals(status)?1:("fail".equals(status)?0:-1));v.put("severity",severity);v.put("description","v49 hardening diagnostic");v.put("expected","Useful latency/readiness evidence is explicit in the debug export.");v.put("started_at",System.currentTimeMillis());v.put("finished_at",System.currentTimeMillis());v.put("duration_ms",0);v.put("detail",detail);v.put("evidence_json",evidence==null?"{}":evidence.toString());db.getWritableDatabase().insertWithOnConflict("cortex_audit_tests",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static long count(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static boolean table(SQLiteDatabase s,String n){Cursor c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{n});boolean b=c.moveToFirst();c.close();return b;}
    private static String nz(String s){return s==null?"":s;}
}
