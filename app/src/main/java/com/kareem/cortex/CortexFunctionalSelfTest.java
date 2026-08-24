package com.kareem.cortex;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import org.json.*;
import java.util.*;

/**
 * Safe functional wiring test for the intelligence update.
 * It exercises real read/compose/classification/provider paths, but never fabricates personal data
 * and never executes an external mutation. Mutating integrations are verified only up to the
 * approval/draft handoff boundary.
 */
public final class CortexFunctionalSelfTest {
    private CortexFunctionalSelfTest(){}

    public static final class Report {
        public int pass,warn,fail;
        public final ArrayList<String> lines=new ArrayList<>();
        public final JSONObject metrics=new JSONObject();
        public boolean ok(){return fail==0;}
        public String text(){StringBuilder b=new StringBuilder();b.append(ok()?"FUNCTIONAL SELF-TEST PASS":"FUNCTIONAL SELF-TEST FAILED").append("\n").append(pass).append(" pass · ").append(warn).append(" warning · ").append(fail).append(" failure");for(String x:lines)b.append("\n\n").append(x);return b.toString();}
    }

    public static Report run(Context context){Report r=new Report();VaultDb db=null;try{db=new VaultDb(context);CognitiveStore.ensure(db);FeatureStore.ensure(db);VisualInsightStore.ensure(db);ScreenshotLearning.ensure(db);
            database(db,r);brief(db,r);notification(r);privacy(context,r);captureAndVisual(db,r);learning(db,r);everywhere(context,r);actions(context,r);brain(context,r);peopleProjects(db,r);
        }catch(Throwable e){fail(r,"Self-test harness",e.getClass().getSimpleName()+": "+safe(e.getMessage()));}finally{if(db!=null)try{DiagnosticsLog.info(db,"CortexFunctionalSelfTest","complete",r.ok()?"pass":"fail",0,0,0,0,0,0,new JSONObject().put("pass",r.pass).put("warn",r.warn).put("fail",r.fail));}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}return r;}

