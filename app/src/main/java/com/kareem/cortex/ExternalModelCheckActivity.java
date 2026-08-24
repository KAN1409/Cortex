package com.kareem.cortex;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Advanced Diagnostics: real end-to-end external provider/model request, never private evidence. */
public class ExternalModelCheckActivity extends Activity {
    TextView status,detail;TextView run;volatile boolean destroyed=false;final ExecutorService worker=Executors.newSingleThreadExecutor();
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();runCheck();}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}
    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(18),dp(20),dp(28));root.setBackgroundColor(CortexUi.BG);LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView h=CortexUi.plain(this,"External model check",27,CortexUi.TEXT);CortexUi.medium(h);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));TextView close=CortexUi.chip(this,"Close",CortexUi.MUTED,false);close.setOnClickListener(v->finish());head.addView(close);root.addView(head);TextView intro=CortexUi.text(this,"Real provider test: API key → configured model → network request → HTTP response → parsed model answer. No private Cortex evidence is sent.",12,CortexUi.MUTED);intro.setPadding(0,dp(8),0,dp(16));root.addView(intro);
        LinearLayout card=CortexUi.card(this,22);status=CortexUi.text(this,"Not checked yet",17,CortexUi.TEXT);CortexUi.medium(status);card.addView(status);detail=CortexUi.text(this,"",12,CortexUi.MUTED);detail.setPadding(0,dp(9),0,0);detail.setTextIsSelectable(true);card.addView(detail);root.addView(card,new LinearLayout.LayoutParams(-1,-2));run=CortexUi.action(this,"Run external model check",CortexUi.ACCENT,true);run.setOnClickListener(v->runCheck());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(50));rp.setMargins(0,dp(14),0,0);root.addView(run,rp);setContentView(root);}
    void runCheck(){if(destroyed||worker.isShutdown())return;run.setEnabled(false);run.setText("Checking provider…");status.setText("RUNNING");detail.setText("Sending a minimal non-private health-check request to the configured external model…");try{worker.execute(()->{ExternalBrainProvider.HealthReport r=ExternalBrainProvider.healthCheck(getApplicationContext());VaultDb db=null;try{db=new VaultDb(getApplicationContext());JSONObject m=new JSONObject().put("configured",r.configured).put("ok",r.ok).put("model",r.model).put("http_code",r.httpCode).put("latency_ms",r.latencyMs).put("status",r.status).put("error",r.error);if(r.ok)DiagnosticsLog.info(db,"ExternalModelCheck","end_to_end","pass",0,0,0,0,0,r.latencyMs,m);else DiagnosticsLog.warn(db,"ExternalModelCheck","end_to_end","fail","EXTERNAL_MODEL_CHECK",0,0,0,0,0,m);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}post(()->show(r));});}catch(RejectedExecutionException e){run.setEnabled(true);run.setText("Run external model check");}}
    void show(ExternalBrainProvider.HealthReport r){if(destroyed)return;status.setText(r.ok?"PASS · external model responded":"FAIL · external model unavailable");String when=new SimpleDateFormat("dd MMM yyyy · HH:mm:ss",Locale.getDefault()).format(new Date(r.checkedAt));detail.setText(r.human()+"\nChecked: "+when+"\n\nCombined mode will fall back to local Cortex reasoning when this route is unavailable.");run.setEnabled(true);run.setText("Run external model check again");}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
}
