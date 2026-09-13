package com.kareem.cortex;

import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.MediaStore;
import org.json.*;
import java.io.File;
import java.util.*;

/**
 * Final product acceptance assessment.
 *
 * Every case records: product goal, expected behavior, actual observed behavior and verdict.
 * It is read-only against personal data; it never fabricates a PASS by creating personal state.
 */
public final class CortexFinalAcceptanceAssessment {
    public static final class Case {
        public final String area,name,goal,expected,actual,status;
        public final boolean critical;
        Case(String a,String n,String g,String e,String x,String s,boolean c){area=a;name=n;goal=g;expected=e;actual=x;status=s;critical=c;}
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("area",area);o.put("name",name);o.put("goal",goal);o.put("expected",expected);o.put("actual",actual);o.put("status",status);o.put("critical",critical);}catch(Exception ignored){}return o;}
    }
    public static final class Report {
        public int pass,warn,fail;public final ArrayList<Case> cases=new ArrayList<>();
        public boolean shipReady(){return fail==0;}
        public JSONObject json(){JSONObject root=new JSONObject();JSONArray a=new JSONArray();try{for(Case c:cases)a.put(c.json());root.put("version","final_acceptance_001");root.put("pass",pass);root.put("warn",warn);root.put("fail",fail);root.put("ship_ready",shipReady());root.put("cases",a);}catch(Exception ignored){}return root;}
        public String text(){StringBuilder b=new StringBuilder();b.append(shipReady()?"FINAL ACCEPTANCE: SHIP READY":"FINAL ACCEPTANCE: NOT READY").append("\n").append(pass).append(" pass · ").append(warn).append(" warning · ").append(fail).append(" failure");for(Case c:cases)b.append("\n\n[").append(c.status.toUpperCase(Locale.ROOT)).append("] ").append(c.area).append(" · ").append(c.name).append("\nGoal: ").append(c.goal).append("\nExpected: ").append(c.expected).append("\nActual: ").append(c.actual);return b.toString();}
    }

    private CortexFinalAcceptanceAssessment(){}

    public static Report run(Context context){
        Report r=new Report();if(context==null){add(r,"Application","Context","Run safely on-device","Valid Android context","Context unavailable","fail",true);return r;}
        Context ctx=context.getApplicationContext();VaultDb db=null;
        try{db=new VaultDb(ctx);identity(ctx,r);database(db,r);captureSurface(ctx,r);voice(ctx,db,r);photo(ctx,db,r);evidenceBoundary(db,r);pipelineHealth(db,r);brain(ctx,r);attention(db,r);workVault(db,r);actions(ctx,r);privacy(ctx,r);diagnostics(ctx,db,r);}
        catch(Throwable e){add(r,"Assessment","Harness","Assessment must finish without hiding failures","Complete report","Harness exception: "+safe(e),"fail",true);}
        finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        return r;
    }

    private static void identity(Context ctx,Report r)throws Exception{
        PackageInfo p=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0);boolean ok="com.kareem.cortex".equals(ctx.getPackageName());add(r,"Application","Identity & update contract","Cortex remains the same installed app and preserves update compatibility","Package com.kareem.cortex with readable build identity",ctx.getPackageName()+" · "+p.versionName+" · build "+(android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode),ok?"pass":"fail",true);
    }

    private static void database(VaultDb db,Report r){try{SQLiteDatabase s=db.getReadableDatabase();String quick=scalar(s,"PRAGMA quick_check(1)");boolean fields=column(s,"knowledge_items","raw_text")&&column(s,"knowledge_items","attachment_path")&&column(s,"knowledge_items","status");add(r,"Evidence core","Vault integrity","Original evidence must remain inspectable while derived understanding stays separate","SQLite healthy and source/status columns present","quick_check="+quick+" · evidence columns="+fields,("ok".equalsIgnoreCase(quick)&&fields)?"pass":"fail",true);}catch(Throwable e){add(r,"Evidence core","Vault integrity","Original evidence must survive safely","Healthy readable Vault","Exception: "+safe(e),"fail",true);}}

    private static void captureSurface(Context ctx,Report r){try{PackageManager pm=ctx.getPackageManager();boolean hub=activity(pm,ctx,CaptureHubActivity.class),sheet=activity(pm,ctx,ProposalCaptureActivity.class),result=activity(pm,ctx,ProposalCaptureResultActivity.class),voice=activity(pm,ctx,VoiceLibraryActivity.class),detail=activity(pm,ctx,VoiceDetailActivity.class);boolean ok=hub&&sheet&&result&&voice&&detail;add(r,"Capture","Core capture flow","Capture should be a coherent evidence-first path from input to inspectable result","Capture Hub, composer, result, Voice Library and Voice Detail all registered","hub="+hub+" · composer="+sheet+" · result="+result+" · voice library="+voice+" · voice detail="+detail,ok?"pass":"fail",true);}catch(Throwable e){add(r,"Capture","Core capture flow","Core capture flow must resolve","All capture activities registered","Exception: "+safe(e),"fail",true);}}

    private static void voice(Context ctx,VaultDb db){ }
    private static void voice(Context ctx,VaultDb db,Report r){
        try{
            PackageManager pm=ctx.getPackageManager();PackageInfo p=pm.getPackageInfo(ctx.getPackageName(),PackageManager.GET_PERMISSIONS);boolean micDeclared=false;if(p.requestedPermissions!=null)for(String x:p.requestedPermissions)if(Manifest.permission.RECORD_AUDIO.equals(x))micDeclared=true;
            boolean local=false;try{local=new AndroidOnDeviceAsrCandidate().isReady(ctx);}catch(Throwable ignored){}boolean cloud=GeminiKeyStore.has(ctx)||GroqKeyStore.has(ctx);boolean route=local||cloud;
            SQLiteDatabase s=db.getReadableDatabase();long now=System.currentTimeMillis();long stale=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type='AUDIO' AND source IN ('manual_recording','audio_import') AND status='analyzing' AND updated_at<"+(now-120000L));long oldQueued=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type='AUDIO' AND source IN ('manual_recording','audio_import') AND status='queued' AND updated_at<"+(now-120000L));long failed=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type='AUDIO' AND source IN ('manual_recording','audio_import') AND status IN ('analysis_failed','failed_retryable')");long analyzed=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE type='AUDIO' AND source IN ('manual_recording','audio_import') AND status='analyzed'");
            add(r,"Voice","Record without cloud dependency","Recording must start with microphone permission; cloud credentials are fallback, not a prerequisite","RECORD_AUDIO declared and dedicated bounded voice pipeline present","permission declared="+micDeclared+" · VoiceCapturePipeline present="+classExists("com.kareem.cortex.VoiceCapturePipeline"),(micDeclared&&classExists("com.kareem.cortex.VoiceCapturePipeline"))?"pass":"fail",true);
            add(r,"Voice","Transcription route","A saved WAV should have at least one real path to transcription","On-device ASR ready or configured cloud fallback","on-device="+local+" · cloud fallback configured="+cloud,route?"pass":"warn",false);
            add(r,"Voice","No permanent Processing state","Manual voice must either finish or expose a retryable failure; it must not remain stale Processing","0 stale analyzing and 0 manual queued older than 2 minutes","stale analyzing="+stale+" · old queued="+oldQueued+" · analyzed="+analyzed+" · retryable/failed="+failed,(stale==0&&oldQueued==0)?"pass":"fail",true);
            Cursor c=s.rawQuery("SELECT attachment_path,status,updated_at FROM knowledge_items WHERE type='AUDIO' AND source='manual_recording' ORDER BY created_at DESC LIMIT 1",null);if(c.moveToFirst()){String path=c.getString(0),st=c.getString(1);boolean exists=path!=null&&new File(path).isFile();add(r,"Voice","Original WAV preservation","Voice playback/transcript must derive from the stored original","Latest manual voice points to an existing source WAV","status="+st+" · source file exists="+exists,exists?"pass":"fail",true);}else add(r,"Voice","Original WAV preservation","Voice playback/transcript must derive from the stored original","When a recording exists its WAV remains present","No manual voice recording exists yet","warn",false);c.close();
        }catch(Throwable e){add(r,"Voice","Pipeline health","Voice capture must be bounded and diagnosable","Voice assessment completes","Exception: "+safe(e),"fail",true);}
    }

    private static void photo(Context ctx,VaultDb db,Report r){
        try{
            PackageManager pm=ctx.getPackageManager();Intent cam=new Intent(MediaStore.ACTION_IMAGE_CAPTURE),gallery=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");boolean camHandler=cam.resolveActivity(pm)!=null,galleryHandler=gallery.resolveActivity(pm)!=null;
            add(r,"Photo","Camera or gallery chooser","Photo capture must explicitly let the user take a photo or choose an existing one","Camera and image document handlers are available from the capture composer","camera handler="+camHandler+" · gallery handler="+galleryHandler,(camHandler&&galleryHandler)?"pass":(galleryHandler?"warn":"fail"),!galleryHandler);
            Cursor c=db.getReadableDatabase().rawQuery("SELECT attachment_path,source,status FROM knowledge_items WHERE type IN ('IMAGE','SCREENSHOT') AND source IN ('manual','android_share','quick_capture') ORDER BY created_at DESC LIMIT 1",null);if(c.moveToFirst()){String p=c.getString(0);boolean exists=p!=null&&new File(p).isFile();add(r,"Photo","Original image preservation","Captured/imported images remain original evidence before visual understanding","Latest intentional image attachment exists","source="+c.getString(1)+" · status="+c.getString(2)+" · file exists="+exists,exists?"pass":"fail",true);}else add(r,"Photo","Original image preservation","Captured/imported images remain original evidence before visual understanding","When an intentional image exists its file remains present","No intentional image capture exists yet","warn",false);c.close();
        }catch(Throwable e){add(r,"Photo","Capture flow","Photo chooser and evidence storage must be diagnosable","Photo assessment completes","Exception: "+safe(e),"fail",true);}
    }

    private static void evidenceBoundary(VaultDb db,Report r){
        try{
            KnowledgeItem manual=new KnowledgeItem(-1,"TEXT","manual","x","x","","","","","","analyzed","","","{}",0,0),notif=new KnowledgeItem(-2,"NOTIFICATION","notification_listener","x","x","","","","","","analyzed","","","{}",0,0),calendar=new KnowledgeItem(-3,"CALENDAR_EVENT","calendar_sync","x","x","","","","","","analyzed","","","{}",0,0);
            boolean ok=IntentionalCapturePolicy.visibleInCapturedLibrary(manual)&&!IntentionalCapturePolicy.visibleInCapturedLibrary(notif)&&!IntentionalCapturePolicy.visibleInCapturedLibrary(calendar);
            add(r,"Evidence hygiene","Intentional vs passive evidence","Captured Evidence must not be polluted by weather, notifications, calendar or ambient phone observations","Manual capture visible; passive notification/calendar hidden from captured library","manual="+IntentionalCapturePolicy.visibleInCapturedLibrary(manual)+" · notification="+IntentionalCapturePolicy.visibleInCapturedLibrary(notif)+" · calendar="+IntentionalCapturePolicy.visibleInCapturedLibrary(calendar),ok?"pass":"fail",true);
        }catch(Throwable e){add(r,"Evidence hygiene","Intentional vs passive evidence","Captured library stays semantically clean","Policy test passes","Exception: "+safe(e),"fail",true);}
    }

    private static void pipelineHealth(VaultDb db,Report r){try{SQLiteDatabase s=db.getReadableDatabase();long queued=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE status='queued'"),analyzing=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE status='analyzing'"),failed=count(s,"SELECT COUNT(*) FROM knowledge_items WHERE status IN ('analysis_failed','failed_retryable')");String st=analyzing>20?"fail":(queued>500||failed>100?"warn":"pass");add(r,"Reliability","Analysis backlog","Capture pipeline should make forward progress and expose failures rather than silently wedge","No pathological active/backlog state","queued="+queued+" · analyzing="+analyzing+" · failed/retryable="+failed,st,"fail".equals(st));}catch(Throwable e){add(r,"Reliability","Analysis backlog","Pipeline remains measurable","Queue metrics readable","Exception: "+safe(e),"fail",true);}}

    private static void brain(Context ctx,Report r){try{boolean ext=ExternalBrainProvider.configured(ctx),local=LocalModelManager.installed(ctx)&&LocalModelManager.verified(ctx);add(r,"Brain","Reasoning availability","Cortex should reason through a configured external brain or verified local model while preserving evidence boundaries","At least one reasoning route available","external="+ext+" · verified local="+local,(ext||local)?"pass":"warn",false);}catch(Throwable e){add(r,"Brain","Reasoning availability","Reasoning route is explicit","Provider state readable","Exception: "+safe(e),"warn",false);}}

    private static void attention(VaultDb db,Report r){try{SQLiteDatabase s=db.getReadableDatabase();boolean judge=classExists("com.kareem.cortex.CortexAttentionJudge"),triage=classExists("com.kareem.cortex.AttentionDecisionEngine");long open=table(s,"ue_attention_items")?count(s,"SELECT COUNT(*) FROM ue_attention_items WHERE state='open'"):0;add(r,"Attention","Single final judgment authority","Triage may rank, but final user interruption must remain owned by CortexAttentionJudge","Judge and triage components both exist; shown items are explicit","judge="+judge+" · triage="+triage+" · currently open attention items="+open,(judge&&triage)?"pass":"fail",true);}catch(Throwable e){add(r,"Attention","Judgment boundary","Attention materialization remains bounded","Boundary components readable","Exception: "+safe(e),"fail",true);}}

    private static void workVault(VaultDb db,Report r){try{boolean workspace=classExists("com.kareem.cortex.WorkVaultActivity"),ask=classExists("com.kareem.cortex.WorkVaultAskActivity"),builder=classExists("com.kareem.cortex.WorkDocumentBuilderBridge");add(r,"Work","Professional archive","Work Vault should retain project evidence separately from generated/derived documents and remain queryable","Work Vault workspace, archive query and document bridge components exist","workspace="+workspace+" · ask archive="+ask+" · document bridge="+builder,(workspace&&ask&&builder)?"pass":"warn",false);}catch(Throwable e){add(r,"Work","Professional archive","Work tools remain available","Work components readable","Exception: "+safe(e),"warn",false);}}

    private static void actions(Context ctx,Report r){try{PackageManager pm=ctx.getPackageManager();boolean calendar=pm.resolveActivity(new Intent(Intent.ACTION_INSERT,android.provider.CalendarContract.Events.CONTENT_URI),PackageManager.MATCH_DEFAULT_ONLY)!=null;boolean mail=pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:test@example.com")),PackageManager.MATCH_DEFAULT_ONLY)!=null;add(r,"Actions","Approval-first handoff","External actions must be explicit user-approved handoffs, not silent execution","At least one external handoff target resolves; absence is reported, never faked","calendar="+calendar+" · email="+mail,(calendar||mail)?"pass":"warn",false);}catch(Throwable e){add(r,"Actions","Approval-first handoff","External actions fail safely","Handler state readable","Exception: "+safe(e),"warn",false);}}

    private static void privacy(Context ctx,Report r){try{KnowledgeItem unknown=new KnowledgeItem(-9,"TEXT","future_unknown_source","x","x","","","","","","analyzed","","","{}",0,0);boolean blocked=!CloudEvidencePolicy.canSend(ctx,unknown);add(r,"Privacy","Default-deny unknown evidence","Unknown/new evidence sources must not silently cross the cloud boundary","Unknown source is blocked from cloud send","blocked="+blocked,blocked?"pass":"fail",true);}catch(Throwable e){add(r,"Privacy","Default-deny unknown evidence","Cloud boundary defaults safe","Synthetic policy check passes","Exception: "+safe(e),"fail",true);}}

    private static void diagnostics(Context ctx,VaultDb db,Report r){try{boolean audit=classExists("com.kareem.cortex.CortexAuditWorker"),export=classExists("com.kareem.cortex.CortexDiagnosticExporter"),self=classExists("com.kareem.cortex.CortexFunctionalSelfTest");add(r,"Diagnostics","Expected vs actual assessment","The app must be able to explain what should happen and what actually happened for every final acceptance case","Audit worker, diagnostic export and functional self-test are present; this report emits expected+actual for each case","audit="+audit+" · exporter="+export+" · functional self-test="+self,(audit&&export&&self)?"pass":"fail",true);}catch(Throwable e){add(r,"Diagnostics","Expected vs actual assessment","Final test remains self-explanatory","Diagnostic components readable","Exception: "+safe(e),"fail",true);}}

    private static void add(Report r,String area,String name,String goal,String expected,String actual,String status,boolean critical){String s=status==null?"warn":status.toLowerCase(Locale.ROOT);Case c=new Case(area,name,goal,expected,actual,s,critical);r.cases.add(c);if("pass".equals(s))r.pass++;else if("fail".equals(s))r.fail++;else r.warn++;}
    private static boolean activity(PackageManager pm,Context c,Class<?> cls){try{pm.getActivityInfo(new ComponentName(c,cls),0);return true;}catch(Throwable e){return false;}}
    private static boolean classExists(String n){try{Class.forName(n);return true;}catch(Throwable e){return false;}}
    private static boolean table(SQLiteDatabase s,String n){Cursor c=s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{n});boolean ok=c.moveToFirst();c.close();return ok;}
    private static boolean column(SQLiteDatabase s,String t,String n){Cursor c=s.rawQuery("PRAGMA table_info("+t+")",null);boolean ok=false;while(c.moveToNext())if(n.equals(c.getString(1))){ok=true;break;}c.close();return ok;}
    private static long count(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static String scalar(SQLiteDatabase s,String q){Cursor c=s.rawQuery(q,null);String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}
    private static String safe(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return e.getClass().getSimpleName()+(m==null||m.trim().isEmpty()?"":": "+m.trim());}
}
