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
 * Exhaustive user-journey simulation for development diagnostics.
 *
 * The harness deliberately exercises real Cortex code paths. Synthetic database mutations run
 * inside a transaction that is never committed. External side effects (SMS, email, Calendar,
 * calls) are resolution/preview tests only. The final master JSON intentionally includes the
 * normal exhaustive DebugExporter snapshot, so raw text/OCR/transcripts and non-BLOB DB rows may
 * be present. Secret values and binary attachment contents remain excluded.
 */
public final class CortexUserSimulationRunner {
    public static final int SCHEMA_VERSION=1;
    private static final long AUDIT_WAIT_MS=180_000L;
    private CortexUserSimulationRunner(){}

    public interface Progress { void update(int percent,String phase,String detail); }
    private interface Work { JSONObject run() throws Exception; }

    public static final class Result {
        public final File file; public final JSONObject summary;
        Result(File f,JSONObject s){file=f;summary=s;}
    }

    public static Result run(Context context,Progress progress)throws Exception{
        Context c=context.getApplicationContext();long startedWall=System.currentTimeMillis(),startedElapsed=SystemClock.elapsedRealtime();
        JSONObject root=new JSONObject();JSONArray steps=new JSONArray();JSONArray warnings=new JSONArray();
        String runId="user_sim_"+startedWall+"_"+Long.toHexString(System.nanoTime());
        root.put("schema","CORTEX_FULL_USER_SIMULATION_V1");root.put("schema_version",SCHEMA_VERSION);root.put("run_id",runId);
        root.put("started_at_ms",startedWall);root.put("started_at",iso(startedWall));root.put("mode","FULL_SAFE_MAX_DATA");
        root.put("purpose","Simulate broad real user usage across Cortex and export the maximum useful machine-readable diagnostic state without external side effects.");
        root.put("privacy_warning","THIS FILE MAY CONTAIN REAL CORTEX RAW TEXT, OCR, TRANSCRIPTS, PATHS, MODEL OUTPUTS AND DATABASE ROWS. API keys/encrypted secret values and binary attachment payloads are excluded.");
        root.put("safety_contract",new JSONObject()
                .put("external_mutations",false)
                .put("synthetic_db_mutations_committed",false)
                .put("synthetic_db_strategy","single real SQLite transaction intentionally rolled back")
                .put("sms_email_calendar_calls","resolve/preview only")
                .put("secrets_exported",false)
                .put("binary_attachments_exported",false));
        VaultDb db=new VaultDb(c);
        try{
            phase(progress,2,"Baseline","Capturing app/database state before simulation");
            root.put("baseline",quickState(c,db));

            phase(progress,6,"Inventory","Evaluating authoritative Cortex capabilities");
            root.put("capability_inventory",capabilities(c,db));
            root.put("ui_surface_inventory",surfaces(c));
            root.put("interaction_environment",interactionEnvironment(c));

            steps.put(step("functional_self_test","core",()->functional(c)));
            phase(progress,18,"Functional test","Core functional self-test complete");

            steps.put(step("synthetic_end_to_end_user_journey","journey",()->syntheticJourney(c,db,runId)));
            phase(progress,42,"User journey","Synthetic capture → retrieval → Brain → Deep Review path complete and rolled back");

            steps.put(step("synthetic_image_ocr_pipeline","vision",()->syntheticOcr(c,runId)));
            phase(progress,50,"Vision","Synthetic image OCR path complete");

            JSONObject auditStep=runAudit(c,db,progress);steps.put(auditStep);
            phase(progress,76,"Audit","Immediate full-app audit observed");

            root.put("steps",steps);
            root.put("post_simulation_pre_export",quickState(c,db));

            phase(progress,80,"Debug snapshot","Building exhaustive database/runtime snapshot");
            JSONObject debugWrapper=new JSONObject();long dbgStart=SystemClock.elapsedRealtime();File debugFile=null;
            try{
                debugFile=DebugExporter.build(c,db);debugWrapper.put("intermediate_file_name",debugFile.getName());debugWrapper.put("intermediate_file_bytes",debugFile.length());
                Object parsed=new JSONTokener(read(debugFile)).nextValue();debugWrapper.put("snapshot",parsed);debugWrapper.put("embedded",true);debugWrapper.put("duration_ms",SystemClock.elapsedRealtime()-dbgStart);
            }catch(Throwable e){debugWrapper.put("embedded",false);debugWrapper.put("error",error(e));warnings.put("Debug snapshot could not be embedded: "+e.getClass().getSimpleName());}
            finally{if(debugFile!=null)try{debugFile.delete();}catch(Throwable ignored){}}
            root.put("debug_snapshot",debugWrapper);

            phase(progress,93,"Finalizing","Verifying no synthetic residue and summarizing results");
            root.put("final_state",quickState(c,db));
            JSONObject residue=syntheticResidue(db,runId);root.put("synthetic_residue_check",residue);
            if(residue.optLong("rows",0)>0)warnings.put("Synthetic residue detected: "+residue.optLong("rows"));

            JSONObject summary=summarize(steps,warnings,residue);root.put("summary",summary);root.put("warnings",warnings);
            root.put("finished_at_ms",System.currentTimeMillis());root.put("finished_at",iso(System.currentTimeMillis()));root.put("total_duration_ms",SystemClock.elapsedRealtime()-startedElapsed);
            root.put("max_data_manifest",new JSONObject()
                    .put("functional_self_test",true)
                    .put("authoritative_capability_states",true)
                    .put("synthetic_capture_file_phone_context_prompt_library_brain_deep_review",true)
                    .put("synthetic_image_ocr",true)
                    .put("immediate_full_app_audit",true)
                    .put("complete_non_blob_database_dump",true)
                    .put("database_schema_and_indexes",true)
                    .put("runtime_components_permissions_models_providers",true)
                    .put("audit_and_interaction_telemetry_rows",true)
                    .put("raw_user_text_ocr_transcripts_possible",true)
                    .put("secret_values",false)
                    .put("binary_attachment_contents",false));

            File dir=new File(c.getFilesDir(),"debug_exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create debug_exports");
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());File out=new File(dir,"CortexFullUserSimulation_v"+versionCode(c)+"_"+stamp+".json");
            try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out),java.nio.charset.StandardCharsets.UTF_8),131072)){w.write(root.toString(2));}
            summary.put("report_file",out.getName());summary.put("report_bytes",out.length());
            phase(progress,100,"Complete","Master JSON ready · "+human(out.length()));
            return new Result(out,summary);
        }finally{try{db.close();}catch(Throwable ignored){}}
    }

    private static JSONObject functional(Context c)throws Exception{
        long t=SystemClock.elapsedRealtime();CortexFunctionalSelfTest.Report r=CortexFunctionalSelfTest.run(c);JSONObject o=new JSONObject();
        o.put("result_status",r.fail>0?"FAIL":(r.warn>0?"WARN":"PASS"));o.put("pass",r.pass);o.put("warn",r.warn);o.put("fail",r.fail);o.put("duration_ms",SystemClock.elapsedRealtime()-t);o.put("text",r.text());
        JSONArray lines=new JSONArray();for(String x:r.lines)lines.put(x);o.put("lines",lines);o.put("metrics",new JSONObject(r.metrics.toString()));return o;
    }

    private static JSONObject syntheticJourney(Context c,VaultDb db,String runId)throws Exception{
        CognitiveStore.ensure(db);FeatureStore.ensure(db);PromptLibraryStore.ensure(db);PhoneContextStore.ensure(db);
        SQLiteDatabase sql=db.getWritableDatabase();JSONObject out=new JSONObject();JSONArray trace=new JSONArray();File tempFile=null;boolean began=false;String token="CORTEX_SIM_"+runId;
        try{
            sql.beginTransaction();began=true;long t0=SystemClock.elapsedRealtime();
            String text=token+" Project Atlas: اتفقنا نراجع voice pipeline tomorrow at 10 AM. Next action: verify fallback latency and send a draft summary. Owner: Karim. Priority high.";
            String category=AutoClassifier.category(text,"text/plain"),title=AutoClassifier.title(text,"text/plain"),tags=AutoClassifier.tags(text,category),fp=Fingerprint.text(text);
            JSONObject classify=new JSONObject().put("input",text).put("title",title).put("category",category).put("tags",tags).put("fingerprint",fp);trace.put(event("classify_text",t0,classify));

            long capStart=SystemClock.elapsedRealtime();long id=db.insert("TEXT","diagnostic_user_sim",title,text,category,tags,"",fp,new JSONObject().put("synthetic",true).put("run_id",runId).put("rollback",true).toString());
            KnowledgeItem stored=id>0?db.getById(id):null;JSONObject cap=new JSONObject().put("id",id).put("read_back",stored!=null).put("raw_text_matches",stored!=null&&text.equals(stored.rawText)).put("status",stored==null?"":stored.status);trace.put(event("capture_text_roundtrip",capStart,cap));
            if(id<=0||stored==null)throw new IllegalStateException("Synthetic capture insert/read failed");

            long dedupStart=SystemClock.elapsedRealtime();long duplicate=db.insert("TEXT","diagnostic_user_sim",title,text,category,tags,"",fp,new JSONObject().put("synthetic",true).put("duplicate_probe",true).put("run_id",runId).toString());
            trace.put(event("dedup_probe",dedupStart,new JSONObject().put("first_id",id).put("duplicate_insert_result",duplicate).put("duplicate_rejected_or_resolved",duplicate<=0||duplicate==id)));

            long searchStart=SystemClock.elapsedRealtime();ArrayList<KnowledgeItem> hits=db.lexicalSearch("Atlas fallback latency",20);JSONArray hitJson=new JSONArray();boolean found=false;for(KnowledgeItem k:hits){if(k==null)continue;if(k.id==id)found=true;hitJson.put(new JSONObject().put("id",k.id).put("type",safe(k.type)).put("source",safe(k.source)).put("title",safe(k.title)).put("status",safe(k.status)));}
            trace.put(event("evidence_search",searchStart,new JSONObject().put("query","Atlas fallback latency").put("hit_count",hits.size()).put("synthetic_found",found).put("hits",hitJson)));

            tempFile=new File(c.getCacheDir(),"cortex_sim_"+runId+".txt");write(tempFile,"Cortex synthetic attachment\n"+text+"\nThis file exists only during the rolled-back user simulation.");
            long fileStart=SystemClock.elapsedRealtime();long fileId=db.insert("FILE","diagnostic_user_sim","Atlas simulation attachment",text,"Diagnostics","file,simulation,rollback",tempFile.getAbsolutePath(),Fingerprint.text("file|"+text),new JSONObject().put("synthetic",true).put("run_id",runId).put("bytes",tempFile.length()).toString());
            trace.put(event("file_attachment_capture",fileStart,new JSONObject().put("id",fileId).put("path",tempFile.getAbsolutePath()).put("bytes",tempFile.length()).put("exists",tempFile.exists())));

            long phoneStart=SystemClock.elapsedRealtime();long phoneId=PhoneContextStore.record(db,"app_transition","diagnostic_user_sim","com.kareem.cortex","Cortex","InputActivity","window_state_changed",token,System.currentTimeMillis(),new JSONObject().put("synthetic",true).put("run_id",runId));boolean phoneFound=false;for(PhoneContextStore.Event e:PhoneContextStore.recent(db,System.currentTimeMillis()-60_000L,100))if(token.equals(e.text)){phoneFound=true;break;}
            trace.put(event("phone_context_roundtrip",phoneStart,new JSONObject().put("id",phoneId).put("found",phoneFound)));

            long promptStart=SystemClock.elapsedRealtime();PromptLibraryStore.pin(db,id);PromptLibraryStore.rate(db,id,1);PromptLibraryStore.recordRun(db,id,"SIMULATED_RESULT","cortex-user-sim",123);JSONObject promptState=new JSONObject().put("found",false);for(PromptLibraryStore.Entry e:PromptLibraryStore.list(db,300))if(e.itemId==id){promptState.put("found",true).put("pinned",e.pinned).put("rating",e.rating).put("use_count",e.useCount).put("last_provider",safe(e.lastProvider)).put("last_latency_ms",e.lastLatencyMs).put("last_result",safe(e.lastResult));break;}
            trace.put(event("prompt_library_roundtrip",promptStart,promptState));

            long brainStart=SystemClock.elapsedRealtime();LocalAskRouter.Result brain=LocalAskRouter.fast(c,db,"For "+token+", what is the grounded next action and why?");JSONObject brainJson=new JSONObject().put("job_id",brain.jobId).put("provider",safe(brain.provider)).put("source_mode",safe(brain.sourceMode)).put("answer",safe(brain.answer)).put("error",safe(brain.error)).put("total_ms",brain.totalMs).put("retrieval_ms",brain.retrievalMs).put("confidence",brain.grounded==null?0:brain.grounded.confidence).put("open_loops",brain.grounded==null?0:brain.grounded.openLoops.size()).put("decisions",brain.grounded==null?0:brain.grounded.decisions.size());
            JSONArray src=new JSONArray();if(brain.grounded!=null)for(SemanticHit h:brain.grounded.sources)if(h!=null&&h.item!=null)src.put(new JSONObject().put("id",h.item.id).put("title",safe(h.item.title)).put("score",h.score).put("snippet",safe(h.snippet)));brainJson.put("sources",src);trace.put(event("grounded_brain_query",brainStart,brainJson));

            long reviewBuild=SystemClock.elapsedRealtime();DeepReviewContractV1.PromptPack pack=DeepReviewContractV1.build(db);JSONObject packJson=new JSONObject().put("request_id",pack.requestId).put("evidence_count",pack.evidenceCount).put("state_count",pack.stateCount).put("prompt_chars",pack.text.length()).put("prompt_pack_text",pack.text);trace.put(event("deep_review_build_context_pack",reviewBuild,packJson));
            String mock=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject()
                    .put("request_id",pack.requestId).put("answer","Synthetic grounded review for "+token)
                    .put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","Verify Atlas fallback latency").put("body","Run the latency verification before drafting the summary.").put("importance",88).put("confidence",0.94).put("reason","The synthetic capture explicitly names this as the next action.").put("evidence_ids",new JSONArray().put(id))))
                    .put("suggested_actions",new JSONArray().put(new JSONObject().put("title","Prepare draft summary after latency verification").put("why","The synthetic capture requests a draft summary after validation.").put("evidence_ids",new JSONArray().put(id)))).toString();
            long parseStart=SystemClock.elapsedRealtime();DeepReviewContractV1.Review review=DeepReviewContractV1.parse(db,mock,pack.requestId);trace.put(event("deep_review_parse_validate",parseStart,new JSONObject().put("raw_response",mock).put("request_id",review.requestId).put("answer",review.answer).put("priority_item_count",review.items.size()).put("suggested_action_count",review.actions.size())));
            long applyStart=SystemClock.elapsedRealtime();DeepReviewContractV1.ApplyResult applied=DeepReviewContractV1.apply(db,review);trace.put(event("deep_review_apply_inside_rollback",applyStart,new JSONObject().put("review_evidence_id",applied.reviewEvidenceId).put("derived_created",applied.created).put("will_be_rolled_back",true)));

            long briefStart=SystemClock.elapsedRealtime();String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);trace.put(event("brief_generation_with_synthetic_context",briefStart,new JSONObject().put("daily",daily).put("weekly",weekly).put("daily_chars",daily.length()).put("weekly_chars",weekly.length())));

            out.put("transaction_elapsed_ms",SystemClock.elapsedRealtime()-t0);out.put("synthetic_primary_evidence_id",id);out.put("trace",trace);out.put("pre_rollback_counts",quickCounts(sql));out.put("result_status","PASS");
        }catch(Throwable e){out.put("result_status","FAIL");out.put("error",error(e));throw e instanceof Exception?(Exception)e:new RuntimeException(e);}
        finally{
            if(began)try{sql.endTransaction();}catch(Throwable ignored){}
            if(tempFile!=null)try{tempFile.delete();}catch(Throwable ignored){}
        }
        JSONObject residue=syntheticResidue(db,runId);out.put("rollback_verification",residue);if(residue.optLong("rows",0)>0)out.put("result_status","FAIL");return out;
    }

    private static JSONObject syntheticOcr(Context c,String runId)throws Exception{
        JSONObject out=new JSONObject();File f=new File(c.getCacheDir(),"cortex_sim_ocr_"+runId+".png");Bitmap b=null;long start=SystemClock.elapsedRealtime();
        try{
            b=Bitmap.createBitmap(1400,620,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(86);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));canvas.drawText("CORTEX TEST 123",75,190,p);p.setTextSize(72);p.setTypeface(Typeface.DEFAULT);canvas.drawText("Project Atlas fallback latency",75,320,p);canvas.drawText("اختبار كورتكس 123",75,460,p);try(FileOutputStream fos=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,fos);}out.put("synthetic_image_path",f.getAbsolutePath()).put("bytes",f.length()).put("arabic_ocr_model_ready",ArabicOcr.modelReady(c));
            long now=System.currentTimeMillis();KnowledgeItem item=new KnowledgeItem(-1,"IMAGE","diagnostic_user_sim","Synthetic OCR image","","","","Diagnostics","ocr,simulation",f.getAbsolutePath(),"queued",Fingerprint.text("ocr|"+runId),"",new JSONObject().put("synthetic",true).put("run_id",runId).toString(),now,now);
            CountDownLatch latch=new CountDownLatch(1);AnalysisResult[] result=new AnalysisResult[1];Exception[] failure=new Exception[1];
            OcrAnalyzer.analyze(c,item,new OcrAnalyzer.Callback(){public void ok(AnalysisResult r){result[0]=r;latch.countDown();}public void fail(Exception e){failure[0]=e;latch.countDown();}});
            boolean completed=latch.await(60,TimeUnit.SECONDS);out.put("callback_completed",completed);if(!completed){out.put("result_status","WARN").put("execution_state","timeout");return out;}if(failure[0]!=null){out.put("result_status","WARN").put("execution_state","pipeline_returned_error").put("error",error(failure[0]));return out;}
            out.put("result_status","PASS").put("execution_state","executed_real").put("analysis",new JSONObject(result[0].toJson()));return out;
        }finally{out.put("duration_ms",SystemClock.elapsedRealtime()-start);if(b!=null)try{b.recycle();}catch(Throwable ignored){}try{f.delete();}catch(Throwable ignored){}}
    }

    private static JSONObject runAudit(Context c,VaultDb db,Progress progress)throws Exception{
        JSONObject out=new JSONObject();long start=SystemClock.elapsedRealtime();long runId=CortexAuditScheduler.start(c);out.put("audit_run_id",runId).put("wait_budget_ms",AUDIT_WAIT_MS);long deadline=SystemClock.elapsedRealtime()+AUDIT_WAIT_MS;CortexAuditStore.Run state=null;
        while(SystemClock.elapsedRealtime()<deadline){state=CortexAuditStore.latest(db);if(state!=null&&state.id==runId){out.put("status",safe(state.status)).put("phase",safe(state.phase)).put("current_test",safe(state.currentTest)).put("progress_percent",state.progress());phase(progress,55+(int)(state.progress()*.20),"Full app audit",safe(state.phase)+(state.currentTest.isEmpty()?"":" · "+state.currentTest));if(!state.active())break;}Thread.sleep(1200);}
        state=CortexAuditStore.latest(db);if(state!=null&&state.id==runId){out.put("status",safe(state.status)).put("phase",safe(state.phase)).put("current_test",safe(state.currentTest)).put("progress_percent",state.progress()).put("summary",safe(state.summary));out.put("result_status",state.active()?"WARN":("complete".equals(state.status)?"PASS":"WARN"));out.put("execution_state",state.active()?"timeout_audit_continues_in_background":"terminal");}
        else{out.put("result_status","WARN").put("execution_state","audit_state_unavailable");}
        try{out.put("audit_export",CortexAuditStore.exportJson(c,db));}catch(Throwable e){out.put("audit_export_error",error(e));}
        out.put("duration_ms",SystemClock.elapsedRealtime()-start);return wrapStep("immediate_full_app_audit","audit",out,start);
    }

    private static JSONObject capabilities(Context c,VaultDb db)throws Exception{
        JSONArray all=new JSONArray();JSONObject counts=new JSONObject();HashMap<String,Integer> m=new LinkedHashMap<>();for(CortexCapabilityRegistry.Capability cap:CortexCapabilityRegistry.all()){CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(c,db,cap);all.put(new JSONObject().put("number",cap.number).put("key",cap.key).put("title",cap.title).put("status",s.status).put("detail",s.detail));m.put(s.status,m.containsKey(s.status)?m.get(s.status)+1:1);}for(Map.Entry<String,Integer> e:m.entrySet())counts.put(e.getKey(),e.getValue());return new JSONObject().put("registered",all.length()).put("counts",counts).put("capabilities",all);
    }

    private static JSONObject surfaces(Context c)throws Exception{
        PackageManager pm=c.getPackageManager();JSONArray a=new JSONArray();Class<?>[] classes={InputActivity.class,EvidenceActivity.class,DeepReviewActivity.class,SettingsActivity.class,EnvironmentActivity.class,ProposalCaptureActivity.class,ProposalCaptureResultActivity.class,PeopleProjectsActivity.class,ProposalAskCortexActivity.class,PromptLibraryActivity.class,ReviewQueueActivity.class,CorrectionLearningActivity.class,CapabilityMatrixActivity.class,PhoneContextAccessActivity.class,CortexStatusActivity.class,CortexAuditActivity.class,OcrTestActivity.class,UserSimulationTestLabActivity.class};
        for(Class<?> cls:classes){JSONObject x=new JSONObject().put("class",cls.getName());try{ActivityInfo i=pm.getActivityInfo(new ComponentName(c,cls),0);x.put("present",true).put("enabled",i.enabled).put("exported",i.exported);}catch(Throwable e){x.put("present",false).put("error",e.getClass().getSimpleName());}a.put(x);}return new JSONObject().put("activities",a);
    }

    private static JSONObject interactionEnvironment(Context c)throws Exception{
        PackageManager pm=c.getPackageManager();JSONObject o=new JSONObject();o.put("speech_recognition_available",SpeechRecognizer.isRecognitionAvailable(c));o.put("record_audio_granted",pm.checkPermission(android.Manifest.permission.RECORD_AUDIO,c.getPackageName())==PackageManager.PERMISSION_GRANTED);o.put("notification_permission_granted",Build.VERSION.SDK_INT<33||pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS,c.getPackageName())==PackageManager.PERMISSION_GRANTED);o.put("screen_accessibility_connected",CortexScreenAccessibilityService.connected());o.put("shizuku_granted",ShizukuContextBridge.granted());o.put("usage_access",PhoneUsageAccess.has(c));o.put("notification_listener_enabled",CortexAuditSoakWorker.notificationListenerEnabled(c));o.put("network_connected",CortexAuditSoakWorker.network(c));
        o.put("calendar_insert_handler",pm.resolveActivity(new Intent(Intent.ACTION_INSERT,android.provider.CalendarContract.Events.CONTENT_URI),PackageManager.MATCH_DEFAULT_ONLY)!=null);o.put("email_handler",pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("mailto:test@example.com")),PackageManager.MATCH_DEFAULT_ONLY)!=null);o.put("sms_handler",pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null);o.put("call_handler",pm.resolveActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null);
        o.put("external_action_execution","preview_only_in_test");return o;
    }

    private static JSONObject quickState(Context c,VaultDb db)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();JSONObject o=new JSONObject();o.put("captured_at",iso(System.currentTimeMillis()));o.put("counts",quickCounts(s));o.put("db_quick_check",scalar(s,"PRAGMA quick_check(1)"));o.put("db_file_bytes",c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0);o.put("heap_used_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());o.put("heap_max_bytes",Runtime.getRuntime().maxMemory());return o;
    }

    private static JSONObject quickCounts(SQLiteDatabase s)throws Exception{
        JSONObject o=new JSONObject();String[] tables={"knowledge_items","derived_items","source_links","actions","ai_jobs","ai_job_sources","ai_model_runs","interaction_telemetry","phone_context_events","feedback_events","correction_rules","visual_insights","entity_nodes","cortex_audit_runs","cortex_audit_tests","cortex_audit_events"};for(String t:tables)o.put(t,tableExists(s,t)?count(s,"SELECT COUNT(*) FROM \""+t.replace("\"","\"\"")+"\""):-1);return o;
    }

    private static JSONObject syntheticResidue(VaultDb db,String runId)throws Exception{
        SQLiteDatabase s=db.getReadableDatabase();long rows=0;JSONObject byTable=new JSONObject();String like="%"+runId+"%";if(tableExists(s,"knowledge_items")){long n=countArgs(s,"SELECT COUNT(*) FROM knowledge_items WHERE source='diagnostic_user_sim' OR metadata_json LIKE ? OR raw_text LIKE ?",new String[]{like,like});byTable.put("knowledge_items",n);rows+=n;}if(tableExists(s,"derived_items")){long n=countArgs(s,"SELECT COUNT(*) FROM derived_items WHERE metadata_json LIKE ?",new String[]{like});byTable.put("derived_items",n);rows+=n;}if(tableExists(s,"phone_context_events")){long n=countArgs(s,"SELECT COUNT(*) FROM phone_context_events WHERE metadata_json LIKE ? OR text LIKE ?",new String[]{like,like});byTable.put("phone_context_events",n);rows+=n;}return new JSONObject().put("rows",rows).put("by_table",byTable).put("clean",rows==0);
    }

    private static JSONObject step(String name,String category,Work work){long start=SystemClock.elapsedRealtime();try{JSONObject data=work.run();return wrapStep(name,category,data,start);}catch(Throwable e){try{return new JSONObject().put("name",name).put("category",category).put("status","FAIL").put("duration_ms",SystemClock.elapsedRealtime()-start).put("error",error(e));}catch(Exception ignored){return new JSONObject();}}}
    private static JSONObject wrapStep(String name,String category,JSONObject data,long start)throws Exception{String status=data.optString("result_status","PASS");return new JSONObject().put("name",name).put("category",category).put("status",status).put("duration_ms",data.has("duration_ms")?data.optLong("duration_ms"):SystemClock.elapsedRealtime()-start).put("data",data);}
    private static JSONObject event(String name,long start,JSONObject data)throws Exception{return new JSONObject().put("name",name).put("duration_ms",SystemClock.elapsedRealtime()-start).put("data",data);}

    private static JSONObject summarize(JSONArray steps,JSONArray warnings,JSONObject residue)throws Exception{int pass=0,warn=0,fail=0;for(int i=0;i<steps.length();i++){String s=steps.getJSONObject(i).optString("status","PASS");if("FAIL".equals(s))fail++;else if("WARN".equals(s))warn++;else pass++;}if(residue.optLong("rows",0)>0)fail++;return new JSONObject().put("pass_steps",pass).put("warning_steps",warn).put("failed_steps",fail).put("warning_count",warnings.length()).put("synthetic_residue_rows",residue.optLong("rows",0)).put("overall",fail>0?"FAIL":(warn>0?"PASS_WITH_WARNINGS":"PASS"));}
    private static JSONObject error(Throwable e)throws Exception{JSONObject o=new JSONObject().put("type",e==null?"":e.getClass().getName()).put("message",e==null?"":safe(e.getMessage()));if(e!=null){JSONArray st=new JSONArray();StackTraceElement[] xs=e.getStackTrace();for(int i=0;i<Math.min(30,xs.length);i++)st.put(xs[i].toString());o.put("stack",st);Throwable cause=e.getCause();if(cause!=null&&cause!=e)o.put("cause",new JSONObject().put("type",cause.getClass().getName()).put("message",safe(cause.getMessage())));}return o;}

    private static boolean tableExists(SQLiteDatabase s,String t){Cursor c=null;try{c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{t});return c.moveToFirst();}catch(Throwable e){return false;}finally{if(c!=null)c.close();}}
    private static long count(SQLiteDatabase s,String sql){Cursor c=s.rawQuery(sql,null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static long countArgs(SQLiteDatabase s,String sql,String[] args){Cursor c=s.rawQuery(sql,args);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String scalar(SQLiteDatabase s,String sql){Cursor c=s.rawQuery(sql,null);try{return c.moveToFirst()?safe(c.getString(0)):"";}finally{c.close();}}
    private static void write(File f,String text)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8)){w.write(text);}}
    private static String read(File f)throws Exception{StringBuilder b=new StringBuilder((int)Math.min(Integer.MAX_VALUE,f.length()+256));char[] buf=new char[32768];try(Reader r=new InputStreamReader(new FileInputStream(f),java.nio.charset.StandardCharsets.UTF_8)){int n;while((n=r.read(buf))>0)b.append(buf,0,n);}return b.toString();}
    private static String iso(long ms){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date(ms));}
    private static long versionCode(Context c){try{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Throwable e){return 0;}}
    private static String human(long n){if(n<1024)return n+" B";if(n<1024*1024)return String.format(Locale.US,"%.1f KB",n/1024.0);return String.format(Locale.US,"%.2f MB",n/(1024.0*1024.0));}
    private static String safe(String s){return s==null?"":s;}
    private static void phase(Progress p,int percent,String phase,String detail){if(p!=null)try{p.update(Math.max(0,Math.min(100,percent)),phase,detail);}catch(Throwable ignored){}}
}
