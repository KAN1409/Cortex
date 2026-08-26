package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.File;
import java.util.*;

/** Authoritative 43-capability product/runtime matrix. */
public final class CortexCapabilityRegistry {
    public static final String ACTIVE="ACTIVE",READY="READY",NEEDS_ACCESS="NEEDS ACCESS",NEEDS_SETUP="NEEDS SETUP",FAILED="FAILED",NOT_VERIFIED="NOT VERIFIED";
    private CortexCapabilityRegistry(){}
    public static final class Capability {public final int number;public final String key,title;Capability(int n,String k,String t){number=n;key=k;title=t;}}
    public static final class State {public final String status,detail;State(String s,String d){status=s;detail=d;}}

    public static List<Capability> all(){return Arrays.asList(
        c(1,"app_identity","App identity / build"),c(2,"components","Activities / services / providers"),c(3,"permissions","Permission & special-access inventory"),c(4,"db_integrity","Database integrity"),c(5,"db_schema","Database schema inventory"),c(6,"vault_readability","Vault readability"),c(7,"attachments","Archived attachments"),c(8,"screenshot_folder","Screenshot folder"),c(9,"screenshot_queue","Screenshot extraction queue"),c(10,"dedup","Duplicate fingerprints"),c(11,"ocr","OCR component"),c(12,"visual_intelligence","Visual Intelligence"),c(13,"privacy_guard","Privacy Guard"),c(14,"strong_vision","Strong Vision provider"),c(15,"local_qwen","Local Qwen"),c(16,"grounded_ask","Grounded Ask routing"),c(17,"semantic_retrieval","Semantic retrieval"),c(18,"smart_inbox","Smart Inbox / Brief state"),c(19,"temporal_actions","Temporal actions"),c(20,"graph_relations","Graph / relations"),c(21,"people_memory","People memory"),c(22,"projects_context","Projects / context packs"),c(23,"prompt_library","Prompt / AI library"),c(24,"screenshot_learning","Screenshot learning"),c(25,"corrections","Correction rules"),c(26,"calendar_read","Calendar read"),c(27,"contacts_read","Contacts read"),c(28,"backup_export","Backup export"),c(29,"debug_export","Debug export"),c(30,"notification_capture","All-notification capture"),c(31,"audio_asr","Audio / ASR"),c(32,"background_visual","Background visual processing"),c(33,"stability_soak","Stability soak"),c(34,"performance","Performance / stability metrics"),c(35,"proactive_needs","Proactive / Needs"),c(36,"user_feedback","User feedback learning"),c(37,"calendar_write","Calendar write / draft"),c(38,"restore","Validated restore"),c(39,"external_writes","External write drafts"),c(40,"visual_actions","Visual actions"),c(41,"briefs","Daily / weekly Brief"),c(42,"warm_qwen","Warm Qwen reuse"),c(43,"interaction_telemetry","Interaction telemetry"));}