    private static void database(VaultDb db,Report r){try{Cursor c=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null);String x=c.moveToFirst()?safe(c.getString(0)):"";c.close();check(r,"Vault read/write schema", "ok".equalsIgnoreCase(x),"SQLite quick_check="+x);put(r,"db_quick_check",x);}catch(Throwable e){fail(r,"Vault read/write schema",e.toString());}}

    private static void brief(VaultDb db,Report r){try{String daily=BriefComposer.compose(db,false),weekly=BriefComposer.compose(db,true);boolean ok=daily.startsWith("Daily Cortex Brief")&&weekly.startsWith("Weekly Cortex Brief")&&daily.length()>20&&weekly.length()>20;check(r,"Daily / weekly brief composition",ok,"Real PRIME state composed without injecting synthetic tasks.");put(r,"daily_brief_chars",daily.length());put(r,"weekly_brief_chars",weekly.length());}catch(Throwable e){fail(r,"Daily / weekly brief composition",e.toString());}}

    private static void notification(Report r){try{String a=NotificationEnrichmentEngine.classifyForDiagnostics("Order delivered"),b=NotificationEnrichmentEngine.classifyForDiagnostics("تم التوصيل"),c=NotificationEnrichmentEngine.classifyForDiagnostics("Battery 80%");boolean ok="delivered".equals(a)&&"delivered".equals(b)&&c.isEmpty();check(r,"Notification event intelligence",ok,"Synthetic English/Arabic delivery states classify; unrelated status stays unclassified.");}catch(Throwable e){fail(r,"Notification event intelligence",e.toString());}}

    private static void privacy(Context ctx,Report r){try{KnowledgeItem unknown=new KnowledgeItem(-1,"TEXT","future_unknown_source","Synthetic diagnostic item","","","","","","","analyzed","","","{}",0,0);boolean unknownBlocked=!CloudEvidencePolicy.canSend(ctx,unknown);check(r,"Cloud evidence default-deny",unknownBlocked,"Unknown evidence type is blocked from cloud by policy.");put(r,"unknown_cloud_blocked",unknownBlocked);}catch(Throwable e){fail(r,"Cloud evidence default-deny",e.toString());}}

    private static void captureAndVisual(VaultDb db,Report r){try{PrimeBriefStore.Snapshot s=PrimeBriefStore.load(db);int recent=s.recent.size(),done=VisualInsightStore.countDone(db),failed=VisualInsightStore.countFailed(db),rate=VisualInsightStore.countRateLimited(db);check(r,"Capture → Recent read path",recent>=0,"Recent intentional captures are queryable (current count "+recent+").");if(failed>0||rate>0)warn(r,"Visual Intelligence backlog",done+" done · "+failed+" failed · "+rate+" rate-limited; recovery action remains available in Advanced diagnostics.");else pass(r,"Visual Intelligence store",done+" completed visual insight(s); no current failed/rate-limited backlog.");put(r,"recent_capture_count",recent);put(r,"visual_done",done);put(r,"visual_failed",failed);put(r,"visual_rate_limited",rate);}catch(Throwable e){fail(r,"Capture / Visual Intelligence store",e.toString());}}

    private static void learning(VaultDb db,Report r){try{int taught=ScreenshotLearning.taughtCount(db),prefs=ScreenshotLearning.learnedPreferenceCount(db);long corrections=count(db,"SELECT COUNT(*) FROM correction_rules"),feedback=count(db,"SELECT COUNT(*) FROM feedback_events");pass(r,"Learning persistence","Screenshot teaching="+taught+" · learned preferences="+prefs+" · corrections="+corrections+" · feedback events="+feedback+". Zero is valid until the user teaches/corrects Cortex.");put(r,"taught_screenshots",taught);put(r,"learned_preferences",prefs);put(r,"corrections",corrections);put(r,"feedback_events",feedback);}catch(Throwable e){fail(r,"Learning persistence",e.toString());}}

    private static void everywhere(Context ctx,Report r){try{PackageManager pm=ctx.getPackageManager();boolean capture=activity(pm,ctx,CaptureActivity.class),result=activity(pm,ctx,CaptureResultActivity.class),voice=service(pm,ctx,CortexQuickTileService.class),screen=service(pm,ctx,UnderstandScreenTileService.class),access=service(pm,ctx,CortexScreenAccessibilityService.class);boolean ok=capture&&result&&voice&&screen&&access;check(r,"Everywhere Cortex components",ok,"Capture="+capture+" · Result="+result+" · Voice tile="+voice+" · Understand-screen tile="+screen+" · Accessibility sensor="+access);put(r,"everywhere_ready",ok);}catch(Throwable e){fail(r,"Everywhere Cortex components",e.toString());}}

    private static void actions(Context ctx,Report r){try{PackageManager pm=ctx.getPackageManager();boolean calendar=pm.resolveActivity(new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI),PackageManager.MATCH_DEFAULT_ONLY)!=null;boolean email=pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:test@example.com")),PackageManager.MATCH_DEFAULT_ONLY)!=null;boolean sms=pm.resolveActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:01000000000")),PackageManager.MATCH_DEFAULT_ONLY)!=null;int handlers=(calendar?1:0)+(email?1:0)+(sms?1:0);if(handlers>0)pass(r,"Approval-first action handoff","Device handlers available: calendar="+calendar+" · email="+email+" · message="+sms+". Self-test does not launch or send anything.");else warn(r,"Approval-first action handoff","No Calendar/email/SMS handler resolved on this device profile; Cortex will fail safely instead of claiming execution.");put(r,"calendar_handler",calendar);put(r,"email_handler",email);put(r,"sms_handler",sms);}catch(Throwable e){fail(r,"Approval-first action handoff",e.toString());}}

    private static void brain(Context ctx,Report r){try{boolean gemini=GeminiKeyStore.has(ctx),local=LocalModelManager.installed(ctx);put(r,"gemini_configured",gemini);put(r,"local_model_installed",local);if(gemini){ExternalBrainProvider.HealthReport h=ExternalBrainProvider.healthCheck(ctx);put(r,"external_model",h.model);put(r,"external_http",h.httpCode);put(r,"external_latency_ms",h.latencyMs);if(h.ok)pass(r,"Combined Brain external route","Real non-private request passed · "+h.model+" · HTTP "+h.httpCode+" · "+h.latencyMs+" ms.");else fail(r,"Combined Brain external route",h.status+(h.error.isEmpty()?"":" · "+clip(h.error,180)));}else warn(r,"Combined Brain external route","Gemini is not configured; external request was not faked.");if(local)pass(r,"Combined Brain local fallback","Local model is installed and available as the fallback path.");else warn(r,"Combined Brain local fallback","Local model is not installed; Combined cannot provide a local-model fallback until it is installed.");}catch(Throwable e){fail(r,"Combined Brain routing",e.toString());}}

    private static void peopleProjects(VaultDb db,Report r){try{long people=count(db,"SELECT COUNT(*) FROM entity_nodes WHERE status='active' AND upper(kind)='PERSON'"),projects=count(db,"SELECT COUNT(*) FROM entity_nodes WHERE status='active' AND upper(kind)='PROJECT'"),links=count(db,"SELECT COUNT(*) FROM source_links WHERE to_type='entity' AND from_type='memory'");pass(r,"People / Projects scoped evidence","Entity graph readable · people="+people+" · projects="+projects+" · memory→entity links="+links+". Brain handoff uses the latest linked evidence when available.");put(r,"people",people);put(r,"projects",projects);put(r,"entity_memory_links",links);}catch(Throwable e){fail(r,"People / Projects scoped evidence",e.toString());}}

    private static boolean activity(PackageManager pm,Context c,Class<?> cls){try{pm.getActivityInfo(new ComponentName(c,cls),0);return true;}catch(Throwable e){return false;}}private static boolean service(PackageManager pm,Context c,Class<?> cls){try{pm.getServiceInfo(new ComponentName(c,cls),0);return true;}catch(Throwable e){return false;}}
    private static long count(VaultDb db,String sql){Cursor c=db.getReadableDatabase().rawQuery(sql,null);long n=c.moveToFirst()?c.getLong(0):0;c.close();return n;}
    private static void check(Report r,String name,boolean ok,String detail){if(ok)pass(r,name,detail);else fail(r,name,detail);}private static void pass(Report r,String n,String d){r.pass++;r.lines.add("PASS · "+n+"\n"+d);}private static void warn(Report r,String n,String d){r.warn++;r.lines.add("WARN · "+n+"\n"+d);}private static void fail(Report r,String n,String d){r.fail++;r.lines.add("FAIL · "+n+"\n"+d);}private static void put(Report r,String k,Object v){try{r.metrics.put(k,v);}catch(Exception ignored){}}private static String clip(String s,int n){String x=safe(s);return x.length()<=n?x:x.substring(0,n)+"…";}private static String safe(String s){return s==null?"":s;}
}
