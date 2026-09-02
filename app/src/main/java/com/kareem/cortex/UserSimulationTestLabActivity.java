package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;

/** In-app launcher for quick repair regressions or the strict long-form exhaustive simulation. */
public class UserSimulationTestLabActivity extends Activity {
    TextView status,detail,summary;ProgressBar progress;Button run,repair,share,unblock,live;volatile boolean running=false,destroyed=false;File latest;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);build();if(getIntent()!=null&&getIntent().getBooleanExtra("repair_only",false))repair.postDelayed(this::startRepairRun,300);else if(getIntent()!=null&&getIntent().getBooleanExtra("autorun",false))run.postDelayed(this::startRun,350);}
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout hs=new LinearLayout(this);hs.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"Cortex Test Lab",27,CortexUi.TEXT);CortexUi.medium(title);hs.addView(title);hs.addView(CortexUi.text(this,"Fast repair regressions for daily development. Full exhaustive simulation is the release gate.",11,CortexUi.MUTED));head.addView(hs,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        TextView intro=CortexUi.text(this,"Repair mode runs only permanent product regressions for bugs we already found. It skips the 90-second stability window and protected/live tests. Full mode keeps the strict exhaustive matrix for release validation.",12,CortexUi.MUTED);intro.setPadding(dp(2),dp(10),dp(2),dp(18));body.addView(intro);
        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(16),dp(15),dp(16),dp(15));TextView eyebrow=CortexUi.plain(this,"REPAIR FAST · EXHAUSTIVE WHEN NEEDED",9,CortexUi.LIME);CortexUi.medium(eyebrow);card.addView(eyebrow);status=CortexUi.plain(this,"Ready",21,CortexUi.TEXT);CortexUi.medium(status);status.setPadding(0,dp(6),0,0);card.addView(status);detail=CortexUi.text(this,"Repair: action polarity · Brain scope · ASR sanity · provider policy · queue priority. Full: all product/runtime/audit checks.",11,CortexUi.MUTED);detail.setPadding(0,dp(6),0,dp(12));card.addView(detail);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(0);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));body.addView(card);

        repair=button("Run repair regressions",true);repair.setOnClickListener(v->startRepairRun());LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(54));qp.setMargins(0,dp(14),0,dp(8));body.addView(repair,qp);
        run=button("Run full exhaustive simulation",false);run.setOnClickListener(v->startRun());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(50));rp.setMargins(0,0,0,dp(8));body.addView(run,rp);
        unblock=button("Unblock remaining tests",false);unblock.setOnClickListener(v->open(CortexTestUnblockWizardActivity.class));body.addView(unblock,new LinearLayout.LayoutParams(-1,dp(50)));
        live=button("Protected live tests",false);live.setOnClickListener(v->open(CortexProtectedLiveTestsActivity.class));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(8),0,0);body.addView(live,lp);
        share=button("Share latest JSON",false);share.setEnabled(false);share.setOnClickListener(v->shareLatest());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));sp.setMargins(0,dp(8),0,0);body.addView(share,sp);

        summary=CortexUi.text(this,"Use Repair regressions after a targeted code change. Use Full exhaustive only when we need broad validation or a release gate. Protected live tests remain explicit because they touch microphone, Calendar, screenshot import or external Android surfaces.",10,CortexUi.FAINT);summary.setTextIsSelectable(true);summary.setPadding(dp(2),dp(18),dp(2),0);body.addView(summary);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?CortexUi.BG:CortexUi.TEXT);b.setBackground(CortexUi.round(this,primary?CortexUi.LIME:CortexUi.SURFACE_2,primary?android.graphics.Color.TRANSPARENT:CortexUi.BORDER,18));return b;}

    void setBusy(boolean busy){running=busy;run.setEnabled(!busy);repair.setEnabled(!busy);share.setEnabled(!busy&&latest!=null&&latest.exists());unblock.setEnabled(!busy);live.setEnabled(!busy);}

    void startRepairRun(){if(running)return;latest=null;setBusy(true);repair.setText("Running repair regressions…");status.setText("Repair regressions");detail.setText("Running only permanent product regressions");progress.setProgress(120);
        new Thread(()->{try{CortexRepairRegressionRunner.Result result=CortexRepairRegressionRunner.run(getApplicationContext());latest=result.file;ui(()->{setBusy(false);repair.setText("Run repair regressions again");share.setEnabled(true);progress.setProgress(1000);String overall=result.summary.optString("overall","Complete");status.setText(overall);detail.setText(result.summary.optInt("test_count",0)+" repair checks · "+result.summary.optInt("pass",0)+" pass · "+result.summary.optInt("fail",0)+" fail · "+result.summary.optLong("duration_ms",0)+" ms");summary.setText(result.summary.toString());if(getIntent()!=null&&getIntent().getBooleanExtra("auto_share",false))shareLatest();});}catch(Throwable e){ui(()->{setBusy(false);repair.setText("Try repair regressions again");status.setText("Repair regression failed");detail.setText(e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage()));summary.setText(android.util.Log.getStackTraceString(e));});}},"CortexRepairRegressions").start();}

    void startRun(){if(running)return;latest=null;setBusy(true);run.setText("Running full exhaustive…");status.setText("Starting");detail.setText("Preparing strict user simulation");progress.setProgress(5);
        new Thread(()->{try{
            CortexUserSimulationRunner.Result base=CortexUserSimulationRunner.run(getApplicationContext(),(pct,phase,msg)->ui(()->{progress.setProgress(Math.min(940,pct*9));status.setText(phase);detail.setText(msg);}));
            latest=base.file;ui(()->{status.setText("Exhaustive matrix");detail.setText("Running strict assertions + repair regressions");progress.setProgress(955);});
            org.json.JSONObject finalSummary=CortexExhaustiveReportAugmenter.augment(getApplicationContext(),base.file);
            latest=base.file;ui(()->{setBusy(false);run.setText("Run full exhaustive again");share.setEnabled(true);progress.setProgress(1000);String overall=finalSummary.optString("overall","Complete");status.setText(overall);int tests=finalSummary.optInt("extra_test_count",0),executed=finalSummary.optInt("extra_executed_count",0),bu=finalSummary.optInt("blocked_waiting_user",0),bs=finalSummary.optInt("blocked_setup",0),pr=finalSummary.optInt("protected_confirmation_tests",0);detail.setText(tests+" extra checks · "+executed+" executed · "+bu+" access blockers · "+bs+" setup blockers · "+pr+" protected");summary.setText(finalSummary.toString());if(getIntent()!=null&&getIntent().getBooleanExtra("auto_share",false))shareLatest();});
        }catch(Throwable e){ui(()->{setBusy(false);run.setText("Try full exhaustive again");status.setText("Simulation failed");detail.setText(e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage()));summary.setText(android.util.Log.getStackTraceString(e));});}},"CortexExhaustiveTestLab").start();}

    void shareLatest(){if(latest==null||!latest.exists()){Toast.makeText(this,"No test JSON is ready yet",Toast.LENGTH_SHORT).show();return;}try{Uri u=FileProvider.getUriForFile(this,getPackageName()+".feedback.files",latest);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");i.putExtra(Intent.EXTRA_STREAM,u);i.putExtra(Intent.EXTRA_SUBJECT,latest.getName());i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share Cortex test JSON"));}catch(Throwable e){Toast.makeText(this,"Could not share JSON: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable e){Toast.makeText(this,"Could not open test surface",Toast.LENGTH_SHORT).show();}}
    void ui(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
}
