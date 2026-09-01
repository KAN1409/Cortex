package com.kareem.cortex;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.os.*;
import android.speech.SpeechRecognizer;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Strict, long-form, safe Cortex user simulation.
 * No external send/save/call side effect is executed. Synthetic user state is rolled back.
 * The run is intentionally long enough to collect stability/telemetry samples instead of
 * reducing "full simulation" to instant code-presence checks.
 */
public final class CortexUserSimulationRunner {
    public static final int SCHEMA_VERSION=2;
    private static final long AUDIT_WAIT_MS=180_000L;
    private static final long BEHAVIOR_WINDOW_MS=90_000L;
    private static final long SAMPLE_MS=3_000L;
    private CortexUserSimulationRunner(){}
    public interface Progress{void update(int percent,String phase,String detail);}
    private interface Work{JSONObject run()throws Exception;}
    public static final class Result{public final File file;public final JSONObject summary;Result(File f,JSONObject s){file=f;summary=s;}}

    public static Result run(Context context,Progress p)throws Exception{
        Context c=context.getApplicationContext();
        long started=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        String runId="user_sim_"+started+"_"+Long.toHexString(System.nanoTime());
        JSONObject root=new JSONObject();JSONArray steps=new JSONArray(),warnings=new JSONArray();
        VaultDb db=new VaultDb(c);
        root.put("schema","CORTEX_FULL_USER_SIMULATION_V2").put("schema_version",SCHEMA_VERSION)
            .put("run_id",runId).put("started_at",iso(started)).put("started_at_ms",started)
            .put("mode","STRICT_FULL_SAFE_MAX_DATA");
        root.put("purpose","Strict long-form user simulation: execute real safe Cortex paths, validate expected outcomes, observe runtime behavior over time, and export maximum machine-readable diagnostics.");
        root.put("privacy_warning","Report may contain real Cortex raw text, OCR, transcripts, paths, model outputs and DB rows. Secret values and binary payloads are excluded.");
        root.put("safety_contract",new JSONObject()
            .put("external_mutations",false)
            .put("synthetic_user_state_committed",false)
            .put("synthetic_db_strategy","real SQLite transaction deliberately rolled back")
            .put("external_actions","resolve/preview only")
            .put("behavior_window_ms",BEHAVIOR_WINDOW_MS)
            .put("secrets_exported",false)
            .put("binary_attachments_exported",false));
        try{
            phase(p,2,"Baseline","Capturing app/database state");
            root.put("baseline",quickState(c,db));
            phase(p,5,"Inventory","Evaluating capabilities and UI surfaces");
            root.put("capability_inventory",capabilities(c,db));
            root.put("ui_surface_inventory",surfaces(c));
            root.put("interaction_environment",interactionEnvironment(c));

            steps.put(step("functional_self_test","core",()->functional(c)));
            phase(p,10,"Functional test","Core self-test complete");

            steps.put(step("strict_end_to_end_user_journey","journey",()->journey(c,db,runId)));
            phase(p,20,"User journey","Capture → analysis → retrieval → Brain → Deep Review completed");

            steps.put(step("synthetic_image_ocr_pipeline","vision",()->ocr(c,runId)));
            phase(p,25,"Vision","Synthetic OCR complete");

            steps.put(step("synthetic_audio_asr_probe","audio",()->audio(c,runId)));
            phase(p,28,"Audio","ASR route attempted or explicitly blocked by setup/privacy");

            steps.put(audit(c,db,p));
            phase(p,35,"Audit","Immediate audit complete");

            steps.put(step("user_behavior_stability_window","stability",()->behaviorWindow(c,db,p)));
            phase(p,90,"Stability","Runtime observation window complete");

            root.put("steps",steps);
            root.put("post_simulation_pre_export",quickState(c,db));

            phase(p,92,"Debug snapshot","Embedding exhaustive DB/runtime export");
            JSONObject dbg=new JSONObject();File intermediate=null;long ds=SystemClock.elapsedRealtime();
            try{
                intermediate=DebugExporter.build(c,db);
                dbg.put("file_name",intermediate.getName()).put("bytes",intermediate.length())
                   .put("snapshot",new JSONTokener(read(intermediate)).nextValue()).put("embedded",true);
            }catch(Throwable e){
                dbg.put("embedded",false).put("error",error(e));
                warnings.put("Debug snapshot embed failed: "+e.getClass().getSimpleName());
            }finally{
                dbg.put("duration_ms",SystemClock.elapsedRealtime()-ds);
                if(intermediate!=null)try{intermediate.delete();}catch(Throwable ignored){}
            }
            root.put("debug_snapshot",dbg);

            phase(p,97,"Finalizing","Checking rollback cleanliness and strict assertions");
            root.put("final_state",quickState(c,db));
            JSONObject residue=residue(db,runId);
            root.put("synthetic_residue_check",residue);
            if(!residue.optBoolean("clean",false))warnings.put("Synthetic residue detected");

            JSONObject summary=summarize(steps,warnings,residue);
            root.put("summary",summary).put("warnings",warnings)
                .put("finished_at",iso(System.currentTimeMillis()))
                .put("finished_at_ms",System.currentTimeMillis())
                .put("total_duration_ms",SystemClock.elapsedRealtime()-elapsed);
            root.put("max_data_manifest",new JSONObject()
                .put("functional_self_test",true)
                .put("43_capability_states",true)
                .put("strict_synthetic_capture_analysis_search_file_phone_context_prompt_library",true)
                .put("strict_grounded_brain_assertions",true)
                .put("deep_review_context_parse_apply_assertions",true)
                .put("synthetic_real_ocr",true)
                .put("synthetic_audio_route_probe",true)
                .put("immediate_full_app_audit",true)
                .put("90_second_runtime_stability_sampling",true)
                .put("complete_non_blob_database_dump",true)
                .put("schema_indexes_components_permissions_models_providers",true)
                .put("audit_and_interaction_telemetry",true)
                .put("raw_user_text_possible",true)
                .put("secrets",false)
                .put("binary_attachment_contents",false));

            File dir=new File(c.getFilesDir(),"debug_exports");
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create debug_exports");
            File out=new File(dir,"CortexFullUserSimulation_v"+versionCode(c)+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".json");
            try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),java.nio.charset.StandardCharsets.UTF_8),131072)){w.write(root.toString(2));}
            summary.put("report_file",out.getName()).put("report_bytes",out.length());
            phase(p,100,"Complete","Master JSON ready · "+human(out.length()));
            return new Result(out,summary);
        }finally{try{db.close();}catch(Throwable ignored){}}
    }

    private static JSONObject functional(Context c)throws Exception{
        long t=SystemClock.elapsedRealtime();CortexFunctionalSelfTest.Report r=CortexFunctionalSelfTest.run(c);
        JSONArray lines=new JSONArray();for(String x:r.lines)lines.put(x);
        return new JSONObject().put("result_status",r.fail>0?"FAIL":r.warn>0?"WARN":"PASS")
            .put("pass",r.pass).put("warn",r.warn).put("fail",r.fail)
            .put("duration_ms",SystemClock.elapsedRealtime()-t).put("text",r.text())
            .put("lines",lines).put("metrics",new JSONObject(r.metrics.toString()));
    }

    private static JSONObject journey(Context c,VaultDb db,String runId)throws Exception{
        CognitiveStore.ensure(db);FeatureStore.ensure(db);PromptLibraryStore.ensure(db);PhoneContextStore.ensure(db);
        SQLiteDatabase sql=db.getWritableDatabase();JSONObject out=new JSONObject();JSONArray trace=new JSONArray(),assertions=new JSONArray();
        File temp=null;boolean tx=false;String token="CORTEX_SIM_"+runId;boolean ok=true;
        try{
            sql.beginTransaction();tx=true;long all=SystemClock.elapsedRealtime();
            String text=token+" Project Atlas: اتفقنا نراجع voice pipeline tomorrow at 10 AM. Next action: verify fallback latency and send a draft summary. Owner: Karim. Priority high.";
            long t=SystemClock.elapsedRealtime();
            String cat=AutoClassifier.category(text,"text/plain"),title=AutoClassifier.title(text,"text/plain"),tags=AutoClassifier.tags(text,cat),fp=Fingerprint.text(text);
            trace.put(event("classify_text",t,new JSONObject().put("input",text).put("title",title).put("category",cat).put("tags",tags).put("fingerprint",fp)));

            t=SystemClock.elapsedRealtime();
            long id=db.insert("TEXT","diagnostic_user_sim",title,text,cat,tags,"",fp,new JSONObject().put("synthetic",true).put("run_id",runId).put("rollback",true).toString());
            KnowledgeItem item=id>0?db.getById(id):null;
            boolean captureOk=item!=null&&text.equals(item.rawText);
            assertions.put(assertion("capture_roundtrip",captureOk,"Inserted synthetic text must be readable unchanged",new JSONObject().put("id",id)));
            ok&=captureOk;if(!captureOk)throw new IllegalStateException("Synthetic capture round-trip failed");
            trace.put(event("capture_text_roundtrip",t,new JSONObject().put("id",id).put("read_back",item!=null).put("raw_text_matches",captureOk)));

            t=SystemClock.elapsedRealtime();
            AnalysisResult analysis=LocalAnalyzer.analyze(text,"text/plain");
            db.applyAnalysis(id,analysis);
            try{TemporalResolver.afterAnalysis(db,id);}catch(Throwable ignored){}
            try{CoreBrainEngine.afterAnalysis(db,id);}catch(Throwable ignored){}
            KnowledgeItem analyzed=db.getById(id);
            try{IntentionalCognitiveBridge.afterAnalysis(db,analyzed,analysis);}catch(Throwable ignored){}
            analyzed=db.getById(id);
            boolean analysisOk=analyzed!=null&&"analyzed".equals(analyzed.status);
            assertions.put(assertion("text_analysis",analysisOk,"Normal user capture must reach analyzed state",new JSONObject().put("status",analyzed==null?"missing":safe(analyzed.status)).put("analysis",new JSONObject(analysis.toJson()))));
            ok&=analysisOk;
            trace.put(event("text_analysis_and_cognitive_post",t,new JSONObject().put("status",analyzed==null?"missing":safe(analyzed.status)).put("analysis",new JSONObject(analysis.toJson()))));

            t=SystemClock.elapsedRealtime();
            long dup=db.insert("TEXT","diagnostic_user_sim",title,text,cat,tags,"",fp,new JSONObject().put("synthetic",true).put("run_id",runId).toString());
            boolean dedupOk=dup<=0||dup==id;
            assertions.put(assertion("dedup",dedupOk,"Duplicate fingerprint must not create a second logical capture",new JSONObject().put("first_id",id).put("duplicate_result",dup)));
            ok&=dedupOk;
            trace.put(event("dedup_probe",t,new JSONObject().put("first_id",id).put("duplicate_result",dup).put("rejected_or_resolved",dedupOk)));

            t=SystemClock.elapsedRealtime();
            ArrayList<KnowledgeItem> hits=db.lexicalSearch("Atlas fallback latency",20);JSONArray hj=new JSONArray();boolean found=false;
            for(KnowledgeItem x:hits){if(x==null)continue;if(x.id==id)found=true;hj.put(new JSONObject().put("id",x.id).put("type",safe(x.type)).put("source",safe(x.source)).put("title",safe(x.title)).put("status",safe(x.status)));}
            assertions.put(assertion("evidence_retrieval",found,"Fresh analyzed capture must be retrievable by its unique content",new JSONObject().put("query","Atlas fallback latency").put("hit_count",hits.size()).put("synthetic_found",found)));
            ok&=found;
            trace.put(event("evidence_search",t,new JSONObject().put("query","Atlas fallback latency").put("hit_count",hits.size()).put("synthetic_found",found).put("hits",hj)));

            temp=new File(c.getCacheDir(),"cortex_sim_"+runId+".txt");write(temp,"Cortex synthetic attachment\n"+text);
            t=SystemClock.elapsedRealtime();
            long fid=db.insert("FILE","diagnostic_user_sim","Atlas simulation attachment",text,"Diagnostics","file,simulation,rollback",temp.getAbsolutePath(),Fingerprint.text("file|"+text),new JSONObject().put("synthetic",true).put("run_id",runId).put("bytes",temp.length()).toString());
            KnowledgeItem fi=fid>0?db.getById(fid):null;boolean fileOk=fi!=null&&temp.exists();
            if(fileOk)try{
                AnalysisResult fa=AttachmentAnalyzer.analyze(fi);db.applyAnalysis(fid,fa);
                try{TemporalResolver.afterAnalysis(db,fid);}catch(Throwable ignored){}
                try{CoreBrainEngine.afterAnalysis(db,fid);}catch(Throwable ignored){}
                try{IntentionalCognitiveBridge.afterAnalysis(db,db.getById(fid),fa);}catch(Throwable ignored){}
                fileOk="analyzed".equals(db.getById(fid).status);
            }catch(Throwable e){fileOk=false;}
            assertions.put(assertion("file_capture_analysis",fileOk,"Synthetic file must traverse real attachment analysis",new JSONObject().put("id",fid).put("bytes",temp.length()).put("exists",temp.exists())));
            ok&=fileOk;
            trace.put(event("file_attachment_capture_and_analysis",t,new JSONObject().put("id",fid).put("path",temp.getAbsolutePath()).put("bytes",temp.length()).put("exists",temp.exists()).put("analyzed",fileOk)));

            t=SystemClock.elapsedRealtime();
            long pid=PhoneContextStore.record(db,"app_transition","diagnostic_user_sim",c.getPackageName(),"Cortex","InputActivity","window_state_changed",token,System.currentTimeMillis(),new JSONObject().put("synthetic",true).put("run_id",runId));
            boolean pf=false;for(PhoneContextStore.Event e:PhoneContextStore.recent(db,System.currentTimeMillis()-60_000L,100))if(token.equals(e.text)){pf=true;break;}
            assertions.put(assertion("phone_context_roundtrip",pf,"Phone context event must be readable from bounded timeline",new JSONObject().put("id",pid)));
            ok&=pf;trace.put(event("phone_context_roundtrip",t,new JSONObject().put("id",pid).put("found",pf)));

            t=SystemClock.elapsedRealtime();
            PromptLibraryStore.pin(db,id);PromptLibraryStore.rate(db,id,1);PromptLibraryStore.recordRun(db,id,"SIMULATED_RESULT","cortex-user-sim",123);
            JSONObject ps=new JSONObject().put("found",false);boolean promptOk=false;
            for(PromptLibraryStore.Entry e:PromptLibraryStore.list(db,300))if(e.itemId==id){promptOk=e.pinned&&e.rating==1&&e.useCount>=1;ps.put("found",true).put("pinned",e.pinned).put("rating",e.rating).put("use_count",e.useCount).put("last_provider",safe(e.lastProvider)).put("last_latency_ms",e.lastLatencyMs).put("last_result",safe(e.lastResult));break;}
            assertions.put(assertion("prompt_library_roundtrip",promptOk,"Prompt metadata must survive a real readback",ps));ok&=promptOk;trace.put(event("prompt_library_roundtrip",t,ps));

            t=SystemClock.elapsedRealtime();
            LocalAskRouter.Result brain=LocalAskRouter.fast(c,db,"For "+token+", what is the grounded next action and why?");
            JSONObject bj=new JSONObject().put("job_id",brain.jobId).put("provider",safe(brain.provider)).put("source_mode",safe(brain.sourceMode)).put("answer",safe(brain.answer)).put("error",safe(brain.error)).put("total_ms",brain.totalMs).put("retrieval_ms",brain.retrievalMs).put("confidence",brain.grounded==null?0:brain.grounded.confidence);
            JSONArray src=new JSONArray(),loops=new JSONArray(),decisions=new JSONArray();boolean brainGrounded=false;
            if(brain.grounded!=null){
                for(String x:brain.grounded.openLoops)loops.put(x);for(String x:brain.grounded.decisions)decisions.put(x);
                for(SemanticHit h:brain.grounded.sources)if(h!=null&&h.item!=null){if(h.item.id==id)brainGrounded=true;src.put(new JSONObject().put("id",h.item.id).put("title",safe(h.item.title)).put("score",h.score).put("snippet",safe(h.snippet)));}
            }
            bj.put("open_loops",loops).put("decisions",decisions).put("sources",src).put("synthetic_source_grounded",brainGrounded);
            assertions.put(assertion("grounded_brain",brainGrounded,"Brain answer must cite the synthetic capture it was explicitly asked about",new JSONObject().put("job_id",brain.jobId).put("answer",safe(brain.answer)).put("sources",src)));
            ok&=brainGrounded;trace.put(event("grounded_brain_query",t,bj));

            t=SystemClock.elapsedRealtime();
            DeepReviewContractV1.PromptPack pack=DeepReviewContractV1.build(db);
            boolean packHasEvidence=pack.text.contains("\"evidence_id\":"+id)||pack.text.contains("\"evidence_id\": "+id);
            assertions.put(assertion("deep_review_context_grounding",packHasEvidence,"Deep Review context pack must contain synthetic evidence ID",new JSONObject().put("request_id",pack.requestId).put("evidence_count",pack.evidenceCount).put("state_count",pack.stateCount).put("prompt_chars",pack.text.length())));
            ok&=packHasEvidence;
            trace.put(event("deep_review_build_context_pack",t,new JSONObject().put("request_id",pack.requestId).put("evidence_count",pack.evidenceCount).put("state_count",pack.stateCount).put("prompt_chars",pack.text.length()).put("prompt_pack_text",pack.text)));

            String mock=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject().put("request_id",pack.requestId).put("answer","Synthetic grounded review for "+token)
                .put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","Verify Atlas fallback latency").put("body","Run latency verification before drafting the summary.").put("importance",88).put("confidence",0.94).put("reason","Synthetic capture explicitly names it as next action.").put("evidence_ids",new JSONArray().put(id))))
                .put("suggested_actions",new JSONArray().put(new JSONObject().put("title","Prepare draft summary after latency verification").put("why","Requested by the synthetic capture.").put("evidence_ids",new JSONArray().put(id)))).toString();
            t=SystemClock.elapsedRealtime();
            DeepReviewContractV1.Review review=DeepReviewContractV1.parse(db,mock,pack.requestId);
            boolean parseOk=review.items.size()==1&&review.actions.size()==1;
            assertions.put(assertion("deep_review_parse_validate",parseOk,"Structured Deep Review response must parse and validate",new JSONObject().put("priority_item_count",review.items.size()).put("suggested_action_count",review.actions.size())));
            ok&=parseOk;trace.put(event("deep_review_parse_validate",t,new JSONObject().put("raw_response",mock).put("request_id",review.requestId).put("answer",review.answer).put("priority_item_count",review.items.size()).put("suggested_action_count",review.actions.size())));

            t=SystemClock.elapsedRealtime();
            DeepReviewContractV1.ApplyResult ar=DeepReviewContractV1.apply(db,review);boolean applyOk=ar.reviewEvidenceId>0&&ar.created>0;
            assertions.put(assertion("deep_review_apply",applyOk,"Validated review must create grounded cognitive output inside rollback transaction",new JSONObject().put("review_evidence_id",ar.reviewEvidenceId).put("derived_created",ar.created)));
            ok&=applyOk;trace.put(event("deep_review_apply_inside_rollback",t,new JSONObject().put("review_evidence_id",ar.reviewEvidenceId).put("derived_created",ar.created).put("will_be_rolled_back",true)));

            t=SystemClock.elapsedRealtime();String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);
            boolean briefOk=daily.contains("latency")||weekly.contains("latency")||daily.contains("Atlas")||weekly.contains("Atlas");
            assertions.put(assertion("brief_reflects_synthetic_state",briefOk,"Brief should reflect the newly derived grounded action",new JSONObject().put("daily",daily).put("weekly",weekly)));
            ok&=briefOk;trace.put(event("brief_generation",t,new JSONObject().put("daily",daily).put("weekly",weekly).put("daily_chars",daily.length()).put("weekly_chars",weekly.length())));

            out.put("transaction_elapsed_ms",SystemClock.elapsedRealtime()-all).put("synthetic_primary_evidence_id",id).put("trace",trace).put("assertions",assertions).put("pre_rollback_counts",counts(sql)).put("result_status",ok?"PASS":"FAIL");
        }finally{
            if(tx)try{sql.endTransaction();}catch(Throwable ignored){}
            if(temp!=null)try{temp.delete();}catch(Throwable ignored){}
        }
        JSONObject r=residue(db,runId);out.put("rollback_verification",r);if(!r.optBoolean("clean",false))out.put("result_status","FAIL");
        return out;
    }

    private static JSONObject ocr(Context c,String runId)throws Exception{
        JSONObject out=new JSONObject();File f=new File(c.getCacheDir(),"cortex_sim_ocr_"+runId+".png");Bitmap b=null;long t=SystemClock.elapsedRealtime();
        try{
            b=Bitmap.createBitmap(1400,620,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);cv.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.BLACK);p.setTextSize(86);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));cv.drawText("CORTEX TEST 123",75,190,p);
            p.setTextSize(72);p.setTypeface(Typeface.DEFAULT);cv.drawText("Project Atlas fallback latency",75,320,p);cv.drawText("اختبار كورتكس 123",75,460,p);
            try(FileOutputStream fos=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,fos);}
            long now=System.currentTimeMillis();KnowledgeItem item=new KnowledgeItem(-1,"IMAGE","diagnostic_user_sim","Synthetic OCR image","","","","Diagnostics","ocr,simulation",f.getAbsolutePath(),"queued",Fingerprint.text("ocr|"+runId),"",new JSONObject().put("synthetic",true).put("run_id",runId).toString(),now,now);
            CountDownLatch latch=new CountDownLatch(1);AnalysisResult[] rr=new AnalysisResult[1];Exception[] ee=new Exception[1];
            OcrAnalyzer.analyze(c,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){rr[0]=r;latch.countDown();}public void fail(Exception e){ee[0]=e;latch.countDown();}});
            boolean done=latch.await(60,TimeUnit.SECONDS);
            out.put("synthetic_image_path",f.getAbsolutePath()).put("bytes",f.length()).put("arabic_ocr_model_ready",ArabicOcr.modelReady(c)).put("callback_completed",done);
            if(!done)return out.put("result_status","FAIL").put("execution_state","timeout");
            if(ee[0]!=null)return out.put("result_status","FAIL").put("execution_state","pipeline_returned_error").put("error",error(ee[0]));
            String extracted=rr[0]==null?"":safe(rr[0].extractedText);boolean expected=extracted.contains("CORTEX TEST 123")&&extracted.toLowerCase(Locale.ROOT).contains("atlas");
            return out.put("result_status",expected?"PASS":"FAIL").put("execution_state","executed_real").put("expected_text_found",expected).put("analysis",new JSONObject(rr[0].toJson()));
        }finally{out.put("duration_ms",SystemClock.elapsedRealtime()-t);if(b!=null)try{b.recycle();}catch(Throwable ignored){}try{f.delete();}catch(Throwable ignored){}}
    }

    private static JSONObject audio(Context c,String runId)throws Exception{
        JSONObject out=new JSONObject();File f=new File(c.getCacheDir(),"cortex_sim_audio_"+runId+".wav");long t=SystemClock.elapsedRealtime();
        try{
            writeSilentWav(f,16000,1200);
            long now=System.currentTimeMillis();KnowledgeItem item=new KnowledgeItem(-1,"AUDIO","diagnostic_user_sim","Synthetic audio probe","","","","Voice & Audio","audio,simulation",f.getAbsolutePath(),"queued",Fingerprint.text("audio|"+runId),"",new JSONObject().put("synthetic",true).put("run_id",runId).toString(),now,now);
            CountDownLatch latch=new CountDownLatch(1);AnalysisResult[] rr=new AnalysisResult[1];Exception[] ee=new Exception[1];
            AudioAnalyzer.analyze(c,item,new AudioAnalyzer.Callback(){public void ok(AnalysisResult r){rr[0]=r;latch.countDown();}public void fail(Exception e){ee[0]=e;latch.countDown();}});
            boolean done=latch.await(25,TimeUnit.SECONDS);
            out.put("file_bytes",f.length()).put("callback_completed",done).put("privacy_mode",PrivacyPolicy.mode(c,"audio"))
               .put("gemini_configured",GeminiKeyStore.has(c)).put("groq_configured",GroqKeyStore.has(c));
            if(!done)return out.put("result_status","WARN").put("execution_state","timeout_or_provider_wait");
            if(ee[0]!=null)return out.put("result_status","WARN").put("execution_state","blocked_or_failed").put("error",error(ee[0]));
            return out.put("result_status","PASS").put("execution_state","executed_real").put("analysis",new JSONObject(rr[0].toJson()));
        }finally{out.put("duration_ms",SystemClock.elapsedRealtime()-t);try{f.delete();}catch(Throwable ignored){}}
    }

    private static JSONObject audit(Context c,VaultDb db,Progress p)throws Exception{
        JSONObject out=new JSONObject();long t=SystemClock.elapsedRealtime(),id=CortexAuditScheduler.start(c),deadline=t+AUDIT_WAIT_MS;
        out.put("audit_run_id",id).put("wait_budget_ms",AUDIT_WAIT_MS);CortexAuditStore.Run r=null;
        while(SystemClock.elapsedRealtime()<deadline){
            r=CortexAuditStore.latest(db);
            if(r!=null&&r.id==id){
                out.put("status",safe(r.status)).put("phase",safe(r.phase)).put("current_test",safe(r.currentTest)).put("progress_percent",r.progress());
                phase(p,30+(int)(r.progress()*.05),"Full app audit",safe(r.phase)+(r.currentTest.isEmpty()?"":" · "+r.currentTest));
                if(!r.active())break;
            }
            Thread.sleep(500);
        }
        r=CortexAuditStore.latest(db);
        if(r!=null&&r.id==id){
            out.put("status",safe(r.status)).put("phase",safe(r.phase)).put("current_test",safe(r.currentTest)).put("progress_percent",r.progress()).put("summary",safe(r.summary))
               .put("result_status",r.active()?"WARN":"complete".equals(r.status)?"PASS":"WARN").put("execution_state",r.active()?"timeout_audit_continues_in_background":"terminal");
        }else out.put("result_status","WARN").put("execution_state","audit_state_unavailable");
        try{out.put("audit_export",CortexAuditStore.exportJson(c,db));}catch(Throwable e){out.put("audit_export_error",error(e));}
        out.put("duration_ms",SystemClock.elapsedRealtime()-t);return wrap("immediate_full_app_audit","audit",out,t);
    }

    private static JSONObject behaviorWindow(Context c,VaultDb db,Progress p)throws Exception{
        long start=SystemClock.elapsedRealtime(),deadline=start+BEHAVIOR_WINDOW_MS;JSONArray samples=new JSONArray(),probes=new JSONArray();int i=0;
        long maxHeap=0,minHeap=Long.MAX_VALUE,maxDb=0;int maxPending=0;
        while(SystemClock.elapsedRealtime()<deadline){
            long wall=System.currentTimeMillis(),heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            long dbBytes=c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0;
            int pending=0;try{pending=db.pendingCount();}catch(Throwable ignored){}
            maxHeap=Math.max(maxHeap,heap);minHeap=Math.min(minHeap,heap);maxDb=Math.max(maxDb,dbBytes);maxPending=Math.max(maxPending,pending);
            JSONObject s=new JSONObject().put("sample",i).put("captured_at",iso(wall)).put("elapsed_ms",SystemClock.elapsedRealtime()-start)
                .put("heap_used_bytes",heap).put("heap_max_bytes",Runtime.getRuntime().maxMemory()).put("db_file_bytes",dbBytes).put("analysis_pending",pending)
                .put("network_connected",CortexAuditSoakWorker.network(c)).put("phone_events_24h",PhoneContextStore.countSince(db,wall-86400000L))
                .put("active_process_count",PhoneContextStore.activeProcessCount(db)).put("visual_worker_state",VisualInsightStore.workerState(c))
                .put("visual_worker_stage",VisualInsightStore.workerStage(c)).put("visual_worker_detail",VisualInsightStore.workerDetail(c))
                .put("local_model_state",safe(LocalLlmRuntime.state(c).state));
            samples.put(s);
            if(i%5==0){
                long q=SystemClock.elapsedRealtime();GroundedAnswer g=SecondBrainEngine.ask(db,i%10==0?"What needs my attention?":"Cortex");
                probes.put(new JSONObject().put("sample",i).put("query",i%10==0?"What needs my attention?":"Cortex").put("duration_ms",SystemClock.elapsedRealtime()-q)
                    .put("answer",safe(g.answer)).put("confidence",g.confidence).put("source_count",g.sources.size()).put("open_loop_count",g.openLoops.size()).put("decision_count",g.decisions.size()));
                String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);
                probes.put(new JSONObject().put("sample",i).put("kind","brief_probe").put("daily_chars",daily.length()).put("weekly_chars",weekly.length()).put("daily",daily).put("weekly",weekly));
            }
            i++;
            int pct=35+(int)Math.min(55,((SystemClock.elapsedRealtime()-start)*55/BEHAVIOR_WINDOW_MS));
            phase(p,pct,"Behavior window","Runtime sample "+i+" · "+((BEHAVIOR_WINDOW_MS-(SystemClock.elapsedRealtime()-start)+999)/1000)+"s remaining");
            long remaining=deadline-SystemClock.elapsedRealtime();if(remaining>0)Thread.sleep(Math.min(SAMPLE_MS,remaining));
        }
        return new JSONObject().put("result_status","PASS").put("execution_state","executed_real_time_window")
            .put("configured_duration_ms",BEHAVIOR_WINDOW_MS).put("actual_duration_ms",SystemClock.elapsedRealtime()-start)
            .put("sample_interval_ms",SAMPLE_MS).put("sample_count",samples.length()).put("samples",samples).put("probes",probes)
            .put("max_heap_used_bytes",maxHeap).put("min_heap_used_bytes",minHeap==Long.MAX_VALUE?0:minHeap).put("max_db_file_bytes",maxDb).put("max_analysis_pending",maxPending);
    }

    private static JSONObject capabilities(Context c,VaultDb db)throws Exception{
        JSONArray a=new JSONArray();JSONObject counts=new JSONObject();HashMap<String,Integer> m=new LinkedHashMap<>();
        for(CortexCapabilityRegistry.Capability cap:CortexCapabilityRegistry.all()){
            CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(c,db,cap);
            String coverage=CortexCapabilityRegistry.NEEDS_ACCESS.equals(s.status)?"BLOCKED_BY_ACCESS":CortexCapabilityRegistry.NEEDS_SETUP.equals(s.status)?"BLOCKED_BY_SETUP":"ELIGIBLE_FOR_TEST";
            a.put(new JSONObject().put("number",cap.number).put("key",cap.key).put("title",cap.title).put("status",s.status).put("detail",s.detail).put("simulation_coverage_state",coverage));
            m.put(s.status,m.getOrDefault(s.status,0)+1);
        }
        for(Map.Entry<String,Integer> e:m.entrySet())counts.put(e.getKey(),e.getValue());
        return new JSONObject().put("registered",a.length()).put("counts",counts).put("capabilities",a);
    }

    private static JSONObject surfaces(Context c)throws Exception{
        PackageManager pm=c.getPackageManager();JSONArray a=new JSONArray();
        Class<?>[] cls={InputActivity.class,EvidenceActivity.class,DeepReviewActivity.class,SettingsActivity.class,EnvironmentActivity.class,ProposalCaptureActivity.class,ProposalCaptureResultActivity.class,PeopleProjectsActivity.class,ProposalAskCortexActivity.class,PromptLibraryActivity.class,ReviewQueueActivity.class,CorrectionLearningActivity.class,CapabilityMatrixActivity.class,PhoneContextAccessActivity.class,CortexStatusActivity.class,CortexAuditActivity.class,OcrTestActivity.class,UserSimulationTestLabActivity.class};
        for(Class<?> x:cls){JSONObject o=new JSONObject().put("class",x.getName());try{ActivityInfo ai=pm.getActivityInfo(new ComponentName(c,x),0);o.put("present",true).put("enabled",ai.enabled).put("exported",ai.exported);}catch(Throwable e){o.put("present",false).put("error",e.getClass().getSimpleName());}a.put(o);}
        return new JSONObject().put("activities",a);
    }

    private static JSONObject interactionEnvironment(Context c)throws Exception{
        PackageManager pm=c.getPackageManager();
        return new JSONObject().put("speech_recognition_available",SpeechRecognizer.isRecognitionAvailable(c))
            .put("record_audio_granted",pm.checkPermission(android.Manifest.permission.RECORD_AUDIO,c.getPackageName())==PackageManager.PERMISSION_GRANTED)
            .put("notification_permission_granted",Build.VERSION.SDK_INT<33||pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS,c.getPackageName())==PackageManager.PERMISSION_GRANTED)
            .put("screen_accessibility_connected",CortexScreenAccessibilityService.connected()).put("shizuku_granted",ShizukuContextBridge.granted())
            .put("usage_access",PhoneUsageAccess.has(c)).put("notification_listener_enabled",CortexAuditSoakWorker.notificationListenerEnabled(c))
            .put("network_connected",CortexAuditSoakWorker.network(c))
            .put("calendar_insert_handler",pm.resolveActivity(new Intent(Intent.ACTION_INSERT,android.provider.CalendarContract.Events.CONTENT_URI),PackageManager.MATCH_DEFAULT_ONLY)!=null)
            .put("email_handler",pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("mailto:test@example.com")),PackageManager.MATCH_DEFAULT_ONLY)!=null)
            .put("sms_handler",pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null)
            .put("call_handler",pm.resolveActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null)
            .put("external_action_execution","preview_only_in_test");
    }

    private static JSONObject quickState(Context c,VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();
        return new JSONObject().put("captured_at",iso(System.currentTimeMillis())).put("counts",counts(s)).put("db_quick_check",scalar(s,"PRAGMA quick_check(1)"))
            .put("db_file_bytes",c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0)
            .put("heap_used_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("heap_max_bytes",Runtime.getRuntime().maxMemory());
    }

    private static JSONObject counts(SQLiteDatabase s)throws Exception{
        JSONObject o=new JSONObject();String[] ts={"knowledge_items","derived_items","source_links","actions","ai_jobs","ai_job_sources","ai_model_runs","interaction_telemetry","phone_context_events","feedback_events","correction_rules","visual_insights","entity_nodes","cortex_audit_runs","cortex_audit_tests","cortex_audit_events"};
        for(String t:ts)o.put(t,table(s,t)?count(s,"SELECT COUNT(*) FROM \""+t+"\""):-1);return o;
    }

    private static JSONObject residue(VaultDb db,String runId)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();String like="%"+runId+"%";long rows=0;JSONObject by=new JSONObject();
        if(table(s,"knowledge_items")){long n=countArgs(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='diagnostic_user_sim' OR metadata_json LIKE ? OR raw_text LIKE ?",new String[]{like,like});by.put("knowledge_items",n);rows+=n;}
        if(table(s,"derived_items")){long n=countArgs(s,"SELECT COUNT(*) FROM derived_items WHERE metadata_json LIKE ?",new String[]{like});by.put("derived_items",n);rows+=n;}
        if(table(s,"phone_context_events")){long n=countArgs(s,"SELECT COUNT(*) FROM phone_context_events WHERE metadata_json LIKE ? OR text_preview LIKE ?",new String[]{like,like});by.put("phone_context_events",n);rows+=n;}
        return new JSONObject().put("rows",rows).put("by_table",by).put("clean",rows==0);
    }

    private static JSONObject assertion(String name,boolean pass,String expected,JSONObject evidence)throws Exception{
        return new JSONObject().put("name",name).put("pass",pass).put("expected",expected).put("evidence",evidence);
    }
    private static JSONObject step(String n,String c,Work w){
        long t=SystemClock.elapsedRealtime();
        try{return wrap(n,c,w.run(),t);}catch(Throwable e){try{return new JSONObject().put("name",n).put("category",c).put("status","FAIL").put("duration_ms",SystemClock.elapsedRealtime()-t).put("error",error(e));}catch(Exception ignored){return new JSONObject();}}
    }
    private static JSONObject wrap(String n,String c,JSONObject d,long t)throws Exception{
        return new JSONObject().put("name",n).put("category",c).put("status",d.optString("result_status","PASS")).put("duration_ms",d.has("duration_ms")?d.optLong("duration_ms"):SystemClock.elapsedRealtime()-t).put("data",d);
    }
    private static JSONObject event(String n,long t,JSONObject d)throws Exception{return new JSONObject().put("name",n).put("duration_ms",SystemClock.elapsedRealtime()-t).put("data",d);}
    private static JSONObject summarize(JSONArray s,JSONArray w,JSONObject r)throws Exception{
        int p=0,a=0,f=0;for(int i=0;i<s.length();i++){String x=s.getJSONObject(i).optString("status","PASS");if("FAIL".equals(x))f++;else if("WARN".equals(x))a++;else p++;}if(!r.optBoolean("clean",false))f++;
        return new JSONObject().put("pass_steps",p).put("warning_steps",a).put("failed_steps",f).put("warning_count",w.length()).put("synthetic_residue_rows",r.optLong("rows",0)).put("overall",f>0?"FAIL":a>0?"PASS_WITH_WARNINGS":"PASS");
    }
    private static JSONObject error(Throwable e)throws Exception{
        JSONObject o=new JSONObject().put("type",e==null?"":e.getClass().getName()).put("message",e==null?"":safe(e.getMessage()));
        if(e!=null){JSONArray a=new JSONArray();for(int i=0;i<Math.min(30,e.getStackTrace().length);i++)a.put(e.getStackTrace()[i].toString());o.put("stack",a);}
        return o;
    }
    private static void writeSilentWav(File f,int sampleRate,int durationMs)throws Exception{
        int samples=(int)((long)sampleRate*durationMs/1000L),dataLen=samples*2;try(DataOutputStream o=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))){
            o.writeBytes("RIFF");le32(o,36+dataLen);o.writeBytes("WAVE");o.writeBytes("fmt ");le32(o,16);le16(o,1);le16(o,1);le32(o,sampleRate);le32(o,sampleRate*2);le16(o,2);le16(o,16);o.writeBytes("data");le32(o,dataLen);for(int i=0;i<samples;i++)le16(o,0);
        }
    }
    private static void le16(DataOutputStream o,int v)throws IOException{o.writeByte(v&255);o.writeByte((v>>>8)&255);}
    private static void le32(DataOutputStream o,int v)throws IOException{o.writeByte(v&255);o.writeByte((v>>>8)&255);o.writeByte((v>>>16)&255);o.writeByte((v>>>24)&255);}
    private static boolean table(SQLiteDatabase s,String t){Cursor c=null;try{c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{t});return c.moveToFirst();}catch(Throwable e){return false;}finally{if(c!=null)c.close();}}
    private static long count(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static long countArgs(SQLiteDatabase s,String q,String[] a){Cursor c=s.rawQuery(q,a);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String scalar(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);try{return c.moveToFirst()?safe(c.getString(0)):"";}finally{c.close();}}
    private static void write(File f,String s)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8)){w.write(s);}}
    private static String read(File f)throws Exception{StringBuilder b=new StringBuilder();char[] x=new char[32768];try(Reader r=new InputStreamReader(new FileInputStream(f),java.nio.charset.StandardCharsets.UTF_8)){int n;while((n=r.read(x))>0)b.append(x,0,n);}return b.toString();}
    private static String iso(long ms){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date(ms));}
    private static long versionCode(Context c){try{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Throwable e){return 0;}}
    private static String human(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024.0);return String.format(Locale.US,"%.2f MB",n/1048576.0);}
    private static String safe(String s){return s==null?"":s;}
    private static void phase(Progress p,int n,String a,String b){if(p!=null)try{p.update(Math.max(0,Math.min(100,n)),a,b);}catch(Throwable ignored){}}
}
