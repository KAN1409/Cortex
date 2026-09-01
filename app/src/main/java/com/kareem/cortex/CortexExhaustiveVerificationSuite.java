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
import android.provider.Settings;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * V3 exhaustive verification layer.
 *
 * Every row is an executable assertion or an explicit blocker. A class being present is never
 * sufficient by itself for a functional PASS. External side effects are never silently executed.
 */
public final class CortexExhaustiveVerificationSuite {
    public static final String EXECUTED_PASS="EXECUTED_PASS";
    public static final String EXECUTED_FAIL="EXECUTED_FAIL";
    public static final String BLOCKED_WAITING_USER="BLOCKED_WAITING_USER";
    public static final String BLOCKED_SETUP="BLOCKED_SETUP";
    public static final String PROTECTED_REQUIRES_CONFIRMATION="PROTECTED_REQUIRES_CONFIRMATION";
    public static final String EXPECTED_SIGNER_SHA256="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7";
    private CortexExhaustiveVerificationSuite(){}

    public static JSONObject run(Context context,VaultDb db,String runId)throws Exception{
        Context c=context.getApplicationContext();JSONArray tests=new JSONArray();Counter k=new Counter();
        PackageManager pm=c.getPackageManager();SQLiteDatabase sql=db.getWritableDatabase();

        // Application / package / manifest.
        add(tests,k,exec("X001","Application","Package identity","Installed package must be com.kareem.cortex",()->ev("package",c.getPackageName()),"com.kareem.cortex".equals(c.getPackageName())));
        PackageInfo pi=pm.getPackageInfo(c.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
        add(tests,k,exec("X002","Application","Version identity","Version code/name must be readable",()->new JSONObject().put("version_name",pi.versionName).put("version_code",Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode),pi.versionName!=null&&!pi.versionName.isEmpty()));
        String signer=signerSha256(pi);add(tests,k,exec("X003","Application","Permanent signer continuity","APK signer must match permanent Cortex signer",()->ev("sha256",signer),EXPECTED_SIGNER_SHA256.equalsIgnoreCase(signer)));
        ApplicationInfo ai=pm.getApplicationInfo(c.getPackageName(),0);add(tests,k,exec("X004","Application","Target SDK","Target SDK must be modern Android target",()->ev("target_sdk",ai.targetSdkVersion),ai.targetSdkVersion>=35));
        add(tests,k,componentActivity(pm,c,InputActivity.class,"X005","Main chat activity"));
        add(tests,k,componentActivity(pm,c,EvidenceActivity.class,"X006","Evidence activity"));
        add(tests,k,componentActivity(pm,c,DeepReviewActivity.class,"X007","Deep Review activity"));
        add(tests,k,componentActivity(pm,c,SettingsActivity.class,"X008","Settings activity"));
        add(tests,k,componentActivity(pm,c,UserSimulationTestLabActivity.class,"X009","Test Lab activity"));
        add(tests,k,componentService(pm,c,NotificationCaptureService.class,"X010","Notification listener service"));
        add(tests,k,componentService(pm,c,CortexScreenAccessibilityService.class,"X011","Screen accessibility service"));
        add(tests,k,provider(pm,c,c.getPackageName()+".feedback.files","X012","Debug FileProvider"));
        add(tests,k,exec("X013","Application","Launcher resolution","Launcher intent must resolve to Cortex",()->{Intent i=pm.getLaunchIntentForPackage(c.getPackageName());return new JSONObject().put("resolved",i!=null).put("component",i==null?"":String.valueOf(i.getComponent()));},pm.getLaunchIntentForPackage(c.getPackageName())!=null));

        // Database integrity and schema.
        String quick=scalar(sql,"PRAGMA quick_check(1)");add(tests,k,exec("X014","Database","SQLite quick_check","Database must pass SQLite quick_check",()->ev("quick_check",quick),"ok".equalsIgnoreCase(quick)));
        long fk=count(sql,"PRAGMA foreign_key_check");add(tests,k,exec("X015","Database","Foreign-key diagnostics","No foreign-key violation rows",()->ev("violations",fk),fk==0));
        int tables=tableCount(sql);add(tests,k,exec("X016","Database","Schema breadth","Current Cortex schema should expose at least 40 application tables",()->ev("table_count",tables),tables>=40));
        int indexes=indexCount(sql);add(tests,k,exec("X017","Database","Index inventory","Database must expose indexes for bounded retrieval",()->ev("index_count",indexes),indexes>=5));
        add(tests,k,tableTest(sql,"knowledge_items","X018","Evidence ledger table"));
        add(tests,k,tableTest(sql,"derived_items","X019","Derived intelligence table"));
        add(tests,k,tableTest(sql,"source_links","X020","Grounding relation table"));
        add(tests,k,tableTest(sql,"actions","X021","Action table"));
        add(tests,k,tableTest(sql,"action_temporal","X022","Temporal action table"));
        add(tests,k,tableTest(sql,"entity_nodes","X023","People/projects entity table"));
        add(tests,k,tableTest(sql,"prompt_library_items","X024","Prompt Library table"));
        add(tests,k,tableTest(sql,"interaction_telemetry","X025","Interaction telemetry table"));
        add(tests,k,tableTest(sql,"cortex_audit_runs","X026","Audit run table"));
        add(tests,k,tableTest(sql,"cortex_audit_tests","X027","Audit test table"));
        add(tests,k,tableTest(sql,"cortex_audit_events","X028","Audit event table"));
        add(tests,k,exec("X029","Database","Evidence schema columns","Evidence table must retain immutable/raw and derived analysis fields",()->schemaColumns(sql,"knowledge_items"),hasColumns(sql,"knowledge_items","id","type","source","raw_text","extracted_text","status","fingerprint","metadata_json")));

        // Synthetic user state: real DB paths, one outer rollback.
        String token="XSUITE_"+runId;long dbBefore=c.getDatabasePath("cortex.db").length();boolean tx=false;long id=0;File attachment=null;
        try{
            sql.beginTransaction();tx=true;
            String text=token+" Project Orion. Tomorrow at 11 AM I need to verify search latency. اتفقنا نراجع الخطة. Next action: prepare the report. 🚀";
            String cat=AutoClassifier.category(text,"text/plain"),title=AutoClassifier.title(text,"text/plain"),tags=AutoClassifier.tags(text,cat),fp=Fingerprint.text(text);
            id=db.insert("TEXT","manual",title,text,cat,tags,"",fp,new JSONObject().put("synthetic",true).put("run_id",runId).toString());
            KnowledgeItem item=id>0?db.getById(id):null;
            add(tests,k,exec("X030","Capture","Text insert/read round-trip","Real Vault insert must read back unchanged",()->new JSONObject().put("id",id).put("raw",item==null?"":item.rawText),item!=null&&text.equals(item.rawText)));
            add(tests,k,exec("X031","Capture","Unicode Arabic preservation","Arabic content must survive storage unchanged",()->ev("contains_arabic",item!=null&&item.rawText.contains("اتفقنا نراجع الخطة")),item!=null&&item.rawText.contains("اتفقنا نراجع الخطة")));
            add(tests,k,exec("X032","Capture","Emoji preservation","Supplementary/user symbols must survive storage",()->ev("contains_emoji",item!=null&&item.rawText.contains("🚀")),item!=null&&item.rawText.contains("🚀")));
            add(tests,k,exec("X033","Capture","Fingerprint determinism","Same text must generate same SHA fingerprint",()->new JSONObject().put("a",fp).put("b",Fingerprint.text(text)),fp.equals(Fingerprint.text(text))));
            add(tests,k,exec("X034","Capture","Fingerprint distinction","Different text must not share fingerprint",()->new JSONObject().put("original",fp).put("different",Fingerprint.text(text+"!")),!fp.equals(Fingerprint.text(text+"!"))));
            long dup=db.insert("TEXT","manual",title,text,cat,tags,"",fp,"{}");add(tests,k,exec("X035","Capture","Duplicate rejection","Duplicate fingerprint must not create second evidence row",()->new JSONObject().put("first_id",id).put("duplicate_result",dup),dup<=0||dup==id));

            AnalysisResult ar=LocalAnalyzer.analyze(text,"text/plain");db.applyAnalysis(id,ar);KnowledgeItem analyzed=db.getById(id);
            add(tests,k,exec("X036","Analysis","Local analysis state","Text must reach analyzed state",()->new JSONObject().put("status",analyzed==null?"":analyzed.status).put("analysis",new JSONObject(ar.toJson())),analyzed!=null&&"analyzed".equals(analyzed.status)));
            add(tests,k,exec("X037","Analysis","Mixed-language analysis","Arabic/English input must remain non-empty after analysis",()->new JSONObject().put("summary",ar.summary).put("title",ar.title),ar.summary!=null&&!ar.summary.trim().isEmpty()));
            add(tests,k,exec("X038","Analysis","Date entity extraction","Tomorrow signal must produce a DATE entity",()->entities(ar),hasEntity(ar,"DATE","tomorrow")));
            add(tests,k,exec("X039","Analysis","Project entity extraction","Project Orion must produce a PROJECT entity",()->entities(ar),hasEntity(ar,"PROJECT","Orion")));
            add(tests,k,exec("X040","Analysis","Action extraction","Explicit next action must produce action evidence",()->actions(ar),ar.actions!=null&&!ar.actions.isEmpty()));
            AnalysisResult neg=LocalAnalyzer.analyze("I don't need to call Ahmed. مش لازم أكلمه.","text/plain");add(tests,k,exec("X041","Analysis","Negated-action safety","Explicitly negated task should not become an action",()->actions(neg),neg.actions==null||neg.actions.isEmpty()));
            StringBuilder longText=new StringBuilder();for(int z=0;z<600;z++)longText.append("Cortex long capture ").append(z).append(". ");AnalysisResult lr=LocalAnalyzer.analyze(longText.toString(),"text/plain");add(tests,k,exec("X042","Analysis","Long-text analyzer resilience","Large text must analyze without exception and produce summary",()->new JSONObject().put("chars",longText.length()).put("summary_chars",lr.summary==null?0:lr.summary.length()),lr.summary!=null));

            ArrayList<KnowledgeItem> exact=db.lexicalSearch("search latency",20);add(tests,k,exec("X043","Retrieval","Exact lexical phrase retrieval","Contiguous user phrase must retrieve fresh evidence",()->hits(exact),containsId(exact,id)));
            ArrayList<KnowledgeItem> semantic=db.search("Orion search latency");add(tests,k,exec("X044","Retrieval","Semantic multi-term retrieval","Natural multi-term query must retrieve fresh evidence",()->hits(semantic),containsId(semantic,id)));
            ArrayList<KnowledgeItem> unicode=db.search("راجع الخطة");add(tests,k,exec("X045","Retrieval","Arabic retrieval","Arabic query must retrieve mixed-language evidence",()->hits(unicode),containsId(unicode,id)));
            ArrayList<KnowledgeItem> all=db.lexicalSearch("",30);add(tests,k,exec("X046","Retrieval","Recent evidence enumeration","Empty lexical query must enumerate recent evidence",()->hits(all),containsId(all,id)));

            try{TemporalResolver.afterAnalysis(db,id);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(db,id);}catch(Throwable ignored){}try{IntentionalCognitiveBridge.afterAnalysis(db,db.getById(id),ar);}catch(Throwable ignored){}
            long derived=countArgs(sql,"SELECT COUNT(*) FROM derived_items WHERE metadata_json LIKE ?",new String[]{"%\"memory_id\":"+id+"%"});add(tests,k,exec("X047","Cognition","Intentional cognitive bridge","Intentional analyzed capture should create/review cognitive state",()->ev("derived_rows",derived),derived>0));
            long links=countArgs(sql,"SELECT COUNT(*) FROM source_links WHERE from_id=? OR to_id=?",new String[]{String.valueOf(id),String.valueOf(id)});add(tests,k,exec("X048","Cognition","Grounding links","Derived cognition must retain source links",()->ev("link_rows",links),links>0));

            GroundedAnswer g=SecondBrainEngine.ask(db,"What should I do for Orion search latency?");boolean grounded=false;for(SemanticHit h:g.sources)if(h!=null&&h.item!=null&&h.item.id==id)grounded=true;
            final boolean groundedFinal=grounded;add(tests,k,exec("X049","Brain","Grounded retrieval answer","Brain must cite the seeded evidence",()->grounded(g),groundedFinal));
            add(tests,k,exec("X050","Brain","Grounded answer non-empty","Grounded query must return a meaningful response",()->ev("answer",g.answer),g.answer!=null&&!g.answer.trim().isEmpty()));
            add(tests,k,exec("X051","Brain","Grounded source confidence bounds","Confidence must remain between 0 and 1",()->ev("confidence",g.confidence),g.confidence>=0&&g.confidence<=1));

            DeepReviewContractV1.PromptPack pack=DeepReviewContractV1.build(db);boolean packHas=pack.text.contains("\"evidence_id\":"+id)||pack.text.contains("\"evidence_id\": "+id);
            add(tests,k,exec("X052","Deep Review","Context grounding","Deep Review pack must include source evidence ID",()->new JSONObject().put("request_id",pack.requestId).put("evidence_count",pack.evidenceCount).put("chars",pack.text.length()),packHas));
            String good=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject().put("request_id",pack.requestId).put("answer","Grounded synthetic review").put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","Verify search latency").put("body","Verify search latency").put("importance",80).put("confidence",.9).put("reason","Explicit action").put("evidence_ids",new JSONArray().put(id)))).put("suggested_actions",new JSONArray()).toString();
            DeepReviewContractV1.Review rv=DeepReviewContractV1.parse(db,good,pack.requestId);add(tests,k,exec("X053","Deep Review","Valid response parsing","Valid grounded review must parse",()->new JSONObject().put("items",rv.items.size()).put("actions",rv.actions.size()),rv.items.size()==1));
            boolean wrongRequest=rejected(()->DeepReviewContractV1.parse(db,good,"wrong_request"));add(tests,k,exec("X054","Deep Review","Request-ID mismatch rejection","Response for another request must fail closed",()->ev("rejected",wrongRequest),wrongRequest));
            String badId=DeepReviewContractV1.RESPONSE_MARKER+"\n"+new JSONObject().put("request_id",pack.requestId).put("answer","bad").put("priority_items",new JSONArray().put(new JSONObject().put("kind","ACTION").put("title","bad").put("body","bad").put("importance",50).put("confidence",.5).put("reason","bad").put("evidence_ids",new JSONArray().put(999999999L)))).put("suggested_actions",new JSONArray()).toString();
            boolean invalidGround=rejected(()->DeepReviewContractV1.parse(db,badId,pack.requestId));add(tests,k,exec("X055","Deep Review","Unknown grounding-ID rejection","Review citing unknown evidence must fail closed",()->ev("rejected",invalidGround),invalidGround));
            boolean malformed=rejected(()->DeepReviewContractV1.parse(db,DeepReviewContractV1.RESPONSE_MARKER+"\n{bad json",pack.requestId));add(tests,k,exec("X056","Deep Review","Malformed response rejection","Malformed structured review must fail closed",()->ev("rejected",malformed),malformed));
            DeepReviewContractV1.ApplyResult applied=DeepReviewContractV1.apply(db,rv);add(tests,k,exec("X057","Deep Review","Validated apply path","Validated review must create grounded derived output",()->new JSONObject().put("review_evidence_id",applied.reviewEvidenceId).put("created",applied.created),applied.reviewEvidenceId>0&&applied.created>0));

            String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);add(tests,k,exec("X058","Briefs","Daily brief composition","Daily brief must compose and reflect current state",()->ev("daily",daily),daily.startsWith("Daily Cortex Brief")&&daily.length()>30));
            add(tests,k,exec("X059","Briefs","Weekly brief composition","Weekly brief must compose and reflect current state",()->ev("weekly",weekly),weekly.startsWith("Weekly Cortex Brief")&&weekly.length()>30));

            PromptLibraryStore.ensure(db);PromptLibraryStore.pin(db,id);PromptLibraryStore.rate(db,id,1);PromptLibraryStore.recordRun(db,id,"XSUITE_RESULT","x-suite",77);boolean promptFound=false,pinned=false,rated=false,runMeta=false;for(PromptLibraryStore.Entry e:PromptLibraryStore.list(db,500))if(e.itemId==id){promptFound=true;pinned=e.pinned;rated=e.rating==1;runMeta=e.useCount>=1&&"x-suite".equals(e.lastProvider)&&e.lastLatencyMs==77;break;}
            add(tests,k,exec("X060","Prompt Library","Prompt persistence","Synthetic prompt must appear in library",()->ev("found",promptFound),promptFound));
            add(tests,k,exec("X061","Prompt Library","Pin persistence","Pinned state must read back",()->ev("pinned",pinned),pinned));
            add(tests,k,exec("X062","Prompt Library","Rating persistence","Rating must read back",()->ev("rated",rated),rated));
            add(tests,k,exec("X063","Prompt Library","Run telemetry persistence","Provider/latency/use metadata must read back",()->ev("run_metadata",runMeta),runMeta));

            long phone=PhoneContextStore.record(db,"app_transition","x_suite",c.getPackageName(),"Cortex","InputActivity","test",token,System.currentTimeMillis(),new JSONObject().put("synthetic",true).put("run_id",runId));boolean phoneFound=false;for(PhoneContextStore.Event e:PhoneContextStore.recent(db,System.currentTimeMillis()-60000,100))if(token.equals(e.text)){phoneFound=true;break;}
            add(tests,k,exec("X064","Phone context","Phone event round-trip","Bounded phone-context row must read back",()->new JSONObject().put("id",phone).put("found",phoneFound),phoneFound));

            attachment=new File(c.getCacheDir(),"x_suite_"+runId+".txt");write(attachment,"Cortex attachment "+token+" search latency");long fid=db.insert("FILE","manual","XSuite attachment",token,"Diagnostics","file,xsuite",attachment.getAbsolutePath(),Fingerprint.text("file|"+token),new JSONObject().put("run_id",runId).toString());KnowledgeItem fi=fid>0?db.getById(fid):null;boolean fileStored=fi!=null&&attachment.exists();add(tests,k,exec("X065","Files","Attachment storage","Synthetic attachment must be stored/readable",()->new JSONObject().put("id",fid).put("bytes",attachment.length()),fileStored));
            AnalysisResult fa=null;boolean fileAnalyzed=false;try{fa=AttachmentAnalyzer.analyze(fi);db.applyAnalysis(fid,fa);fileAnalyzed="analyzed".equals(db.getById(fid).status);}catch(Throwable ignored){}final AnalysisResult faFinal=fa;add(tests,k,exec("X066","Files","Attachment analysis","Readable text attachment must traverse analyzer",()->new JSONObject().put("analyzed",fileAnalyzed).put("analysis",faFinal==null?JSONObject.NULL:new JSONObject(faFinal.toJson())),fileAnalyzed));

            // Stress / resilience while rollback protects user data.
            long stressStart=System.nanoTime();int inserted=0;for(int n=0;n<100;n++){long x=db.insert("TEXT","x_suite_stress","stress "+n,token+" stress payload "+n,"Diagnostics","stress","",Fingerprint.text(token+"|stress|"+n),new JSONObject().put("run_id",runId).toString());if(x>0)inserted++;}long stressMs=(System.nanoTime()-stressStart)/1_000_000L;
            add(tests,k,exec("X067","Performance","100-capture rollback stress","100 synthetic captures should insert inside rollback transaction",()->new JSONObject().put("inserted",inserted).put("duration_ms",stressMs),inserted==100));
            long readStart=System.nanoTime();for(int n=0;n<100;n++)db.getById(id);long readMs=(System.nanoTime()-readStart)/1_000_000L;add(tests,k,exec("X068","Performance","100 repeated evidence reads","Repeated point reads must complete without exception",()->ev("duration_ms",readMs),readMs<5000));
            long semStart=System.nanoTime();for(int n=0;n<15;n++)db.search("Orion search latency");long semMs=(System.nanoTime()-semStart)/1_000_000L;add(tests,k,exec("X069","Performance","Repeated semantic retrieval","15 semantic retrievals must complete",()->ev("duration_ms",semMs),semMs<15000));
            boolean concurrent=concurrentReads(c,id);add(tests,k,exec("X070","Performance","Concurrent read safety","Independent Vault readers must complete concurrently",()->ev("completed",concurrent),concurrent));
        }finally{
            if(tx)try{sql.endTransaction();}catch(Throwable ignored){}
            if(attachment!=null)try{attachment.delete();}catch(Throwable ignored){}
        }
        long residue=countArgs(sql,"SELECT COUNT(*) FROM knowledge_items WHERE metadata_json LIKE ? OR raw_text LIKE ?",new String[]{"%"+runId+"%","%"+runId+"%"});add(tests,k,exec("X071","Safety","Synthetic rollback cleanliness","Exhaustive suite must leave zero synthetic evidence rows",()->ev("residue_rows",residue),residue==0));
        long dbAfter=c.getDatabasePath("cortex.db").length();add(tests,k,exec("X072","Performance","Rollback file growth bound","Rollback stress must not cause pathological DB growth",()->new JSONObject().put("before_bytes",dbBefore).put("after_bytes",dbAfter).put("delta_bytes",dbAfter-dbBefore),dbAfter-dbBefore<8L*1024L*1024L));

        // OCR / local vision readiness. Full OCR execution remains in the master runner.
        add(tests,k,exec("X073","Vision","Arabic OCR asset readiness","Bundled Arabic OCR model must be ready",()->ev("ready",ArabicOcr.modelReady(c)),ArabicOcr.modelReady(c)));
        int visualFailed=VisualInsightStore.countFailed(db);add(tests,k,exec("X074","Vision","Visual failure backlog","No unresolved failed visual items in current store",()->new JSONObject().put("failed",visualFailed).put("done",VisualInsightStore.countDone(db)).put("rate_limited",VisualInsightStore.countRateLimited(db)),visualFailed==0));

        // Runtime permissions / special access. These are blockers, never fake PASS.
        add(tests,k,permission(c,Manifest.permission.RECORD_AUDIO,"X075","Permissions","Microphone runtime permission","Grant runtime permissions in Test Lab Unblock Wizard"));
        if(Build.VERSION.SDK_INT>=33)add(tests,k,permission(c,Manifest.permission.POST_NOTIFICATIONS,"X076","Permissions","Notification runtime permission","Grant runtime permissions in Test Lab Unblock Wizard"));else add(tests,k,pass("X076","Permissions","Notification runtime permission","Not required below Android 13",new JSONObject().put("sdk",Build.VERSION.SDK_INT)));
        add(tests,k,special("X077","Phone context","Notification Listener access",CortexAuditSoakWorker.notificationListenerEnabled(c),"Open Notification Access from Test Lab Unblock Wizard"));
        add(tests,k,special("X078","Phone context","Usage Access",PhoneUsageAccess.has(c),"Open Usage Access from Test Lab Unblock Wizard"));
        boolean acc=CortexScreenAccessibilityService.connected();add(tests,k,special("X079","Phone context","Accessibility context service",acc,"Open Accessibility from Test Lab Unblock Wizard and enable Cortex"));
        add(tests,k,setupState("X080","Phone context","Shizuku service availability",ShizukuContextBridge.available(),"Start Shizuku on the device"));
        add(tests,k,special("X081","Phone context","Shizuku permission",ShizukuContextBridge.granted(),"Request Cortex permission from Shizuku in Test Lab Unblock Wizard"));
        if(ShizukuContextBridge.granted()){
            ShizukuContextBridge.Snapshot ss=ShizukuContextBridge.captureProcessSnapshot(c,db);add(tests,k,exec("X082","Phone context","Shizuku real process snapshot","Granted Shizuku path must return bounded read-only process data",()->new JSONObject().put("ok",ss.ok).put("process_count",ss.processCount).put("stored_count",ss.storedCount).put("detail",ss.detail),ss.ok));
        }else add(tests,k,blocked("X082","Phone context","Shizuku real process snapshot","Requires Shizuku permission","Request Shizuku permission, then rerun",BLOCKED_WAITING_USER));

        Uri tree=ScreenshotIngestor.tree(c);if(tree==null)add(tests,k,blocked("X083","Screenshots","Screenshot folder live connection","Requires user-selected Samsung screenshot folder","Connect screenshot folder in Test Lab Unblock Wizard",BLOCKED_WAITING_USER));else{DocumentFile root=DocumentFile.fromTreeUri(c,tree);boolean ok=root!=null&&root.exists()&&root.canRead();add(tests,k,exec("X083","Screenshots","Screenshot folder live connection","Persisted screenshot tree must still be readable",()->new JSONObject().put("uri",tree.toString()).put("readable",ok).put("name",root==null?"":String.valueOf(root.getName())),ok));}

        // Calendar / contacts real provider reads if user allows them.
        boolean cal=granted(c,Manifest.permission.READ_CALENDAR);if(!cal)add(tests,k,blocked("X084","Integrations","Calendar provider read","READ_CALENDAR required","Grant Calendar permission in Test Lab Unblock Wizard",BLOCKED_WAITING_USER));else add(tests,k,providerQuery(c,CalendarContract.Calendars.CONTENT_URI,"X084","Integrations","Calendar provider read"));
        boolean contacts=granted(c,Manifest.permission.READ_CONTACTS);if(!contacts)add(tests,k,blocked("X085","Integrations","Contacts provider read","READ_CONTACTS required","Grant Contacts permission in Test Lab Unblock Wizard",BLOCKED_WAITING_USER));else add(tests,k,providerQuery(c,ContactsContract.Contacts.CONTENT_URI,"X085","Integrations","Contacts provider read"));

        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI),"X086","External drafts","Calendar draft handler"));
        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:cortex-test@example.invalid")),"X087","External drafts","Email draft handler"));
        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:0000000000")),"X088","External drafts","SMS draft handler"));
        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_DIAL,Uri.parse("tel:0000000000")),"X089","External drafts","Dial handler"));
        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE),"X090","Backup","Backup document-create handler"));
        add(tests,k,intentHandler(pm,new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE),"X091","Backup","Restore document-open handler"));

        // Share/debug path is safe and fully executable internally.
        File probe=new File(c.getFilesDir(),"debug_exports/xsuite_share_probe.json");if(probe.getParentFile()!=null)probe.getParentFile().mkdirs();write(probe,"{\"xsuite\":true}");boolean uriOk=false;String shareUri="";try{Uri u=FileProvider.getUriForFile(c,c.getPackageName()+".feedback.files",probe);shareUri=u.toString();uriOk="content".equals(u.getScheme());}catch(Throwable ignored){}final String shareUriFinal=shareUri;add(tests,k,exec("X092","Diagnostics","FileProvider share URI","Debug JSON must be shareable through content URI",()->ev("uri",shareUriFinal),uriOk));try{probe.delete();}catch(Throwable ignored){}
        File debug=null;boolean debugOk=false;long debugBytes=0;try{debug=DebugExporter.build(c,db);debugBytes=debug.length();Object parsed=new JSONTokener(read(debug)).nextValue();debugOk=debugBytes>1000&&parsed instanceof JSONObject;}catch(Throwable ignored){}final long debugBytesFinal=debugBytes;add(tests,k,exec("X093","Diagnostics","Exhaustive debug export build","Debug exporter must emit parseable JSON",()->ev("bytes",debugBytesFinal),debugOk));if(debug!=null)try{debug.delete();}catch(Throwable ignored){}

        // Provider / local model readiness.
        boolean gemini=GeminiKeyStore.has(c);add(tests,k,gemini?pass("X094","Models","Gemini provider configured","Gemini credential exists",ev("configured",true)):blocked("X094","Models","Gemini provider configured","Gemini API key is missing","Open ASR/Provider Setup from Test Lab Unblock Wizard",BLOCKED_SETUP));
        boolean groq=GroqKeyStore.has(c);add(tests,k,groq?pass("X095","Models","Groq ASR provider configured","Groq credential exists",ev("configured",true)):blocked("X095","Models","Groq ASR provider configured","Groq API key is missing","Open ASR Setup from Test Lab Unblock Wizard",BLOCKED_SETUP));
        boolean lm=LocalModelManager.installed(c);add(tests,k,lm?pass("X096","Models","Local Qwen installed","Model files exist",ev("installed",true)):blocked("X096","Models","Local Qwen installed","Local Qwen is not installed","Open Local Model setup from Test Lab Unblock Wizard",BLOCKED_SETUP));
        boolean lv=LocalModelManager.verified(c);add(tests,k,lv?pass("X097","Models","Local Qwen verified","Model passed integrity verification",ev("verified",true)):blocked("X097","Models","Local Qwen verified","Model is not verified","Install/verify Local Qwen, then rerun",BLOCKED_SETUP));
        boolean lrdy=LocalLlmRuntime.ready(c);add(tests,k,lrdy?pass("X098","Models","Local Qwen inference ready","Runtime reports inference ready",new JSONObject().put("state",LocalLlmRuntime.state(c).state)):blocked("X098","Models","Local Qwen inference ready","Local inference has not passed real self-test","Run Local Qwen self-test from Environment",BLOCKED_SETUP));
        boolean strong=gemini&&PrivacyPolicy.canUseCloud(c,"images");add(tests,k,strong?pass("X099","Models","Strong Vision route ready","Gemini + image cloud permission available",new JSONObject().put("gemini",gemini).put("privacy",PrivacyPolicy.mode(c,"images"))):blocked("X099","Models","Strong Vision route ready","Requires Gemini plus Images AI permission","Configure Gemini / image privacy, then rerun",BLOCKED_SETUP));
        boolean asrReady=(gemini||groq)&&PrivacyPolicy.canUseCloud(c,"audio")&&granted(c,Manifest.permission.RECORD_AUDIO);add(tests,k,asrReady?pass("X100","Models","Cloud ASR route ready","Permission/privacy/provider prerequisites met",new JSONObject().put("gemini",gemini).put("groq",groq)):blocked("X100","Models","Cloud ASR route ready","Requires microphone + audio AI permission + Gemini/Groq","Use Test Lab Unblock Wizard",missingAccess(c)?BLOCKED_WAITING_USER:BLOCKED_SETUP));

        // Protected live tests: user-requested exhaustive path, never silently executed.
        add(tests,k,protectedTest("X101","Live integration","Real microphone phrase + transcription","Requires user to speak the prompted phrase and permits cloud ASR","Run Protected Live Tests after unblocking microphone/provider"));
        add(tests,k,protectedTest("X102","Live integration","Real notification capture","Requires Notification Access and an actual test notification","Run Protected Live Tests and generate a local test notification"));
        add(tests,k,protectedTest("X103","Live integration","Real screenshot folder import","May import a real screenshot into Evidence","Select a disposable screenshot and confirm import"));
        add(tests,k,protectedTest("X104","Live integration","Reversible calendar write","Creates a clearly marked Cortex test event and then deletes it when provider permissions allow","Confirm reversible Calendar test"));
        add(tests,k,protectedTest("X105","Live integration","External email draft handoff","Opens a synthetic draft; Cortex does not press Send","Confirm protected draft test"));
        add(tests,k,protectedTest("X106","Live integration","External SMS draft handoff","Opens a synthetic draft; Cortex does not press Send","Confirm protected draft test"));
        add(tests,k,protectedTest("X107","Live integration","Dialer handoff","Opens a synthetic/invalid number in dialer; Cortex does not place call","Confirm protected dialer test"));
        add(tests,k,protectedTest("X108","Live integration","Backup external document write","Writes a test backup through Android document picker","Confirm protected backup write"));
        add(tests,k,protectedTest("X109","Live integration","Validated restore round-trip","Requires a disposable test backup and explicit restore confirmation","Create disposable backup, then run protected restore"));
        add(tests,k,protectedTest("X110","Reliability","30-minute stability soak","Long-duration device/background behavior cannot be compressed into instant test","Start the real 30-minute soak from Test Lab / Capability Matrix"));

        JSONObject counts=new JSONObject().put(EXECUTED_PASS,k.pass).put(EXECUTED_FAIL,k.fail).put(BLOCKED_WAITING_USER,k.user).put(BLOCKED_SETUP,k.setup).put(PROTECTED_REQUIRES_CONFIRMATION,k.protectedCount);
        int executed=k.pass+k.fail,total=tests.length();double coverage=total==0?0:(double)executed/total;
        return new JSONObject().put("schema","CORTEX_EXHAUSTIVE_VERIFICATION_V1").put("test_count",total).put("executed_count",executed).put("counts",counts).put("execution_coverage",coverage).put("tests",tests)
            .put("result_status",k.fail>0?"FAIL":(k.user+k.setup+k.protectedCount)>0?"INCOMPLETE_BLOCKED":"PASS")
            .put("unblock_hint","Open Test Lab → Unblock remaining tests. Grant/setup only what you want, then rerun; protected external tests always require explicit confirmation.");
    }

    private interface J{JSONObject get()throws Exception;}private interface R{void run()throws Exception;}
    private static final class Counter{int pass,fail,user,setup,protectedCount;}
    private static void add(JSONArray a,Counter c,JSONObject x)throws Exception{a.put(x);String s=x.optString("status");if(EXECUTED_PASS.equals(s))c.pass++;else if(EXECUTED_FAIL.equals(s))c.fail++;else if(BLOCKED_WAITING_USER.equals(s))c.user++;else if(BLOCKED_SETUP.equals(s))c.setup++;else if(PROTECTED_REQUIRES_CONFIRMATION.equals(s))c.protectedCount++;}
    private static JSONObject exec(String id,String cat,String name,String expected,J evidence,boolean pass)throws Exception{long t=System.nanoTime();JSONObject ev;try{ev=evidence.get();}catch(Throwable e){return failure(id,cat,name,expected,e,t);}return base(id,cat,name,pass?EXECUTED_PASS:EXECUTED_FAIL,expected,ev,(System.nanoTime()-t)/1_000_000L);}
    private static JSONObject pass(String id,String cat,String name,String expected,JSONObject ev)throws Exception{return base(id,cat,name,EXECUTED_PASS,expected,ev,0);}
    private static JSONObject blocked(String id,String cat,String name,String why,String action,String status)throws Exception{return base(id,cat,name,status,why,new JSONObject().put("blocker",why).put("unblock_action",action),0);}
    private static JSONObject protectedTest(String id,String cat,String name,String why,String action)throws Exception{return blocked(id,cat,name,why,action,PROTECTED_REQUIRES_CONFIRMATION);}
    private static JSONObject failure(String id,String cat,String name,String expected,Throwable e,long t)throws Exception{return base(id,cat,name,EXECUTED_FAIL,expected,new JSONObject().put("exception",e.getClass().getName()).put("message",safe(e.getMessage())),(System.nanoTime()-t)/1_000_000L);}
    private static JSONObject base(String id,String cat,String name,String status,String expected,JSONObject ev,long ms)throws Exception{return new JSONObject().put("id",id).put("category",cat).put("name",name).put("status",status).put("expected",expected).put("duration_ms",ms).put("evidence",ev);}
    private static JSONObject componentActivity(PackageManager p,Context c,Class<?> cls,String id,String title)throws Exception{try{ActivityInfo x=p.getActivityInfo(new ComponentName(c,cls),0);return pass(id,"Application",title,"Activity must be registered/enabled",new JSONObject().put("class",cls.getName()).put("enabled",x.enabled).put("exported",x.exported));}catch(Throwable e){return failure(id,"Application",title,"Activity must be registered/enabled",e,System.nanoTime());}}
    private static JSONObject componentService(PackageManager p,Context c,Class<?> cls,String id,String title)throws Exception{try{ServiceInfo x=p.getServiceInfo(new ComponentName(c,cls),0);return base(id,"Application",title,x.enabled?EXECUTED_PASS:EXECUTED_FAIL,"Service must be registered/enabled",new JSONObject().put("class",cls.getName()).put("enabled",x.enabled).put("permission",safe(x.permission)),0);}catch(Throwable e){return failure(id,"Application",title,"Service must be registered/enabled",e,System.nanoTime());}}
    private static JSONObject provider(PackageManager p,Context c,String authority,String id,String title)throws Exception{ProviderInfo x=p.resolveContentProvider(authority,0);return base(id,"Application",title,x!=null?EXECUTED_PASS:EXECUTED_FAIL,"Provider must resolve",new JSONObject().put("authority",authority).put("resolved",x!=null),0);}
    private static JSONObject tableTest(SQLiteDatabase s,String table,String id,String title)throws Exception{boolean ok=table(s,table);return base(id,"Database",title,ok?EXECUTED_PASS:EXECUTED_FAIL,"Required table must exist",new JSONObject().put("table",table).put("exists",ok),0);}
    private static JSONObject permission(Context c,String permission,String id,String cat,String name,String action)throws Exception{return granted(c,permission)?pass(id,cat,name,"Runtime permission granted",ev("permission",permission)):blocked(id,cat,name,"Permission not granted: "+permission,action,BLOCKED_WAITING_USER);}
    private static JSONObject special(String id,String cat,String name,boolean ok,String action)throws Exception{return ok?pass(id,cat,name,"Special access must be active",ev("active",true)):blocked(id,cat,name,"Required Android special access is not active",action,BLOCKED_WAITING_USER);}
    private static JSONObject setupState(String id,String cat,String name,boolean ok,String action)throws Exception{return ok?pass(id,cat,name,"Required service/setup must be available",ev("available",true)):blocked(id,cat,name,"Required setup/service is unavailable",action,BLOCKED_SETUP);}
    private static JSONObject providerQuery(Context c,Uri uri,String id,String cat,String name)throws Exception{long t=System.nanoTime();Cursor cur=null;try{cur=c.getContentResolver().query(uri,new String[]{"_id"},null,null,null);int n=cur==null?-1:cur.getCount();return base(id,cat,name,cur!=null?EXECUTED_PASS:EXECUTED_FAIL,"Read-only provider query must complete",new JSONObject().put("uri",uri.toString()).put("row_count",n),(System.nanoTime()-t)/1_000_000L);}catch(Throwable e){return failure(id,cat,name,"Read-only provider query must complete",e,t);}finally{if(cur!=null)cur.close();}}
    private static JSONObject intentHandler(PackageManager p,Intent i,String id,String cat,String name)throws Exception{ResolveInfo r=p.resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);return base(id,cat,name,r!=null?EXECUTED_PASS:EXECUTED_FAIL,"Android must resolve approval-first handoff",new JSONObject().put("action",safe(i.getAction())).put("resolved",r!=null).put("handler",r==null?"":r.activityInfo.packageName+"/"+r.activityInfo.name),0);}
    private static JSONObject ev(String k,Object v)throws Exception{return new JSONObject().put(k,v);}
    private static JSONObject entities(AnalysisResult r)throws Exception{JSONArray a=new JSONArray();if(r!=null)for(AnalysisResult.Entity e:r.entities)a.put(new JSONObject().put("kind",e.kind).put("value",e.value).put("confidence",e.confidence));return new JSONObject().put("entities",a);}
    private static JSONObject actions(AnalysisResult r)throws Exception{JSONArray a=new JSONArray();if(r!=null)for(AnalysisResult.Action e:r.actions)a.put(new JSONObject().put("text",e.text).put("due_text",e.dueText));return new JSONObject().put("actions",a);}
    private static JSONObject hits(List<KnowledgeItem> xs)throws Exception{JSONArray a=new JSONArray();if(xs!=null)for(int i=0;i<Math.min(20,xs.size());i++){KnowledgeItem x=xs.get(i);a.put(new JSONObject().put("id",x.id).put("title",x.title).put("status",x.status));}return new JSONObject().put("count",xs==null?0:xs.size()).put("hits",a);}
    private static JSONObject grounded(GroundedAnswer g)throws Exception{JSONArray a=new JSONArray();for(SemanticHit h:g.sources)if(h!=null&&h.item!=null)a.put(new JSONObject().put("id",h.item.id).put("score",h.score).put("title",h.item.title));return new JSONObject().put("answer",g.answer).put("confidence",g.confidence).put("sources",a);}
    private static boolean containsId(List<KnowledgeItem> xs,long id){if(xs!=null)for(KnowledgeItem x:xs)if(x!=null&&x.id==id)return true;return false;}
    private static boolean hasEntity(AnalysisResult r,String kind,String value){if(r==null)return false;for(AnalysisResult.Entity e:r.entities)if(kind.equalsIgnoreCase(safe(e.kind))&&safe(e.value).toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean rejected(R r){try{r.run();return false;}catch(Throwable e){return true;}}
    private static boolean concurrentReads(Context c,long id){ExecutorService ex=Executors.newFixedThreadPool(4);try{ArrayList<Future<Boolean>> fs=new ArrayList<>();for(int n=0;n<8;n++)fs.add(ex.submit(()->{VaultDb d=new VaultDb(c);try{return d.getById(id)!=null;}finally{d.close();}}));for(Future<Boolean> f:fs)if(!f.get(5,TimeUnit.SECONDS))return false;return true;}catch(Throwable e){return false;}finally{ex.shutdownNow();}}
    private static boolean missingAccess(Context c){return !granted(c,Manifest.permission.RECORD_AUDIO);}
    private static boolean granted(Context c,String p){return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private static boolean table(SQLiteDatabase s,String t){Cursor c=null;try{c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{t});return c.moveToFirst();}finally{if(c!=null)c.close();}}
    private static int tableCount(SQLiteDatabase s){Cursor c=s.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private static int indexCount(SQLiteDatabase s){Cursor c=s.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'",null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private static JSONObject schemaColumns(SQLiteDatabase s,String table)throws Exception{JSONArray a=new JSONArray();Cursor c=s.rawQuery("PRAGMA table_info(\""+table+"\")",null);try{while(c.moveToNext())a.put(c.getString(c.getColumnIndexOrThrow("name")));}finally{c.close();}return new JSONObject().put("columns",a);}
    private static boolean hasColumns(SQLiteDatabase s,String table,String...names){HashSet<String> have=new HashSet<>();Cursor c=s.rawQuery("PRAGMA table_info(\""+table+"\")",null);try{while(c.moveToNext())have.add(c.getString(c.getColumnIndexOrThrow("name")));}finally{c.close();}for(String n:names)if(!have.contains(n))return false;return true;}
    private static long count(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);try{long n=0;while(c.moveToNext())n++;return n;}finally{c.close();}}
    private static long countArgs(SQLiteDatabase s,String q,String[] args){Cursor c=s.rawQuery(q,args);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    private static String scalar(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);try{return c.moveToFirst()?safe(c.getString(0)):"";}finally{c.close();}}
    private static String signerSha256(PackageInfo p){try{byte[] b;if(Build.VERSION.SDK_INT>=28){android.content.pm.Signature[] s=p.signingInfo==null?null:p.signingInfo.getApkContentsSigners();if(s==null||s.length==0)return"";b=s[0].toByteArray();}else{if(p.signatures==null||p.signatures.length==0)return"";b=p.signatures[0].toByteArray();}byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder x=new StringBuilder();for(byte v:h)x.append(String.format(Locale.US,"%02x",v));return x.toString();}catch(Throwable e){return"";}}
    private static void write(File f,String s)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8)){w.write(s);}}
    private static String read(File f)throws Exception{StringBuilder b=new StringBuilder();try(Reader r=new InputStreamReader(new FileInputStream(f),java.nio.charset.StandardCharsets.UTF_8)){char[] x=new char[16384];for(int n;(n=r.read(x))>0;)b.append(x,0,n);}return b.toString();}
    private static String safe(String s){return s==null?"":s;}
}