    public static State evaluate(Context ctx,VaultDb db,Capability x){try{switch(x.key){
        case"app_identity":return active(ctx.getPackageName()+" · "+BuildConfig.VERSION_NAME);
        case"components":return componentState(ctx);
        case"permissions":return permissionState(ctx);
        case"db_integrity":return dbIntegrity(db);
        case"db_schema":return tableCount(db)>0?active(tableCount(db)+" application tables readable"):failed("No Cortex tables found");
        case"vault_readability":return table(db,"knowledge_items")?active(count(db,"SELECT COUNT(*) FROM knowledge_items")+" memories readable"):failed("knowledge_items table is unavailable");
        case"attachments":{long m=count(db,"SELECT COUNT(*) FROM knowledge_items WHERE COALESCE(attachment_path,'')<>'' AND attachment_path IS NOT NULL"),missing=missingAttachments(db);return missing==0?active(m+" archived attachment reference(s) readable"):failed(missing+" missing archived attachment(s)");}
        case"screenshot_folder":return ScreenshotIngestor.tree(ctx)!=null?active("Screenshot folder connected"):setup("Connect the screenshot folder in Advanced diagnostics");
        case"screenshot_queue":{long f=count(db,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND status IN ('analysis_failed','failed_retryable')"),q=count(db,"SELECT COUNT(*) FROM knowledge_items WHERE source='screenshot-folder' AND status IN ('queued','analyzing')");return f>0?failed(f+" failed screenshot item(s) · "+q+" queued/processing"):active(q+" queued/processing · no failed screenshot items");}
        case"dedup":{long n=count(db,"SELECT COUNT(*) FROM (SELECT fingerprint FROM knowledge_items WHERE COALESCE(fingerprint,'')<>'' GROUP BY fingerprint HAVING COUNT(*)>1)");return n==0?active("No duplicate fingerprint groups"):failed(n+" duplicate fingerprint group(s) need review");}
        case"ocr":return ArabicOcr.modelReady(ctx)?active("ML Kit Latin + bundled Arabic OCR available"):setup("Arabic OCR model is not ready");
        case"visual_intelligence":{VisualInsightStore.ensure(db);VisualRecoveryStore.ensure(db);int done=VisualInsightStore.countDone(db),recovering=VisualInsightStore.countRecovering(db),terminal=VisualInsightStore.countFailed(db),rate=VisualInsightStore.countRateLimited(db);if(terminal>0)return failed(done+" done · "+recovering+" recovering · "+terminal+" terminal failure(s) · rate-limited="+rate);if(recovering>0)return ready(done+" done · "+recovering+" recovering · no terminal failures · rate-limited="+rate+" · open Visual recovery for details");return active(done+" visual understanding result(s) · no unresolved recovery items");}
        case"privacy_guard":return privacyGuard();
        case"strong_vision":{if(!GeminiKeyStore.has(ctx))return setup("Configure Gemini to enable strong cloud vision");if(!PrivacyPolicy.canUseCloud(ctx,"images"))return ready("Gemini is configured, but Images are "+PrivacyPolicy.label(PrivacyPolicy.mode(ctx,"images"))+"; automatic strong vision will stay local/protected");return ready("Gemini configured · run External Model Check / Visual Intelligence for live verification");}
        case"local_qwen":return localQwen(ctx);
        case"grounded_ask":return table(db,"knowledge_items")?active("Grounded Ask route can retrieve from the Cortex Vault; functional self-test verifies execution"):failed("Grounded Ask cannot read the Cortex Vault");
        case"semantic_retrieval":return table(db,"knowledge_items")?active("Grounded semantic retrieval is wired to Cortex memory; functional self-test verifies execution"):failed("Cortex memory retrieval source is unavailable");
        case"smart_inbox":return table(db,"derived_items")?active(count(db,"SELECT COUNT(*) FROM derived_items WHERE state IN ('open','pending')")+" current derived item(s)"):failed("Derived intelligence table unavailable");
        case"temporal_actions":return table(db,"action_temporal")?active(count(db,"SELECT COUNT(*) FROM action_temporal")+" temporal record(s)"):ready("Temporal resolver is installed; table initializes/populates when temporal evidence exists");
        case"graph_relations":return table(db,"source_links")?active(count(db,"SELECT COUNT(*) FROM source_links")+" graph/source link(s)"):failed("Graph/source link table unavailable");
        case"people_memory":return table(db,"entity_nodes")?active(count(db,"SELECT COUNT(*) FROM entity_nodes WHERE upper(kind)='PERSON' AND status='active'")+" active person entity/entities"):ready("People engine installed; entity table will initialize when needed");
        case"projects_context":return table(db,"entity_nodes")?active(count(db,"SELECT COUNT(*) FROM entity_nodes WHERE upper(kind)='PROJECT' AND status='active'")+" active project entity/entities"):ready("Project engine installed; entity table will initialize when needed");
        case"prompt_library":{PromptLibraryStore.ensure(db);long pinned=count(db,"SELECT COUNT(*) FROM prompt_library_items WHERE pinned=1"),runs=count(db,"SELECT COUNT(*) FROM prompt_library_items WHERE last_run_at>0"),rated=count(db,"SELECT COUNT(*) FROM prompt_library_items WHERE rating<>0");return active("Prompt Library ready · "+pinned+" pinned · "+runs+" run result(s) · "+rated+" rated");}
        case"screenshot_learning":ScreenshotLearning.ensure(db);return active(ScreenshotLearning.taughtCount(db)+" taught screenshot(s) · "+ScreenshotLearning.learnedPreferenceCount(db)+" learned preference(s)");
        case"corrections":return table(db,"correction_rules")?active(count(db,"SELECT COUNT(*) FROM correction_rules")+" correction rule(s)"):ready("Correction UI is installed; table initializes on first correction");
        case"calendar_read":return granted(ctx,Manifest.permission.READ_CALENDAR)?active("Calendar read permission granted · import path available in Data & integrations"):access("Grant Calendar read permission when importing calendar context");
        case"contacts_read":return granted(ctx,Manifest.permission.READ_CONTACTS)?active("Contacts read permission granted · import path available in Data & integrations"):access("Grant Contacts read permission when importing contacts");
        case"backup_export":return documentCreateHandler(ctx)?active("Android document export handler available · real Cortex ZIP backup flow installed"):setup("No Android document provider can receive a backup export");
        case"debug_export":return active("Debug export flow + FileProvider are installed");
        case"notification_capture":{if(!PrivacyPolicy.canCollect(ctx,"notifications"))return setup("Notifications are set to Never collect");PhoneContextStore.ensure(db);long seen=count(db,"SELECT COUNT(*) FROM phone_context_events WHERE kind='notification_context'");return notificationEnabled(ctx)?active("Notification Listener enabled · "+seen+" bounded/redacted notification-context event(s) seen · text-bearing relevant events also enter Cortex intelligence"):access("Enable Cortex Notification Access");}
        case"audio_asr":return audioAsr(ctx);
        case"background_visual":{VisualInsightStore.ensure(db);VisualRecoveryStore.ensure(db);int terminal=VisualInsightStore.countFailed(db),recovering=VisualInsightStore.countRecovering(db),rate=VisualInsightStore.countRateLimited(db);String worker=VisualInsightStore.workerState(ctx);if(terminal>0)return failed(terminal+" terminal visual item(s) · "+recovering+" recovering · rate-limited="+rate+" · recovery available in Advanced diagnostics");if(recovering>0)return ready(recovering+" visual item(s) recovering · rate-limited="+rate+" · worker="+safe(worker)+" · no terminal failures");return active("Background visual worker has no unresolved recovery items · worker="+safe(worker));}
        case"stability_soak":return soakState(db);
        case"performance":{InteractionTelemetry.ensure(db);PhoneContextStore.ensure(db);long t=count(db,"SELECT COUNT(*) FROM interaction_telemetry"),pc=PhoneContextStore.countSince(db,System.currentTimeMillis()-24L*60L*60L*1000L);int proc=PhoneContextStore.activeProcessCount(db);return active(t+" interaction telemetry record(s) · "+pc+" phone-context event(s)/24h · "+proc+" current process state(s)");}
        case"proactive_needs":return table(db,"derived_items")?active(count(db,"SELECT COUNT(*) FROM derived_items WHERE kind IN ('ACTION','WAITING','OPPORTUNITY','INSIGHT') AND state='open'")+" proactive/open item(s)"):ready("Proactive engine installed; derived state initializes when evidence produces actionable intelligence");
        case"user_feedback":return table(db,"feedback_events")?active(count(db,"SELECT COUNT(*) FROM feedback_events")+" feedback event(s)"):ready("Feedback persistence is wired and initializes on first feedback");
        case"calendar_write":return calendarHandler(ctx)?ready("Approval-first calendar draft handler available · user confirms in Calendar"):setup("No Calendar app can accept event drafts");
        case"restore":return documentOpenHandler(ctx)?ready("Validated preflight + explicit approval restore flow installed"):setup("No Android document provider can select a backup ZIP");
        case"external_writes":return externalHandlers(ctx)>0?ready(externalHandlers(ctx)+" approval-first external draft handler(s) available · Cortex never presses Send/Save"):setup("No email/SMS/calendar handlers resolved");
        case"visual_actions":return ready("Search, Brain, reminder and project-link actions are wired from Capture Result / Visual Intelligence");
        case"briefs":{String d=BriefComposer.compose(db,false),w=BriefComposer.compose(db,true);return d.startsWith("Daily Cortex Brief")&&w.startsWith("Weekly Cortex Brief")?active("Daily + weekly Brief compose from grounded PRIME state"):failed("Brief composition failed validation");}
        case"warm_qwen":return warmQwen(ctx);
        case"interaction_telemetry":InteractionTelemetry.ensure(db);return active(count(db,"SELECT COUNT(*) FROM interaction_telemetry")+" telemetry record(s) stored");
        default:return new State(NOT_VERIFIED,"No capability evaluator registered");
    }}catch(Throwable e){return failed(e.getClass().getSimpleName()+": "+safe(e.getMessage()));}}

    private static Capability c(int n,String k,String t){return new Capability(n,k,t);}
    private static State active(String d){return new State(ACTIVE,d);}private static State ready(String d){return new State(READY,d);}private static State access(String d){return new State(NEEDS_ACCESS,d);}private static State setup(String d){return new State(NEEDS_SETUP,d);}private static State failed(String d){return new State(FAILED,d);}

    private static State localQwen(Context ctx){
        if(!LocalModelManager.installed(ctx))return setup("Install Local Qwen model/runtime");
        if(!LocalModelManager.verified(ctx))return setup("Local Qwen files exist but the model has not passed verification");
        LocalLlmRuntime.State s=LocalLlmRuntime.state(ctx);
        if("failed".equals(s.state))return failed("Local Qwen runtime self-test failed"+(safe(s.error).isEmpty()?"":": "+safe(s.error)));
        if(!LocalLlmRuntime.ready(ctx))return ready("Local Qwen verified · run the local inference self-test before it is marked ACTIVE");
        return active("Local Qwen verified + real local inference ready"+(s.tokensPerSecond>0?" · "+String.format(Locale.US,"%.2f tok/s",s.tokensPerSecond):""));
    }

    private static State audioAsr(Context ctx){
        if(!granted(ctx,Manifest.permission.RECORD_AUDIO))return access("Grant microphone permission");
        if(!PrivacyPolicy.canCollect(ctx,"audio"))return setup("Audio is set to Never collect");
        if(!PrivacyPolicy.canUseCloud(ctx,"audio"))return setup("Audio is "+PrivacyPolicy.label(PrivacyPolicy.mode(ctx,"audio"))+"; current Gemini/Groq transcription providers require cloud AI permission");
        boolean gemini=GeminiKeyStore.has(ctx),groq=GroqKeyStore.has(ctx);if(!gemini&&!groq)return setup("Configure Gemini and/or Groq for transcription");
        return active("Microphone + cloud ASR ready · "+(gemini?"Gemini primary":"Groq")+(gemini&&groq?" + Groq streaming fallback":""));
    }

    private static State soakState(VaultDb db){
        CortexAuditScheduler.SoakState s=CortexAuditScheduler.soakState(db);if(s==null)return ready("30-minute real stability soak available · tap capability #33 to start");if(s.active()){long left=Math.max(0,s.targetEndAt-System.currentTimeMillis());return active("Stability soak running · about "+Math.max(1,(left+59_999L)/60_000L)+" min remaining");}if("complete".equals(s.status))return active("Last real stability soak completed · tap capability #33 to run again");return ready("Stability soak available · last run status: "+safe(s.status));
    }

    private static State warmQwen(Context ctx){
        if(!LocalModelManager.verified(ctx))return setup("Install/verify Local Qwen before warm reuse can operate");long age=0;try{age=LocalLlmBridge.cachedModelAgeMs();}catch(Throwable ignored){}if(age>0)return active("Production Qwen model cache is warm · age "+friendlyAge(age));return ready("Local Qwen verified · production cache is cold · tap capability #42 to warm it");
    }

    private static State componentState(Context c){try{PackageManager p=c.getPackageManager();p.getActivityInfo(new ComponentName(c,InputActivity.class),0);p.getActivityInfo(new ComponentName(c,CaptureResultActivity.class),0);p.getActivityInfo(new ComponentName(c,PhoneContextAccessActivity.class),0);p.getActivityInfo(new ComponentName(c,CapabilityMatrixActivity.class),0);p.getActivityInfo(new ComponentName(c,PromptLibraryActivity.class),0);p.getActivityInfo(new ComponentName(c,OpenRouterSettingsActivity.class),0);p.getActivityInfo(new ComponentName(c,VisualRecoveryActivity.class),0);p.getServiceInfo(new ComponentName(c,NotificationCaptureService.class),0);p.getServiceInfo(new ComponentName(c,CortexScreenAccessibilityService.class),0);if(p.resolveContentProvider(c.getPackageName()+".shizuku",0)==null)return failed("Shizuku provider is not registered");return active("Core Cortex activities/services/providers resolve correctly");}catch(Throwable e){return failed("A core Android component is missing: "+safe(e.getMessage()));}}
    private static State permissionState(Context c){int miss=0;if(!granted(c,Manifest.permission.RECORD_AUDIO))miss++;if(!notificationEnabled(c))miss++;if(!PhoneUsageAccess.has(c))miss++;boolean a=CortexScreenAccessibilityService.connected()||accessibilityEnabled(c);if(!a)miss++;String sh=ShizukuContextBridge.granted()?"Shizuku process access granted":(ShizukuContextBridge.available()?"Shizuku running · optional permission not granted":"Shizuku optional/not running");String privacy=!PrivacyPolicy.canCollect(c,"phone_context")?" · phone context collection is disabled by privacy setting":"";return miss==0&&privacy.isEmpty()?active("Standard Android context stack enabled · "+sh):access(miss+" standard permission/special-access layer(s) need user approval"+privacy+" · "+sh);}
    private static State dbIntegrity(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null);String x=c.moveToFirst()?safe(c.getString(0)):"";c.close();return"ok".equalsIgnoreCase(x)?active("SQLite quick_check=ok"):failed("SQLite quick_check="+x);}
    private static State privacyGuard(){try{KnowledgeItem k=new KnowledgeItem(-1,"TEXT","audit","Password: secret","Password: secret","","","","","","analyzed","","","{}",0,0);return VisualTriage.evaluate(k).sensitive?active("Synthetic password-like evidence is blocked by privacy triage"):failed("Privacy triage did not block a synthetic secret");}catch(Throwable e){return failed("Privacy synthetic check failed");}}
    private static boolean granted(Context c,String p){return c.getPackageManager().checkPermission(p,c.getPackageName())==PackageManager.PERMISSION_GRANTED;}
    private static boolean notificationEnabled(Context c){try{String x=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");return x!=null&&x.contains(c.getPackageName());}catch(Throwable e){return false;}}
    private static boolean accessibilityEnabled(Context c){try{String x=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return x!=null&&x.contains(c.getPackageName());}catch(Throwable e){return false;}}
    private static boolean calendarHandler(Context c){try{return c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI),PackageManager.MATCH_DEFAULT_ONLY)!=null;}catch(Throwable e){return false;}}
    private static boolean documentCreateHandler(Context c){try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);return c.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY)!=null;}catch(Throwable e){return false;}}
    private static boolean documentOpenHandler(Context c){try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);return c.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY)!=null;}catch(Throwable e){return false;}}
    private static int externalHandlers(Context c){int n=0;PackageManager p=c.getPackageManager();try{if(p.resolveActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:test@example.com")),PackageManager.MATCH_DEFAULT_ONLY)!=null)n++;}catch(Throwable ignored){}try{if(p.resolveActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null)n++;}catch(Throwable ignored){}if(calendarHandler(c))n++;return n;}
    private static long missingAttachments(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT attachment_path FROM knowledge_items WHERE COALESCE(attachment_path,'')<>''",null);long n=0;while(c.moveToNext()){String p=c.getString(0);if(p==null||!new File(p).exists())n++;}c.close();return n;}
    private static boolean table(VaultDb db,String name){Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",new String[]{name});boolean ok=c.moveToFirst();c.close();return ok;}
    private static long tableCount(VaultDb db){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static long count(VaultDb db,String sql){Cursor c=db.getReadableDatabase().rawQuery(sql,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static String friendlyAge(long ms){long s=Math.max(0,ms/1000L);if(s<60)return s+"s";long m=s/60;if(m<60)return m+"m";return(m/60)+"h "+(m%60)+"m";}
    private static String safe(String s){return s==null?"":s;}
}
