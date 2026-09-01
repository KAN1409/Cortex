package com.kareem.cortex;

import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Exhaustive V3 verification layer.
 *
 * The master user-simulation already exercises long-form flows. This layer adds a large matrix of
 * small strict assertions so regressions are localized to one test row. Missing Android access,
 * provider setup or explicit live confirmation is a blocker, never a fake PASS.
 */
public final class CortexExhaustiveVerificationSuite {
    public static final String EXECUTED_PASS="EXECUTED_PASS";
    public static final String EXECUTED_FAIL="EXECUTED_FAIL";
    public static final String BLOCKED_WAITING_USER="BLOCKED_WAITING_USER";
    public static final String BLOCKED_SETUP="BLOCKED_SETUP";
    public static final String PROTECTED_REQUIRES_CONFIRMATION="PROTECTED_REQUIRES_CONFIRMATION";
    public static final String EXPECTED_SIGNER_SHA256="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7";
    private CortexExhaustiveVerificationSuite(){}

    private static final class Acc {int next=1,pass,fail,user,setup,protectedCount;final JSONArray tests=new JSONArray();String id(){return String.format(Locale.US,"X%03d",next++);}void put(JSONObject x){tests.put(x);String s=x.optString("status");if(EXECUTED_PASS.equals(s))pass++;else if(EXECUTED_FAIL.equals(s))fail++;else if(BLOCKED_WAITING_USER.equals(s))user++;else if(BLOCKED_SETUP.equals(s))setup++;else if(PROTECTED_REQUIRES_CONFIRMATION.equals(s))protectedCount++;}}

