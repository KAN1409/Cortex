package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Installed-device end-to-end verification surface. */
public class CortexEndToEndActivity extends Activity {
    LinearLayout body,events;TextView headline,sub;Button run,max,extreme,share,arm;volatile boolean running;
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refreshLatest();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.plain(this,"Cortex Test Lab",28,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);TextView hs=CortexUi.text(this,"Real end-to-end verification inside the installed app on this phone.",11,CortexUi.MUTED);titles.addView(hs);body.addView(head);

        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(16),dp(16),dp(16),dp(16));headline=CortexUi.plain(this,"Ready",20,CortexUi.TEXT);CortexUi.medium(headline);card.addView(headline);sub=CortexUi.text(this,"Runs production code paths, creates isolated fixtures, and produces a structured report for ChatGPT.",12,CortexUi.MUTED);sub.setPadding(0,dp(6),0,0);card.addView(sub);body.addView(card);

        run=new Button(this);run.setText("RUN FULL END-TO-END TEST");run.setAllCaps(false);run.setOnClickListener(v->runProfile(0));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.setMargins(0,dp(16),0,0);body.addView(run,rp);
        max=new Button(this);max.setText("RUN MAXIMUM-INTENSITY TEST");max.setAllCaps(false);max.setOnClickListener(v->runProfile(1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(54));mp.setMargins(0,dp(10),0,0);body.addView(max,mp);
        extreme=new Button(this);extreme.setText("RUN EXTREME-ENDURANCE TEST");extreme.setAllCaps(false);extreme.setOnClickListener(v->runProfile(2));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(56));ep.setMargins(0,dp(10),0,0);body.addView(extreme,ep);
        share=new Button(this);share.setText("ANALYZE WITH CHATGPT");share.setAllCaps(false);share.setEnabled(false);share.setOnClickListener(v->shareLatest());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));sp.setMargins(0,dp(10),0,0);body.addView(share,sp);
        arm=new Button(this);arm.setText("ARM REAL UPDATE-SURVIVAL TEST");arm.setAllCaps(false);arm.setOnClickListener(v->armUpdate());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.setMargins(0,dp(10),0,0);body.addView(arm,ap);

        TextView intense=CortexUi.text(this,"Maximum intensity is the shorter production stress profile. Extreme Endurance adds 100 functional self-tests with per-subtest evidence, 1,000 DB quick_checks, 1,000 document extractions, 4,000 concurrent DB reads, up to ~2 GiB fsync+SHA storage torture, attachment forensics, 250 FileProvider/report round-trips, GC pressure and a 120-minute memory soak. Extreme is intentionally expensive and may use the configured external Brain repeatedly.",11,CortexUi.MUTED);intense.setPadding(0,dp(10),0,dp(4));body.addView(intense);
        TextView honest=CortexUi.text(this,"Cold process-death/relaunch and true background↔foreground cycling are never simulated or claimed by the in-process runner. The exported report now also carries Android process-exit evidence, Java crash evidence, live semantic-pipeline readiness, and exact failed capability names.",11,CortexUi.AMBER);honest.setPadding(0,dp(6),0,dp(4));body.addView(honest);
        TextView note=CortexUi.text(this,"Update test: arm this on the current version, install a strictly newer APK normally without uninstalling, then run the test again. Cortex requires both SharedPreferences and an independent private checkpoint file to survive before it reports update survival PASS.",11,CortexUi.MUTED);note.setPadding(0,dp(8),0,dp(8));body.addView(note);
        body.addView(CortexUi.section(this,"Live test stages"));events=new LinearLayout(this);events.setOrientation(LinearLayout.VERTICAL);body.addView(events);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void runProfile(int mode){
        if(running)return;running=true;run.setEnabled(false);max.setEnabled(false);extreme.setEnabled(false);share.setEnabled(false);events.removeAllViews();
        String label=mode==2?"Extreme Endurance":mode==1?"Maximum intensity":"Full E2E";
        headline.setText(label+" running…");
        sub.setText(mode==2?"Keep Cortex open. This profile is deliberately exhaustive and includes a 120-minute soak.":mode==1?"Keep Cortex open. This profile intentionally repeats production paths and may take several minutes.":"Do not close Cortex until the report is finished.");
        new Thread(()->{
            CortexEndToEndLab.Listener listener=(id,status,detail)->runOnUiThread(()->addStage(id,status,detail));
            CortexEndToEndLab.RunResult result=mode==2?CortexExtremeEnduranceLab.run(getApplicationContext(),listener):mode==1?CortexMaximumIntensityLab.run(getApplicationContext(),listener):CortexEndToEndLab.run(getApplicationContext(),listener);
            int[] counts=augmentReport(result.reportFile,result.pass,result.warn,result.fail);
            runOnUiThread(()->{
                running=false;run.setEnabled(true);max.setEnabled(true);extreme.setEnabled(true);share.setEnabled(result.reportFile.isFile());
                String verdict=counts[2]>0?"FAIL":(counts[1]>0?"PASS WITH WARNINGS":"PASS");
                headline.setText((mode==2?"EXTREME · ":mode==1?"MAXIMUM · ":"")+verdict);
                sub.setText(counts[0]+" passed · "+counts[1]+" warnings · "+counts[2]+" failed · report "+result.runId);
                Toast.makeText(this,label+" report ready",Toast.LENGTH_LONG).show();
            });
        },mode==2?"cortex-extreme-endurance":mode==1?"cortex-maximum-e2e":"cortex-embedded-e2e").start();
    }

    /** Adds acceptance evidence that the base harness previously only exposed indirectly. */
    int[] augmentReport(File file,int basePass,int baseWarn,int baseFail){
        int pass=basePass,warn=baseWarn,fail=baseFail;VaultDb vault=null;
        try{
            JSONObject report=new JSONObject(readFile(file));JSONArray tests=report.optJSONArray("tests");if(tests==null){tests=new JSONArray();report.put("tests",tests);}
            JSONObject runtime=new JSONObject();
            ProcessExitRecorder.captureHistoricalExit(getApplicationContext());
            String javaCrash=CrashRecorder.read(getApplicationContext(),60000),processExit=ProcessExitRecorder.read(getApplicationContext(),120000);
            runtime.put("lastJavaCrash",javaCrash);runtime.put("lastProcessExit",processExit);

            vault=new VaultDb(getApplicationContext());
            long semanticWaiting=scalar(vault,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='waiting' AND superseded_by=0");
            long semanticBlocked=scalar(vault,"SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='blocked' AND superseded_by=0");
            boolean localReady=LocalModelManager.installed(getApplicationContext());LocalModelManager.Status localStatus=LocalModelManager.status(getApplicationContext());
            runtime.put("semanticWaiting",semanticWaiting);runtime.put("semanticBlocked",semanticBlocked);runtime.put("semanticCapabilityReady",localReady);runtime.put("localModelState",localStatus.state);runtime.put("localModelDetail",localStatus.detail);
            if(semanticWaiting>0&&!localReady){tests.put(test("semantic_pipeline_readiness","FAIL",semanticWaiting+" semantic event(s) are waiting but the private background model is unavailable. Local model state="+localStatus.state));fail++;}
            else if(!localReady){tests.put(test("semantic_pipeline_readiness","WARN","No semantic backlog is waiting now, but the private background model is unavailable. Local model state="+localStatus.state));warn++;}
            else{tests.put(test("semantic_pipeline_readiness","PASS","Private background semantic model is ready; waiting="+semanticWaiting+" · blocked="+semanticBlocked));pass++;}

            JSONArray capabilityStates=new JSONArray(),failedCaps=new JSONArray();int failedCount=0;
            for(CortexCapabilityRegistry.Capability c:CortexCapabilityRegistry.all()){
                CortexCapabilityRegistry.State s=CortexCapabilityRegistry.evaluate(getApplicationContext(),vault,c);
                if(CortexCapabilityRegistry.FAILED.equals(s.status)){failedCount++;JSONObject x=new JSONObject();x.put("number",c.number);x.put("key",c.key);x.put("title",c.title);x.put("status",s.status);x.put("detail",s.detail);failedCaps.put(x);capabilityStates.put(x);}
                else if(!CortexCapabilityRegistry.ACTIVE.equals(s.status)&&!CortexCapabilityRegistry.READY.equals(s.status)){JSONObject x=new JSONObject();x.put("number",c.number);x.put("key",c.key);x.put("title",c.title);x.put("status",s.status);x.put("detail",s.detail);capabilityStates.put(x);}
            }
            runtime.put("nonGreenCapabilities",capabilityStates);runtime.put("failedCapabilities",failedCaps);runtime.put("failedCapabilityCount",failedCount);
            if(failedCount>0){tests.put(test("capability_runtime_failures","FAIL",failedCount+" authoritative capability failure(s); exact names and reasons are in runtimeDiagnostics.failedCapabilities"));fail++;}
            else{tests.put(test("capability_runtime_failures","PASS","No authoritative capability is currently in FAILED state"));pass++;}

            boolean javaFailure=!javaCrash.trim().isEmpty();boolean badExit=isFailureExit(processExit);runtime.put("processFailureEvidencePresent",javaFailure||badExit);
            if(javaFailure||badExit){tests.put(test("process_failure_evidence","WARN","Stored failure evidence exists from a prior process: javaCrash="+javaFailure+" · AndroidFailureExit="+badExit+". Inspect runtimeDiagnostics before claiming stability."));warn++;}
            else{tests.put(test("process_failure_evidence","PASS","No stored Java crash or Android crash/ANR/resource-failure exit is currently recorded"));pass++;}

            report.put("runtimeDiagnostics",runtime);JSONObject summary=new JSONObject();summary.put("pass",pass);summary.put("warn",warn);summary.put("fail",fail);summary.put("ok",fail==0);report.put("summary",summary);writeFile(file,report.toString(2));
        }catch(Throwable e){
            try{JSONObject report=new JSONObject(readFile(file));JSONObject d=report.optJSONObject("runtimeDiagnostics");if(d==null){d=new JSONObject();report.put("runtimeDiagnostics",d);}d.put("augmentationError",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));writeFile(file,report.toString(2));}catch(Throwable ignored){}
        }finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
        return new int[]{pass,warn,fail};
    }

    JSONObject test(String id,String status,String detail)throws JSONException{return new JSONObject().put("id",id).put("status",status).put("detail",detail);}
    long scalar(VaultDb db,String sql){Cursor c=null;try{c=db.getReadableDatabase().rawQuery(sql,null);return c.moveToFirst()?c.getLong(0):0;}finally{if(c!=null)c.close();}}
    boolean isFailureExit(String text){if(text==null)return false;return text.contains("reason=CRASH\n")||text.contains("reason=CRASH_NATIVE\n")||text.contains("reason=ANR\n")||text.contains("reason=INITIALIZATION_FAILURE\n")||text.contains("reason=EXCESSIVE_RESOURCE_USAGE\n")||text.contains("reason=LOW_MEMORY\n");}
    String readFile(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    void writeFile(File f,String text)throws IOException{File tmp=new File(f.getParentFile(),f.getName()+".part");try(OutputStream out=new FileOutputStream(tmp,false)){out.write(text.getBytes(StandardCharsets.UTF_8));out.flush();}if(f.exists()&&!f.delete())throw new IOException("Could not replace report");if(!tmp.renameTo(f))throw new IOException("Could not finalize report");}

    void addStage(String id,String status,String detail){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(2),dp(10),dp(2),dp(10));TextView t=CortexUi.plain(this,status+"  "+id,13,"PASS".equals(status)?CortexUi.LIME:("WARN".equals(status)?CortexUi.AMBER:0xffff6666));CortexUi.medium(t);c.addView(t);TextView d=CortexUi.text(this,detail,11,CortexUi.MUTED);d.setPadding(0,dp(3),0,0);c.addView(d);events.addView(c);events.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
    }

    void refreshLatest(){File f=CortexEndToEndLab.latestReport(this);share.setEnabled(f!=null&&f.isFile());if(f!=null){sub.setText("Latest report ready: "+f.getParentFile().getName());}}

    void armUpdate(){
        try{
            String token=CortexEndToEndLab.armUpdateCheckpoint(this);
            Toast.makeText(this,"Update checkpoint armed",Toast.LENGTH_LONG).show();
            sub.setText("Checkpoint armed: "+token.substring(0,Math.min(token.length(),24))+"… Install a newer APK as an update, then run the E2E test again.");
        }catch(Throwable e){
            Toast.makeText(this,"Could not arm update test: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            sub.setText("Update checkpoint was not armed. No survival claim will be made.");
        }
    }

    void shareLatest(){
        File f=CortexEndToEndLab.latestReport(this);if(f==null||!f.isFile()){Toast.makeText(this,"Run the end-to-end test first",Toast.LENGTH_LONG).show();return;}
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".feedback.files",f);
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_STREAM,uri);send.putExtra(Intent.EXTRA_SUBJECT,"Cortex embedded end-to-end report");send.putExtra(Intent.EXTRA_TEXT,"Analyze this Cortex end-to-end report. Identify root causes, failing subsystems, severity, exact next fixes, and any repeated/stress-test instability. Treat PASS/WARN/FAIL, runtimeDiagnostics, and evidence fields as authoritative test output; do not invent missing evidence.");send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(installed("com.openai.chatgpt")){send.setPackage("com.openai.chatgpt");try{startActivity(send);return;}catch(Throwable ignored){send.setPackage(null);}}
            startActivity(Intent.createChooser(send,"Analyze Cortex report with ChatGPT"));
        }catch(Throwable e){Toast.makeText(this,"Could not share report: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}
    }

    boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Throwable e){return false;}}
}
