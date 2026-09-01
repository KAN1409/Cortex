package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;

/** In-app launcher for the exhaustive safe user simulation and master JSON export. */
public class UserSimulationTestLabActivity extends Activity {
    TextView status,detail,summary;ProgressBar progress;Button run,share;volatile boolean running=false,destroyed=false;File latest;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);build();if(getIntent()!=null&&getIntent().getBooleanExtra("autorun",false))run.postDelayed(this::startRun,350);}
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));TextView title=CortexUi.plain(this,"Full user simulation",27,CortexUi.TEXT);CortexUi.medium(title);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);
        TextView intro=CortexUi.text(this,"Runs a broad real-code Cortex journey and exports one maximum-detail JSON report. Synthetic database writes are rolled back. External actions are preview/resolution tests only.",12,CortexUi.MUTED);intro.setPadding(dp(2),dp(6),dp(2),dp(18));body.addView(intro);

        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(16),dp(15),dp(16),dp(15));TextView eyebrow=CortexUi.plain(this,"FULL SAFE · MAX DATA",9,CortexUi.LIME);CortexUi.medium(eyebrow);card.addView(eyebrow);status=CortexUi.plain(this,"Ready",21,CortexUi.TEXT);CortexUi.medium(status);status.setPadding(0,dp(6),0,0);card.addView(status);detail=CortexUi.text(this,"Functional test · 43 capabilities · synthetic user journey · OCR · Brain · Deep Review · full audit · exhaustive DB/runtime snapshot",11,CortexUi.MUTED);detail.setPadding(0,dp(6),0,dp(12));card.addView(detail);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(0);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));body.addView(card);

        run=new Button(this);run.setText("Run full simulation");run.setAllCaps(false);run.setTextColor(CortexUi.BG);run.setTextSize(13);run.setBackground(CortexUi.round(this,CortexUi.LIME,android.graphics.Color.TRANSPARENT,18));run.setOnClickListener(v->startRun());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(54));rp.setMargins(0,dp(14),0,dp(8));body.addView(run,rp);
        share=new Button(this);share.setText("Share latest JSON");share.setAllCaps(false);share.setTextColor(CortexUi.TEXT);share.setTextSize(12);share.setEnabled(false);share.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,18));share.setOnClickListener(v->shareLatest());body.addView(share,new LinearLayout.LayoutParams(-1,dp(50)));

        summary=CortexUi.text(this,"The report may contain real Cortex raw text, OCR, transcripts, model outputs, database rows and local file paths. Secret values and binary attachment contents are excluded.",10,CortexUi.FAINT);summary.setTextIsSelectable(true);summary.setPadding(dp(2),dp(18),dp(2),0);body.addView(summary);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void startRun(){if(running)return;running=true;latest=null;run.setEnabled(false);share.setEnabled(false);run.setText("Running…");status.setText("Starting");detail.setText("Preparing exhaustive simulation");progress.setProgress(5);
        new Thread(()->{try{CortexUserSimulationRunner.Result r=CortexUserSimulationRunner.run(getApplicationContext(),(pct,phase,msg)->ui(()->{progress.setProgress(pct*10);status.setText(phase);detail.setText(msg);}));latest=r.file;ui(()->{running=false;run.setEnabled(true);run.setText("Run again");share.setEnabled(true);status.setText(r.summary.optString("overall","Complete"));detail.setText("Master JSON ready · "+r.file.getName());summary.setText(r.summary.toString());if(getIntent()!=null&&getIntent().getBooleanExtra("auto_share",false))shareLatest();});}catch(Throwable e){ui(()->{running=false;run.setEnabled(true);run.setText("Try again");status.setText("Simulation failed");detail.setText(e.getClass().getSimpleName()+": "+(e.getMessage()==null?"":e.getMessage()));summary.setText(android.util.Log.getStackTraceString(e));});}},"CortexFullUserSimulation").start();}

    void shareLatest(){if(latest==null||!latest.exists()){Toast.makeText(this,"No simulation JSON is ready yet",Toast.LENGTH_SHORT).show();return;}try{Uri u=FileProvider.getUriForFile(this,getPackageName()+".feedback.files",latest);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");i.putExtra(Intent.EXTRA_STREAM,u);i.putExtra(Intent.EXTRA_SUBJECT,latest.getName());i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share Cortex simulation JSON"));}catch(Throwable e){Toast.makeText(this,"Could not share JSON: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void ui(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
}