    public static JSONObject run(Context context,VaultDb db,String runId)throws Exception{
        Context c=context.getApplicationContext();SQLiteDatabase sql=db.getWritableDatabase();PackageManager pm=c.getPackageManager();Acc a=new Acc();

        // ---------- Application identity / Android wiring ----------
        check(a,"Application","Package identity","Package must remain com.kareem.cortex","com.kareem.cortex".equals(c.getPackageName()),new JSONObject().put("package",c.getPackageName()));
        PackageInfo pi=pm.getPackageInfo(c.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
        check(a,"Application","Version metadata","Installed version must be readable",pi.versionName!=null&&!pi.versionName.isEmpty(),new JSONObject().put("version_name",safe(pi.versionName)).put("version_code",Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode));
        String signer=signerSha256(pi);check(a,"Application","Permanent signer continuity","Signer must equal the permanent Cortex signing identity",EXPECTED_SIGNER_SHA256.equalsIgnoreCase(signer),new JSONObject().put("expected",EXPECTED_SIGNER_SHA256).put("actual",signer));
        ApplicationInfo app=pm.getApplicationInfo(c.getPackageName(),0);check(a,"Application","Target SDK","Target SDK must be 35 or newer",app.targetSdkVersion>=35,new JSONObject().put("target_sdk",app.targetSdkVersion).put("device_sdk",Build.VERSION.SDK_INT));

        Class<?>[] activities={InputActivity.class,EvidenceActivity.class,DeepReviewActivity.class,SettingsActivity.class,EnvironmentActivity.class,ProposalCaptureActivity.class,ProposalCaptureResultActivity.class,PeopleProjectsActivity.class,ProposalAskCortexActivity.class,PromptLibraryActivity.class,ReviewQueueActivity.class,CorrectionLearningActivity.class,CapabilityMatrixActivity.class,PhoneContextAccessActivity.class,CortexStatusActivity.class,CortexAuditActivity.class,OcrTestActivity.class,UserSimulationTestLabActivity.class};
        for(Class<?> cls:activities){boolean ok=false;JSONObject e=new JSONObject().put("class",cls.getName());try{ActivityInfo x=pm.getActivityInfo(new ComponentName(c,cls),0);ok=x.enabled;e.put("enabled",x.enabled).put("exported",x.exported);}catch(Throwable x){e.put("error",msg(x));}check(a,"Android components","Activity: "+cls.getSimpleName(),"Activity must be registered and enabled",ok,e);}
        Class<?>[] services={NotificationCaptureService.class,CortexScreenAccessibilityService.class,LocalModelDownloadService.class};
        for(Class<?> cls:services){boolean ok=false;JSONObject e=new JSONObject().put("class",cls.getName());try{ServiceInfo x=pm.getServiceInfo(new ComponentName(c,cls),0);ok=x.enabled;e.put("enabled",x.enabled).put("permission",safe(x.permission));}catch(Throwable x){e.put("error",msg(x));}check(a,"Android components","Service: "+cls.getSimpleName(),"Service must be registered and enabled",ok,e);}
        ProviderInfo fp=pm.resolveContentProvider(c.getPackageName()+".feedback.files",0);check(a,"Android components","Debug FileProvider","Feedback/debug FileProvider must resolve",fp!=null,new JSONObject().put("authority",c.getPackageName()+".feedback.files").put("resolved",fp!=null));
        Intent launcher=pm.getLaunchIntentForPackage(c.getPackageName());check(a,"Android components","Launcher resolution","Package launcher intent must resolve",launcher!=null,new JSONObject().put("component",launcher==null?"":String.valueOf(launcher.getComponent())));

        // ---------- Database integrity / schema / indexes ----------
        String quick=scalar(sql,"PRAGMA quick_check(1)");check(a,"Database","SQLite quick_check","quick_check must be ok","ok".equalsIgnoreCase(quick),new JSONObject().put("result",quick));
        int fkRows=rowCount(sql,"PRAGMA foreign_key_check",null);check(a,"Database","Foreign-key check","No foreign-key violation rows",fkRows==0,new JSONObject().put("violation_rows",fkRows));
        int tableCount=rowCount(sql,"SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",null);check(a,"Database","Application table count","Cortex should expose at least 40 application tables",tableCount>=40,new JSONObject().put("table_count",tableCount));
        int indexCount=rowCount(sql,"SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'",null);check(a,"Database","Index count","Cortex database should expose multiple retrieval indexes",indexCount>=5,new JSONObject().put("index_count",indexCount));
        String[] tables={"knowledge_items","analyses","entities","actions","relations","memory_chunks","embeddings","vision_fields","derived_items","source_links","action_temporal","entity_nodes","prompt_library_items","phone_context_events","phone_process_state","interaction_telemetry","visual_insights","feedback_events","correction_rules","review_queue","ai_jobs","cortex_audit_runs","cortex_audit_tests","cortex_audit_events"};
        for(String t:tables)check(a,"Database schema","Table: "+t,"Required table must exist",table(sql,t),new JSONObject().put("table",t).put("exists",table(sql,t)));
        String[] cols={"id","type","source","title","raw_text","extracted_text","summary","category","tags","attachment_path","status","fingerprint","analysis_error","metadata_json","created_at","updated_at"};
        for(String col:cols)check(a,"Database schema","knowledge_items."+col,"Evidence ledger column must exist",column(sql,"knowledge_items",col),new JSONObject().put("column",col));

        // ---------- Core synthetic transaction ----------
        long beforeBytes=c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0;boolean tx=false;File attachment=null;long seedId=0;
        try{
            sql.beginTransaction();tx=true;
            String token="XSUITE_"+runId;
            String text=token+" Project Orion. Tomorrow at 11 AM I need to verify search latency. اتفقنا نراجع الخطة. Next action: prepare the report. 🚀";
            String category=AutoClassifier.category(text,"text/plain"),title=AutoClassifier.title(text,"text/plain"),tags=AutoClassifier.tags(text,category),fingerprint=Fingerprint.text(text);
            seedId=db.insert("TEXT","manual",title,text,category,tags,"",fingerprint,new JSONObject().put("synthetic",true).put("run_id",runId).toString());
            final long id=seedId;KnowledgeItem stored=id>0?db.getById(id):null;
            check(a,"Capture","Text insert/read","Inserted text must be readable unchanged",stored!=null&&text.equals(stored.rawText),new JSONObject().put("id",id).put("raw_text",stored==null?"":stored.rawText));
            check(a,"Capture","Arabic preservation","Arabic text must survive storage",stored!=null&&stored.rawText.contains("اتفقنا نراجع الخطة"),new JSONObject().put("contains_arabic",stored!=null&&stored.rawText.contains("اتفقنا نراجع الخطة")));
            check(a,"Capture","Emoji preservation","Emoji must survive storage",stored!=null&&stored.rawText.contains("🚀"),new JSONObject().put("contains_emoji",stored!=null&&stored.rawText.contains("🚀")));
            check(a,"Capture","Fingerprint deterministic","Identical input must hash identically",fingerprint.equals(Fingerprint.text(text)),new JSONObject().put("first",fingerprint).put("second",Fingerprint.text(text)));
            check(a,"Capture","Fingerprint distinctness","Different input must hash differently",!fingerprint.equals(Fingerprint.text(text+"!")),new JSONObject().put("different",Fingerprint.text(text+"!")));
            long dup=db.insert("TEXT","manual",title,text,category,tags,"",fingerprint,"{}");check(a,"Capture","Duplicate prevention","Duplicate fingerprint must not create a second logical memory",dup<=0||dup==id,new JSONObject().put("original",id).put("duplicate_result",dup));

            AnalysisResult ar=LocalAnalyzer.analyze(text,"text/plain");db.applyAnalysis(id,ar);KnowledgeItem analyzed=db.getById(id);
            check(a,"Analysis","Analyzed state","Local text analysis must persist analyzed state",analyzed!=null&&"analyzed".equals(analyzed.status),new JSONObject().put("status",analyzed==null?"missing":safe(analyzed.status)).put("analysis",new JSONObject(ar.toJson())));
            check(a,"Analysis","Mixed-language summary","Mixed Arabic/English text must produce non-empty analysis",ar.summary!=null&&!ar.summary.trim().isEmpty(),new JSONObject().put("summary",safe(ar.summary)).put("title",safe(ar.title)));
            check(a,"Analysis","DATE entity","Tomorrow must be recognized as date evidence",hasEntity(ar,"DATE","tomorrow"),entities(ar));
            check(a,"Analysis","PROJECT entity","Project Orion must be recognized",hasEntity(ar,"PROJECT","orion"),entities(ar));
            check(a,"Analysis","Action extraction","Explicit next action must produce action evidence",ar.actions!=null&&!ar.actions.isEmpty(),actions(ar));

            String[][] vectors={
                {"I need to send the report tomorrow","expect_action"},
                {"فكرني أبعت التقرير بكرة","expect_action"},
                {"Project Apollo: review the build","expect_project"},
                {"المشروع Atlas محتاج مراجعة","expect_nonempty"},
                {"I don't need to call Ahmed","negated"},
                {"مش لازم أكلم أحمد","negated"},
                {"Goal: finish Cortex test coverage","expect_nonempty"},
                {"Idea: add a context lens","expect_nonempty"},
                {"Waiting for vendor reply","expect_nonempty"},
                {"اتفقنا نراجع التصميم بكرة","expect_nonempty"}
            };
            for(String[] v:vectors){AnalysisResult r=LocalAnalyzer.analyze(v[0],"text/plain");boolean ok;if("expect_action".equals(v[1]))ok=r.actions!=null&&!r.actions.isEmpty();else if("expect_project".equals(v[1]))ok=hasEntityKind(r,"PROJECT");else if("negated".equals(v[1]))ok=r.actions==null||r.actions.isEmpty();else ok=(safe(r.summary).length()+safe(r.title).length())>0;check(a,"Analysis vectors",v[0],"Vector expectation: "+v[1],ok,new JSONObject().put("expectation",v[1]).put("analysis",new JSONObject(r.toJson())));}

            StringBuilder longText=new StringBuilder();for(int n=0;n<700;n++)longText.append("Cortex long capture ").append(n).append(". ");AnalysisResult longResult=LocalAnalyzer.analyze(longText.toString(),"text/plain");check(a,"Analysis","Long text resilience","Large capture must analyze without exception",longResult.summary!=null,new JSONObject().put("input_chars",longText.length()).put("summary_chars",safe(longResult.summary).length()));

            ArrayList<KnowledgeItem> lexical=db.lexicalSearch("search latency",30);check(a,"Retrieval","Exact lexical phrase","Contiguous phrase must retrieve seeded evidence",containsId(lexical,id),hits(lexical));
            ArrayList<KnowledgeItem> semantic=db.search("Orion search latency");check(a,"Retrieval","Semantic multi-term query","Natural multi-term query must retrieve seeded evidence",containsId(semantic,id),hits(semantic));
            ArrayList<KnowledgeItem> arabic=db.search("راجع الخطة");check(a,"Retrieval","Arabic retrieval","Arabic query must retrieve seeded mixed-language evidence",containsId(arabic,id),hits(arabic));
            ArrayList<KnowledgeItem> recent=db.lexicalSearch("",50);check(a,"Retrieval","Recent enumeration","Empty lexical query must enumerate seeded evidence",containsId(recent,id),hits(recent));

            try{TemporalResolver.afterAnalysis(db,id);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(db,id);}catch(Throwable ignored){}try{IntentionalCognitiveBridge.afterAnalysis(db,db.getById(id),ar);}catch(Throwable ignored){}
            long derived=scalarLong(sql,"SELECT COUNT(*) FROM derived_items WHERE metadata_json LIKE ?",new String[]{"%\"memory_id\":"+id+"%"});check(a,"Cognition","Intentional derived state","Intentional analysis should produce or route cognitive state",derived>0,new JSONObject().put("derived_rows",derived));
            long links=scalarLong(sql,"SELECT COUNT(*) FROM source_links WHERE from_id=? OR to_id=?",new String[]{String.valueOf(id),String.valueOf(id)});check(a,"Cognition","Grounding links","Derived cognition must retain source links",links>0,new JSONObject().put("source_links",links));

            GroundedAnswer g=SecondBrainEngine.ask(db,"What should I do for Orion search latency?");boolean grounded=false;for(SemanticHit h:g.sources)if(h!=null&&h.item!=null&&h.item.id==id)grounded=true;check(a,"Brain","Grounded source inclusion","Brain must cite seeded evidence",grounded,grounded(g));
            check(a,"Brain","Non-empty grounded answer","Grounded question must return an answer",!safe(g.answer).trim().isEmpty(),grounded(g));
            check(a,"Brain","Confidence bounds","Grounding confidence must stay in [0,1]",g.confidence>=0&&g.confidence<=1,new JSONObject().put("confidence",g.confidence));

            DeepReviewContractV1.PromptPack pack=DeepReviewContractV1.build(db);boolean packHas=pack.text.contains("\"evidence_id\":"+id)||pack.text.contains("\"evidence_id\": "+id);check(a,"Deep Review","Context contains evidence","Review context must contain seeded ID",packHas,new JSONObject().put("request_id",pack.requestId).put("evidence_count",pack.evidenceCount).put("state_count",pack.stateCount).put("chars",pack.text.length()));
            String valid=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject().put("request_id",pack.requestId).put("answer","Synthetic grounded review").put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","Verify search latency").put("body","Verify search latency").put("importance",80).put("confidence",.9).put("reason","Explicit action").put("evidence_ids",new JSONArray().put(id)))).put("suggested_actions",new JSONArray()).toString();
            DeepReviewContractV1.Review review=DeepReviewContractV1.parse(db,valid,pack.requestId);check(a,"Deep Review","Valid parse","Grounded structured response must parse",review.items.size()==1,new JSONObject().put("items",review.items.size()).put("actions",review.actions.size()));
            check(a,"Deep Review","Wrong request rejection","Mismatched request ID must fail closed",rejects(()->DeepReviewContractV1.parse(db,valid,"wrong_request")),new JSONObject().put("wrong_request","wrong_request"));
            String unknown=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject().put("request_id",pack.requestId).put("answer","bad").put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","bad").put("body","bad").put("importance",10).put("confidence",.1).put("reason","bad").put("evidence_ids",new JSONArray().put(999999999L)))).put("suggested_actions",new JSONArray()).toString();check(a,"Deep Review","Unknown evidence rejection","Unknown grounding ID must fail closed",rejects(()->DeepReviewContractV1.parse(db,unknown,pack.requestId)),new JSONObject().put("invalid_id",999999999L));
            check(a,"Deep Review","Malformed JSON rejection","Malformed review must fail closed",rejects(()->DeepReviewContractV1.parse(db,DeepReviewContractV1.RESPONSE_MARKER+"\n{bad",pack.requestId)),new JSONObject().put("malformed",true));
            DeepReviewContractV1.ApplyResult apply=DeepReviewContractV1.apply(db,review);check(a,"Deep Review","Apply grounded review","Validated review must create grounded output",apply.reviewEvidenceId>0&&apply.created>0,new JSONObject().put("review_evidence_id",apply.reviewEvidenceId).put("created",apply.created));

            String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);check(a,"Briefs","Daily brief","Daily brief must compose",daily.startsWith("Daily Cortex Brief")&&daily.length()>30,new JSONObject().put("chars",daily.length()).put("text",daily));check(a,"Briefs","Weekly brief","Weekly brief must compose",weekly.startsWith("Weekly Cortex Brief")&&weekly.length()>30,new JSONObject().put("chars",weekly.length()).put("text",weekly));

            PromptLibraryStore.ensure(db);PromptLibraryStore.pin(db,id);PromptLibraryStore.rate(db,id,1);PromptLibraryStore.recordRun(db,id,"XSUITE_RESULT","x-suite",77);PromptLibraryStore.Entry pe=null;for(PromptLibraryStore.Entry e:PromptLibraryStore.list(db,500))if(e.itemId==id){pe=e;break;}check(a,"Prompt Library","Entry readback","Prompt must appear in library",pe!=null,pe==null?new JSONObject():new JSONObject().put("pinned",pe.pinned).put("rating",pe.rating).put("use_count",pe.useCount).put("provider",safe(pe.lastProvider)).put("latency_ms",pe.lastLatencyMs));check(a,"Prompt Library","Pinned readback","Pinned state must persist",pe!=null&&pe.pinned,pe==null?new JSONObject():new JSONObject().put("pinned",pe.pinned));check(a,"Prompt Library","Rating readback","Rating must persist",pe!=null&&pe.rating==1,pe==null?new JSONObject():new JSONObject().put("rating",pe.rating));check(a,"Prompt Library","Run telemetry readback","Run metadata must persist",pe!=null&&pe.useCount>=1&&"x-suite".equals(pe.lastProvider)&&pe.lastLatencyMs==77,pe==null?new JSONObject():new JSONObject().put("use_count",pe.useCount).put("provider",safe(pe.lastProvider)).put("latency_ms",pe.lastLatencyMs));

            PhoneContextStore.ensure(db);long pc=PhoneContextStore.record(db,"app_transition","x_suite",c.getPackageName(),"Cortex","InputActivity","test",token,System.currentTimeMillis(),new JSONObject().put("run_id",runId));boolean pcFound=false;for(PhoneContextStore.Event e:PhoneContextStore.recent(db,System.currentTimeMillis()-60000,200))if(token.equals(e.text)){pcFound=true;break;}check(a,"Phone context","Round-trip","Phone-context event must read back",pcFound,new JSONObject().put("id",pc).put("found",pcFound));
            String secret="password my-secret-value";PhoneContextStore.record(db,"notification_context","x_suite","x","X","","test",secret,System.currentTimeMillis(),new JSONObject());PhoneContextStore.Event latest=PhoneContextStore.latest(db);check(a,"Privacy","Sensitive phone-text redaction","Password-like phone context must be redacted",latest!=null&&!safe(latest.text).contains("my-secret-value"),new JSONObject().put("stored_text",latest==null?"":latest.text));

            attachment=new File(c.getCacheDir(),"x_suite_"+runId+".txt");write(attachment,"Cortex attachment "+token+" search latency");final File attachmentRef=attachment;long fileId=db.insert("FILE","manual","XSuite attachment",token,"Diagnostics","file,xsuite",attachment.getAbsolutePath(),Fingerprint.text("file|"+token),new JSONObject().put("run_id",runId).toString());KnowledgeItem fi=fileId>0?db.getById(fileId):null;check(a,"Files","Attachment stored","Synthetic attachment must exist and map to Evidence",fi!=null&&attachment.exists(),new JSONObject().put("id",fileId).put("bytes",attachmentRef.length()).put("path",attachmentRef.getAbsolutePath()));boolean fileAnalyzed=false;JSONObject fileEvidence=new JSONObject();try{AnalysisResult fr=AttachmentAnalyzer.analyze(fi);db.applyAnalysis(fileId,fr);fileAnalyzed="analyzed".equals(db.getById(fileId).status);fileEvidence.put("analysis",new JSONObject(fr.toJson()));}catch(Throwable x){fileEvidence.put("error",msg(x));}check(a,"Files","Attachment analysis","Text attachment must traverse real analyzer",fileAnalyzed,fileEvidence);

            long stressAt=System.nanoTime();int inserted=0;for(int n=0;n<100;n++){long x=db.insert("TEXT","x_suite_stress","stress "+n,token+" stress payload "+n,"Diagnostics","stress","",Fingerprint.text(token+"|stress|"+n),new JSONObject().put("run_id",runId).toString());if(x>0)inserted++;}long stressMs=(System.nanoTime()-stressAt)/1_000_000L;check(a,"Performance","100-capture rollback stress","100 synthetic captures must insert inside rollback",inserted==100,new JSONObject().put("inserted",inserted).put("duration_ms",stressMs));
            long readAt=System.nanoTime();for(int n=0;n<100;n++)db.getById(id);long readMs=(System.nanoTime()-readAt)/1_000_000L;check(a,"Performance","100 point reads","100 evidence point reads must complete",readMs<5000,new JSONObject().put("duration_ms",readMs));
            long searchAt=System.nanoTime();for(int n=0;n<15;n++)db.search("Orion search latency");long searchMs=(System.nanoTime()-searchAt)/1_000_000L;check(a,"Performance","15 semantic searches","Repeated semantic search must complete",searchMs<15000,new JSONObject().put("duration_ms",searchMs));
            check(a,"Performance","Concurrent Vault reads","Independent readers must complete safely",concurrentReads(c,id),new JSONObject().put("readers",8));
        }finally{if(tx)try{sql.endTransaction();}catch(Throwable ignored){}if(attachment!=null)try{attachment.delete();}catch(Throwable ignored){}}

        long residue=scalarLong(sql,"SELECT COUNT(*) FROM knowledge_items WHERE metadata_json LIKE ? OR raw_text LIKE ?",new String[]{"%"+runId+"%","%"+runId+"%"});check(a,"Safety","Synthetic rollback residue","No suite synthetic Evidence may remain",residue==0,new JSONObject().put("residue_rows",residue));long afterBytes=c.getDatabasePath("cortex.db").exists()?c.getDatabasePath("cortex.db").length():0;check(a,"Performance","Rollback DB growth bound","Rollback stress must not cause pathological database growth",afterBytes-beforeBytes<8L*1024L*1024L,new JSONObject().put("before_bytes",beforeBytes).put("after_bytes",afterBytes).put("delta_bytes",afterBytes-beforeBytes));

        // ---------- Capability-by-capability strict rows (43 additional test rows) ----------
        for(CortexCapabilityRegistry.Capability cap:CortexCapabilityRegistry.all()){
            CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(c,db,cap);JSONObject e=new JSONObject().put("capability_number",cap.number).put("key",cap.key).put("state",s.status).put("detail",s.detail);if(CortexCapabilityRegistry.FAILED.equals(s.status))row(a,"Capabilities","Capability #"+cap.number+" · "+cap.title,EXECUTED_FAIL,"Capability evaluator reports runtime failure",e);else if(CortexCapabilityRegistry.NEEDS_ACCESS.equals(s.status))row(a,"Capabilities","Capability #"+cap.number+" · "+cap.title,BLOCKED_WAITING_USER,"Android/user access is required before execution",e.put("unblock_action","Open Test Lab → Unblock remaining tests"));else if(CortexCapabilityRegistry.NEEDS_SETUP.equals(s.status))row(a,"Capabilities","Capability #"+cap.number+" · "+cap.title,BLOCKED_SETUP,"Provider/model/folder setup is required before execution",e.put("unblock_action","Open Test Lab → Unblock remaining tests"));else check(a,"Capabilities","Capability #"+cap.number+" · "+cap.title,"Capability must resolve to ACTIVE or READY",CortexCapabilityRegistry.ACTIVE.equals(s.status)||CortexCapabilityRegistry.READY.equals(s.status),e);}

        // ---------- OCR / visual readiness ----------
        check(a,"Vision","Arabic OCR asset","Bundled Arabic OCR model must be ready",ArabicOcr.modelReady(c),new JSONObject().put("ready",ArabicOcr.modelReady(c)));
        int vf=VisualInsightStore.countFailed(db);check(a,"Vision","Visual failure backlog","No failed visual items should be stuck",vf==0,new JSONObject().put("failed",vf).put("done",VisualInsightStore.countDone(db)).put("rate_limited",VisualInsightStore.countRateLimited(db)));

        // ---------- Android special access / live provider reads ----------
        permissionRow(a,c,Manifest.permission.RECORD_AUDIO,"Microphone permission");if(Build.VERSION.SDK_INT>=33)permissionRow(a,c,Manifest.permission.POST_NOTIFICATIONS,"Notification runtime permission");
        specialRow(a,"Phone context","Notification Listener",CortexAuditSoakWorker.notificationListenerEnabled(c),"Enable Cortex Notification Access");specialRow(a,"Phone context","Usage Access",PhoneUsageAccess.has(c),"Enable Cortex Usage Access");specialRow(a,"Phone context","Accessibility service",CortexScreenAccessibilityService.connected(),"Enable Cortex Accessibility service");
        if(!ShizukuContextBridge.available())row(a,"Phone context","Shizuku service",BLOCKED_SETUP,"Shizuku service must be running",new JSONObject().put("status",ShizukuContextBridge.status()).put("unblock_action","Start Shizuku"));else check(a,"Phone context","Shizuku service","Shizuku binder must be available",true,new JSONObject().put("status",ShizukuContextBridge.status()));if(!ShizukuContextBridge.granted())row(a,"Phone context","Shizuku permission",BLOCKED_WAITING_USER,"Cortex needs explicit Shizuku permission",new JSONObject().put("status",ShizukuContextBridge.status()).put("unblock_action","Request permission in Unblock Wizard"));else{ShizukuContextBridge.Snapshot ss=ShizukuContextBridge.captureProcessSnapshot(c,db);check(a,"Phone context","Shizuku process snapshot","Real bounded read-only process snapshot must execute",ss.ok,new JSONObject().put("ok",ss.ok).put("parsed",ss.processCount).put("stored",ss.storedCount).put("detail",ss.detail));}

        Uri tree=ScreenshotIngestor.tree(c);if(tree==null)row(a,"Screenshots","Screenshot folder connection",BLOCKED_WAITING_USER,"User must select screenshot folder",new JSONObject().put("unblock_action","Choose folder in Unblock Wizard"));else{DocumentFile d=DocumentFile.fromTreeUri(c,tree);check(a,"Screenshots","Screenshot folder connection","Persisted tree must be readable",d!=null&&d.exists()&&d.canRead(),new JSONObject().put("uri",tree.toString()).put("name",d==null?"":String.valueOf(d.getName())).put("readable",d!=null&&d.canRead()));}

        if(!granted(c,Manifest.permission.READ_CALENDAR))row(a,"Integrations","Calendar read provider",BLOCKED_WAITING_USER,"READ_CALENDAR permission required",new JSONObject().put("unblock_action","Grant Calendar permission"));else providerRead(a,c,CalendarContract.Calendars.CONTENT_URI,"Calendar read provider");
        if(!granted(c,Manifest.permission.READ_CONTACTS))row(a,"Integrations","Contacts read provider",BLOCKED_WAITING_USER,"READ_CONTACTS permission required",new JSONObject().put("unblock_action","Grant Contacts permission"));else providerRead(a,c,ContactsContract.Contacts.CONTENT_URI,"Contacts read provider");

        intentRow(a,pm,new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI),"Calendar draft handler");intentRow(a,pm,new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:cortex-test@example.invalid")),"Email draft handler");intentRow(a,pm,new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:0000000000")),"SMS draft handler");intentRow(a,pm,new Intent(Intent.ACTION_DIAL,Uri.parse("tel:0000000000")),"Dialer handler");intentRow(a,pm,new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE),"Backup document handler");intentRow(a,pm,new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE),"Restore document handler");

        // ---------- Diagnostics / provider readiness ----------
        File shareProbe=new File(c.getFilesDir(),"debug_exports/xsuite_share.json");if(shareProbe.getParentFile()!=null)shareProbe.getParentFile().mkdirs();write(shareProbe,"{\"xsuite\":true}");boolean uriOk=false;String uriText="";try{Uri u=FileProvider.getUriForFile(c,c.getPackageName()+".feedback.files",shareProbe);uriText=u.toString();uriOk="content".equals(u.getScheme());}catch(Throwable ignored){}check(a,"Diagnostics","Shareable JSON URI","FileProvider must return content URI",uriOk,new JSONObject().put("uri",uriText));shareProbe.delete();
        File debug=null;boolean debugOk=false;long debugBytes=0;String debugError="";try{debug=DebugExporter.build(c,db);debugBytes=debug.length();debugOk=debugBytes>1000&&new JSONTokener(read(debug)).nextValue() instanceof JSONObject;}catch(Throwable x){debugError=msg(x);}check(a,"Diagnostics","Debug exporter","Exhaustive debug JSON must build and parse",debugOk,new JSONObject().put("bytes",debugBytes).put("error",debugError));if(debug!=null)debug.delete();

        providerSetupRow(a,"Models","Gemini configured",GeminiKeyStore.has(c),"Configure Gemini in ASR/Provider Setup");providerSetupRow(a,"Models","Groq configured",GroqKeyStore.has(c),"Configure Groq in ASR Setup");providerSetupRow(a,"Models","Local Qwen installed",LocalModelManager.installed(c),"Install Local Qwen in Environment");providerSetupRow(a,"Models","Local Qwen verified",LocalModelManager.verified(c),"Verify Local Qwen in Environment");providerSetupRow(a,"Models","Local Qwen runtime ready",LocalLlmRuntime.ready(c),"Run local inference self-test");boolean visionReady=GeminiKeyStore.has(c)&&PrivacyPolicy.canUseCloud(c,"images");providerSetupRow(a,"Models","Strong Vision live route ready",visionReady,"Configure Gemini + Images AI permission");boolean asrReady=(GeminiKeyStore.has(c)||GroqKeyStore.has(c))&&PrivacyPolicy.canUseCloud(c,"audio")&&granted(c,Manifest.permission.RECORD_AUDIO);if(asrReady)check(a,"Models","Cloud ASR live route ready","Microphone/privacy/provider prerequisites must be met",true,new JSONObject().put("gemini",GeminiKeyStore.has(c)).put("groq",GroqKeyStore.has(c)));else row(a,"Models","Cloud ASR live route ready",granted(c,Manifest.permission.RECORD_AUDIO)?BLOCKED_SETUP:BLOCKED_WAITING_USER,"Requires microphone + audio AI permission + Gemini/Groq",new JSONObject().put("gemini",GeminiKeyStore.has(c)).put("groq",GroqKeyStore.has(c)).put("audio_privacy",PrivacyPolicy.mode(c,"audio")).put("unblock_action","Use Unblock Wizard"));

        // ---------- Protected live tests are part of coverage, but never silently mutate ----------
        String[][] protectedRows={
            {"Real microphone phrase + ASR","Speak a prompted phrase; audio follows current privacy/provider settings"},
            {"Real notification capture","Post/capture a local test notification through Notification Listener"},
            {"Live screenshot-folder import","Can permanently add one selected/recent screenshot to Evidence"},
            {"Reversible Calendar create/delete","Creates a marked event and deletes it immediately; it may briefly sync"},
            {"Backup external document write","Writes through Android document provider"},
            {"Synthetic restore rollback","Runs real restore against synthetic backup inside rollback"},
            {"Email draft UI handoff","Opens unsent synthetic email draft"},
            {"SMS draft UI handoff","Opens unsent synthetic SMS draft"},
            {"Dialer UI handoff","Opens synthetic number without placing call"},
            {"Real Local Qwen inference","Runs actual on-device completion when model is verified"},
            {"Real Strong Vision provider call","Runs provider check inside normal image privacy boundary"},
            {"30-minute stability soak","Long-duration background/device behavior test"}
        };
        for(String[] x:protectedRows)row(a,"Protected live tests",x[0],PROTECTED_REQUIRES_CONFIRMATION,x[1],new JSONObject().put("unblock_action","Open Test Lab → Protected live tests"));

        int executed=a.pass+a.fail,total=a.tests.length();double coverage=total==0?0:(double)executed/total;JSONObject counts=new JSONObject().put(EXECUTED_PASS,a.pass).put(EXECUTED_FAIL,a.fail).put(BLOCKED_WAITING_USER,a.user).put(BLOCKED_SETUP,a.setup).put(PROTECTED_REQUIRES_CONFIRMATION,a.protectedCount);
        return new JSONObject().put("schema","CORTEX_EXHAUSTIVE_VERIFICATION_V2").put("test_count",total).put("executed_count",executed).put("counts",counts).put("execution_coverage",coverage).put("tests",a.tests).put("result_status",a.fail>0?"FAIL":(a.user+a.setup+a.protectedCount)>0?"INCOMPLETE_BLOCKED":"PASS").put("unblock_hint","Use Test Lab → Unblock remaining tests, then rerun. Protected live tests always require explicit confirmation.");
    }

    private interface Throwing {void run()throws Exception;}
    private static void check(Acc a,String category,String name,String expected,boolean pass,JSONObject evidence)throws Exception{row(a,category,name,pass?EXECUTED_PASS:EXECUTED_FAIL,expected,evidence);}
    private static void row(Acc a,String category,String name,String status,String expected,JSONObject evidence)throws Exception{a.put(new JSONObject().put("id",a.id()).put("category",category).put("name",name).put("status",status).put("expected",expected).put("evidence",evidence));}
    private static void permissionRow(Acc a,Context c,String p,String name)throws Exception{if(granted(c,p))check(a,"Permissions",name,"Permission must be granted",true,new JSONObject().put("permission",p));else row(a,"Permissions",name,BLOCKED_WAITING_USER,"Runtime permission required",new JSONObject().put("permission",p).put("unblock_action","Grant in Test Lab Unblock Wizard"));}
    private static void specialRow(Acc a,String cat,String name,boolean active,String action)throws Exception{if(active)check(a,cat,name,"Special access must be active",true,new JSONObject().put("active",true));else row(a,cat,name,BLOCKED_WAITING_USER,"Android special access required",new JSONObject().put("active",false).put("unblock_action",action));}
    private static void providerSetupRow(Acc a,String cat,String name,boolean ready,String action)throws Exception{if(ready)check(a,cat,name,"Provider/model prerequisite must be ready",true,new JSONObject().put("ready",true));else row(a,cat,name,BLOCKED_SETUP,"Provider/model prerequisite missing",new JSONObject().put("ready",false).put("unblock_action",action));}
    private static void providerRead(Acc a,Context c,Uri u,String name)throws Exception{Cursor q=null;try{q=c.getContentResolver().query(u,new String[]{"_id"},null,null,null);check(a,"Integrations",name,"Read-only provider query must complete",q!=null,new JSONObject().put("uri",u.toString()).put("rows",q==null?-1:q.getCount()));}catch(Throwable x){check(a,"Integrations",name,"Read-only provider query must complete",false,new JSONObject().put("error",msg(x)));}finally{if(q!=null)q.close();}}
    private static void intentRow(Acc a,PackageManager pm,Intent i,String name)throws Exception{ResolveInfo r=pm.resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);check(a,"External handoff",name,"Approval-first Android handler must resolve",r!=null,new JSONObject().put("action",safe(i.getAction())).put("resolved",r!=null).put("handler",r==null?"":r.activityInfo.packageName+"/"+r.activityInfo.name));}
    private static boolean rejects(Throwing x){try{x.run();return false;}catch(Throwable e){return true;}}
    private static boolean granted(Context c,String p){return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private static boolean table(SQLiteDatabase s,String t){Cursor c=null;try{c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{t});return c.moveToFirst();}catch(Throwable e){return false;}finally{if(c!=null)c.close();}}
    private static boolean column(SQLiteDatabase s,String t,String col){Cursor c=null;try{c=s.rawQuery("PRAGMA table_info(\""+t+"\")",null);int ix=c.getColumnIndex("name");while(c.moveToNext())if(col.equals(c.getString(ix)))return true;return false;}catch(Throwable e){return false;}finally{if(c!=null)c.close();}}
    private static int rowCount(SQLiteDatabase s,String q,String[] args){Cursor c=s.rawQuery(q,args);try{int n=0;while(c.moveToNext())n++;return n;}finally{c.close();}}
    private static long scalarLong(SQLiteDatabase s,String q,String[] args){Cursor c=s.rawQuery(q,args);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String scalar(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);try{return c.moveToFirst()?safe(c.getString(0)):"";}finally{c.close();}}
    private static boolean hasEntity(AnalysisResult r,String kind,String val){if(r==null)return false;for(AnalysisResult.Entity e:r.entities)if(kind.equalsIgnoreCase(safe(e.kind))&&safe(e.value).toLowerCase(Locale.ROOT).contains(val.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean hasEntityKind(AnalysisResult r,String kind){if(r==null)return false;for(AnalysisResult.Entity e:r.entities)if(kind.equalsIgnoreCase(safe(e.kind)))return true;return false;}
    private static JSONObject entities(AnalysisResult r)throws Exception{JSONArray a=new JSONArray();if(r!=null)for(AnalysisResult.Entity e:r.entities)a.put(new JSONObject().put("kind",e.kind).put("value",e.value).put("confidence",e.confidence));return new JSONObject().put("entities",a);}
    private static JSONObject actions(AnalysisResult r)throws Exception{JSONArray a=new JSONArray();if(r!=null)for(AnalysisResult.Action x:r.actions)a.put(new JSONObject().put("text",x.text).put("due_text",x.dueText));return new JSONObject().put("actions",a);}
    private static boolean containsId(List<KnowledgeItem> xs,long id){if(xs!=null)for(KnowledgeItem x:xs)if(x!=null&&x.id==id)return true;return false;}
    private static JSONObject hits(List<KnowledgeItem> xs)throws Exception{JSONArray a=new JSONArray();if(xs!=null)for(int n=0;n<Math.min(20,xs.size());n++){KnowledgeItem x=xs.get(n);a.put(new JSONObject().put("id",x.id).put("title",x.title).put("status",x.status));}return new JSONObject().put("count",xs==null?0:xs.size()).put("hits",a);}
    private static JSONObject grounded(GroundedAnswer g)throws Exception{JSONArray s=new JSONArray();for(SemanticHit h:g.sources)if(h!=null&&h.item!=null)s.put(new JSONObject().put("id",h.item.id).put("title",h.item.title).put("score",h.score));return new JSONObject().put("answer",safe(g.answer)).put("confidence",g.confidence).put("sources",s).put("open_loops",g.openLoops.size()).put("decisions",g.decisions.size());}
    private static boolean concurrentReads(Context c,long id){ExecutorService ex=Executors.newFixedThreadPool(4);try{ArrayList<Future<Boolean>> f=new ArrayList<>();for(int n=0;n<8;n++)f.add(ex.submit(()->{VaultDb d=new VaultDb(c);try{return d.getById(id)!=null;}finally{d.close();}}));for(Future<Boolean> x:f)if(!x.get(5,TimeUnit.SECONDS))return false;return true;}catch(Throwable e){return false;}finally{ex.shutdownNow();}}
    private static String signerSha256(PackageInfo p){try{byte[] b;if(Build.VERSION.SDK_INT>=28){android.content.pm.Signature[] s=p.signingInfo==null?null:p.signingInfo.getApkContentsSigners();if(s==null||s.length==0)return"";b=s[0].toByteArray();}else{if(p.signatures==null||p.signatures.length==0)return"";b=p.signatures[0].toByteArray();}byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder x=new StringBuilder();for(byte v:h)x.append(String.format(Locale.US,"%02x",v));return x.toString();}catch(Throwable e){return"";}}
    private static void write(File f,String s)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8)){w.write(s);}}
    private static String read(File f)throws Exception{StringBuilder b=new StringBuilder();try(Reader r=new InputStreamReader(new FileInputStream(f),java.nio.charset.StandardCharsets.UTF_8)){char[] x=new char[16384];for(int n;(n=r.read(x))>0;)b.append(x,0,n);}return b.toString();}
    private static String msg(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private static String safe(String s){return s==null?"":s;}
}
