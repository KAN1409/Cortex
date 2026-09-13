package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;

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
        TextView honest=CortexUi.text(this,"Cold process-death/relaunch and true background↔foreground cycling are never simulated or claimed by the in-process runner. Until a durable relaunch orchestrator is installed, Extreme records those requested lifecycle cycles as unexecuted WARN evidence.",11,CortexUi.AMBER);honest.setPadding(0,dp(6),0,dp(4));body.addView(honest);
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
            runOnUiThread(()->{
                running=false;run.setEnabled(true);max.setEnabled(true);extreme.setEnabled(true);share.setEnabled(result.reportFile.isFile());
                String verdict=result.fail>0?"FAIL":(result.warn>0?"PASS WITH WARNINGS":"PASS");
                headline.setText((mode==2?"EXTREME · ":mode==1?"MAXIMUM · ":"")+verdict);
                sub.setText(result.pass+" passed · "+result.warn+" warnings · "+result.fail+" failed · report "+result.runId);
                Toast.makeText(this,label+" report ready",Toast.LENGTH_LONG).show();
            });
        },mode==2?"cortex-extreme-endurance":mode==1?"cortex-maximum-e2e":"cortex-embedded-e2e").start();
    }

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
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_STREAM,uri);send.putExtra(Intent.EXTRA_SUBJECT,"Cortex embedded end-to-end report");send.putExtra(Intent.EXTRA_TEXT,"Analyze this Cortex end-to-end report. Identify root causes, failing subsystems, severity, exact next fixes, and any repeated/stress-test instability. Treat PASS/WARN/FAIL and evidence fields as authoritative test output; do not invent missing evidence.");send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(installed("com.openai.chatgpt")){send.setPackage("com.openai.chatgpt");try{startActivity(send);return;}catch(Throwable ignored){send.setPackage(null);}}
            startActivity(Intent.createChooser(send,"Analyze Cortex report with ChatGPT"));
        }catch(Throwable e){Toast.makeText(this,"Could not share report: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}
    }

    boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Throwable e){return false;}}
}
