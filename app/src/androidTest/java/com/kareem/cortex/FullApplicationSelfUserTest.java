package com.kareem.cortex;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class FullApplicationSelfUserTest {
    private static final String PKG="com.kareem.cortex";
    private Instrumentation inst; private UiDevice device; private Context target; private File out;
    private final JSONArray events=new JSONArray(); private final ArrayList<String> findings=new ArrayList<>();
    private int pass=0,warn=0,fail=0,step=0;

    private static final Class<?>[] SURFACES=new Class<?>[]{
            CompactTodayActivity.class, ProposalPeopleProjectsActivity.class, ProposalCaptureActivity.class,
            ProposalAskCortexActivity.class, SettingsActivity.class, VaultActivity.class, PromptLibraryActivity.class,
            SmartInboxActivity.class, ReviewQueueActivity.class, AttentionEvaluationActivity.class,
            RelevanceEvaluationActivity.class, CorrectionLearningActivity.class, FeatureHubActivity.class,
            PhoneContextAccessActivity.class, CapabilityMatrixActivity.class, EnvironmentActivity.class,
            CortexStatusActivity.class, CortexAuditActivity.class, ExternalModelCheckActivity.class,
            VisualIntelligenceActivity.class, OcrTestActivity.class, AsrSettingsActivity.class,
            OpenRouterSettingsActivity.class, GeminiSettingsActivity.class
    };

    @Test public void fullApplicationSelfUserTest() throws Exception {
        inst=InstrumentationRegistry.getInstrumentation(); device=UiDevice.getInstance(inst); target=inst.getTargetContext();
        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        out=new File(target.getExternalFilesDir(null),"self-user-test/FullCortexSelfUserTest_"+stamp);
        if(!out.mkdirs()&&!out.isDirectory()) throw new IllegalStateException("Cannot create report directory");
        try{device.executeShellCommand("logcat -c");}catch(Throwable ignored){}
        record("RUN","START","PASS","Full instrumented Cortex self-user test started",null); pass++;
        launcherAndNavigation(); surfaceCoverage(); safePersistenceRoundTrip(); todayBehaviorInspection(); cortexAskSmoke(); legacyEscapeChecks(); finalDiagnostics(); writeReports();
    }

    private void launcherAndNavigation(){
        try{
            launch(CompactTodayActivity.class); checkpoint("launcher_today"); expectText("TODAY","Launcher resolves to new Today surface");
            String[] tabs={"Memory","Capture","Cortex","Today"};
            for(String tab:tabs){
                launch(CompactTodayActivity.class); // reset to a known shell before every tab; Capture is a sheet.
                UiObject2 x=device.wait(Until.findObject(By.text(tab)),2500);
                if(x==null){finding("FAIL","NAV","Bottom tab missing: "+tab);continue;}
                x.click(); SystemClock.sleep(700); checkpoint("nav_"+safe(tab));
                if(!PKG.equals(device.getCurrentPackageName())) finding("FAIL","NAV",tab+" escaped Cortex to "+device.getCurrentPackageName());
                else finding("PASS","NAV",tab+" remained inside Cortex");
                if("Capture".equals(tab)) device.pressBack();
            }
        }catch(Throwable t){finding("FAIL","NAV","Navigation test crashed: "+brief(t));}
    }

    private void surfaceCoverage(){
        for(Class<?> cls:SURFACES){String name=cls.getSimpleName();try{
            launch(cls); checkpoint("surface_"+safe(name));
            if(PKG.equals(device.getCurrentPackageName())) finding("PASS","SURFACE",name+" opened"); else finding("FAIL","SURFACE",name+" left package: "+device.getCurrentPackageName());
            screenSanity(name); safeScroll(); checkpoint("surface_"+safe(name)+"_scrolled");
        }catch(Throwable t){finding("FAIL","SURFACE",name+" could not be exercised: "+brief(t));}}
    }

    private void safePersistenceRoundTrip(){VaultDb db=null;SQLiteDatabase s=null;try{
        db=new VaultDb(target); CognitiveStore.ensure(db); s=db.getWritableDatabase(); s.beginTransaction();
        android.content.ContentValues v=new android.content.ContentValues(); long now=System.currentTimeMillis(); String marker="SELF_USER_TEST_"+now;
        v.put("kind","ACTION");v.put("title",marker);v.put("body","Synthetic rolled-back instrumentation item");v.put("source_key","instrumented_self_user_test");v.put("state","open");v.put("confidence",1.0);v.put("importance",60);v.put("thread_id",0);v.put("anchor_signal_id",0);v.put("created_at",now);v.put("updated_at",now);
        long id=s.insert("derived_items",null,v); android.database.Cursor c=s.rawQuery("SELECT title FROM derived_items WHERE id=?",new String[]{String.valueOf(id)}); boolean ok=c.moveToFirst()&&marker.equals(c.getString(0)); c.close();
        finding(ok?"PASS":"FAIL","PERSISTENCE",ok?"Synthetic derived item round-tripped inside rollback transaction":"Synthetic derived item did not round-trip");
    }catch(Throwable t){finding("WARN","PERSISTENCE","Rolled-back persistence probe unavailable: "+brief(t));}finally{if(s!=null&&s.inTransaction())try{s.endTransaction();}catch(Throwable ignored){}if(db!=null)try{db.close();}catch(Throwable ignored){}}}

    private void todayBehaviorInspection(){try{
        launch(CompactTodayActivity.class); checkpoint("today_behavior");
        if(device.hasObject(By.text("CURRENT SIGNAL"))) finding("FAIL","TODAY","Legacy CURRENT SIGNAL hero returned"); else finding("PASS","TODAY","No legacy Current Signal hero");
        if(device.hasObject(By.text("DONE"))) finding("PASS","TODAY","Action controls available"); else finding("WARN","TODAY","No DONE control visible in current data state");
        UiObject2 why=device.findObject(By.text("WHY")); if(why!=null){why.click();SystemClock.sleep(400);checkpoint("today_why_dialog");device.pressBack();finding("PASS","TODAY","WHY interaction opened safely");}
    }catch(Throwable t){finding("FAIL","TODAY","Today interaction probe failed: "+brief(t));}}

    private void cortexAskSmoke(){try{
        launch(ProposalAskCortexActivity.class); checkpoint("cortex_before_ask"); UiObject2 local=device.findObject(By.text("Your data")); if(local!=null)local.click();
        List<UiObject2> edits=device.findObjects(By.clazz(EditText.class)); if(edits.isEmpty()){finding("FAIL","CORTEX","Ask input not found");return;}
        edits.get(edits.size()-1).setText("What needs my attention? Answer in one short sentence."); UiObject2 ask=device.findObject(By.text("Ask")); if(ask==null){finding("FAIL","CORTEX","Ask button not found");return;} ask.click();
        SystemClock.sleep(5000); checkpoint("cortex_after_ask"); if(device.hasObject(By.text("null")))finding("FAIL","CORTEX","Literal null rendered as an answer");else finding("PASS","CORTEX","No literal null answer rendered");
        if(device.hasObject(By.textContains("CORTEX_STRUCTURED_RESPONSE_V1"))) finding("FAIL","CORTEX","Internal structured prompt leaked into UI");
    }catch(Throwable t){finding("WARN","CORTEX","Ask smoke could not complete: "+brief(t));}}

    private void legacyEscapeChecks(){try{
        launch(BrainActivity.class);SystemClock.sleep(350);checkpoint("legacy_brain_redirect"); if(device.hasObject(By.text("CORTEX"))||device.hasObject(By.text("Combined")))finding("PASS","LEGACY","Legacy Brain entry redirects into modern Cortex");else finding("WARN","LEGACY","Legacy Brain redirect could not be confirmed visually");
    }catch(Throwable t){finding("WARN","LEGACY","Legacy Brain redirect probe failed: "+brief(t));}
        try{launch(ProposalBriefActivity.class);SystemClock.sleep(350);checkpoint("legacy_proposal_brief"); if(device.hasObject(By.text("CURRENT SIGNAL")))finding("FAIL","LEGACY","Old proposal Today surface is still independently reachable");else if(device.hasObject(By.text("TODAY")))finding("PASS","LEGACY","Legacy Proposal Today redirects to Compact Today");else finding("WARN","LEGACY","Legacy Proposal redirect could not be confirmed visually");}catch(Throwable t){finding("WARN","LEGACY","Legacy proposal surface probe: "+brief(t));}}

    private void launch(Class<?> cls){Intent i=new Intent(target,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP); target.startActivity(i); device.wait(Until.hasObject(By.pkg(PKG)),5000); SystemClock.sleep(900);}
    private void safeScroll(){try{int w=device.getDisplayWidth(),h=device.getDisplayHeight();device.swipe(w/2,(int)(h*.78f),w/2,(int)(h*.30f),18);SystemClock.sleep(250);device.swipe(w/2,(int)(h*.30f),w/2,(int)(h*.72f),18);SystemClock.sleep(200);}catch(Throwable ignored){}}
    private void screenSanity(String name){try{if(device.hasObject(By.text("null")))finding("FAIL","RENDER",name+" renders literal null");if(device.hasObject(By.textContains("Exception"))||device.hasObject(By.textContains("has stopped")))finding("FAIL","RENDER",name+" appears to show an exception/crash surface");if(PKG.equals(device.getCurrentPackageName()))finding("PASS","RENDER",name+" retained Cortex package after idle");}catch(Throwable ignored){}}
    private void expectText(String text,String message){if(device.wait(Until.hasObject(By.text(text)),2500))finding("PASS","ASSERT",message);else finding("FAIL","ASSERT",message+" — missing text: "+text);}

    private void finalDiagnostics(){try{write(new File(out,"logcat.txt"),device.executeShellCommand("logcat -d -t 1600"));}catch(Throwable t){finding("WARN","DIAGNOSTICS","Could not capture logcat: "+brief(t));}try{write(new File(out,"activity_stack.txt"),device.executeShellCommand("dumpsys activity activities | head -n 220"));}catch(Throwable ignored){}try{write(new File(out,"package.txt"),device.executeShellCommand("dumpsys package "+PKG+" | head -n 260"));}catch(Throwable ignored){}}
    private void checkpoint(String label){step++;String base=String.format(Locale.US,"%03d_%s",step,safe(label));try{device.takeScreenshot(new File(out,base+".png"));}catch(Throwable t){finding("WARN","CAPTURE","Screenshot failed at "+label+": "+brief(t));}try{device.dumpWindowHierarchy(new File(out,base+".xml"));}catch(Throwable t){finding("WARN","CAPTURE","Hierarchy failed at "+label+": "+brief(t));}try{events.put(new JSONObject().put("step",step).put("label",label).put("package",String.valueOf(device.getCurrentPackageName())).put("time_ms",System.currentTimeMillis()));}catch(Throwable ignored){}}
    private void finding(String severity,String area,String message){if("PASS".equals(severity))pass++;else if("FAIL".equals(severity))fail++;else warn++;findings.add(severity+" · "+area+" · "+message);record("FINDING",area,severity,message,null);}
    private void record(String type,String area,String severity,String message,JSONObject extra){try{JSONObject o=new JSONObject().put("type",type).put("area",area).put("severity",severity).put("message",message).put("time_ms",System.currentTimeMillis());if(extra!=null)o.put("extra",extra);events.put(o);}catch(Throwable ignored){}}
    private void writeReports() throws Exception {JSONObject root=new JSONObject().put("schema","CORTEX_INSTRUMENTED_SELF_USER_TEST_V2").put("package",PKG).put("generated_at",System.currentTimeMillis()).put("pass",pass).put("warning",warn).put("failure",fail).put("events",events);write(new File(out,"report.json"),root.toString(2));StringBuilder md=new StringBuilder("# Cortex Instrumented Self-User Test\n\n**Result:** ").append(pass).append(" pass · ").append(warn).append(" warning · ").append(fail).append(" failure\n\n## Findings\n\n");for(String f:findings)md.append("- ").append(f).append("\n");md.append("\n## Evidence\n\nEach numbered checkpoint has PNG + XML evidence; logcat/activity/package diagnostics are included.\n");write(new File(out,"report.md"),md.toString());write(new File(out,"REPORT_DIRECTORY.txt"),out.getAbsolutePath()+"\n");}
    private static void write(File f,String s)throws Exception{File p=f.getParentFile();if(p!=null&&!p.exists())p.mkdirs();try(FileWriter w=new FileWriter(f)){w.write(s==null?"":s);}}
    private static String safe(String s){return s==null?"step":s.toLowerCase(Locale.US).replaceAll("[^a-z0-9_-]+","_");}
    private static String brief(Throwable t){String m=t==null?"":String.valueOf(t.getMessage());if(m.length()>180)m=m.substring(0,180);return t==null?"unknown":t.getClass().getSimpleName()+(m.isEmpty()?"":": "+m);}
}
