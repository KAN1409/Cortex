package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;

/** Live, user-visible explanation of the pipelines that normally run invisibly in background. */
public final class RuntimePipelineDiagnosticsActivity extends Activity {
    private VaultDb db;private TextView output;private Button refresh,export;private volatile boolean destroyed=false;
    private int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);build();load(false);}
    @Override protected void onDestroy(){destroyed=true;if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(CortexUi.aurora(this));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(14),dp(12),dp(14),dp(8));TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);TextView eye=CortexUi.plain(this,"INTENSIVE DIAGNOSTICS",10,CortexUi.AURORA);CortexUi.medium(eye);titleBox.addView(eye);TextView title=CortexUi.plain(this,"Runtime pipeline",25,CortexUi.TEXT);CortexUi.bold(title);titleBox.addView(title);head.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        TextView intro=CortexUi.text(this,"Shows where voice ASR, Cortex Relay delivery, V4 Situation/Pulse and autonomous Gemini are actually stopped. Refresh runs the same cognitive recovery used by NOW and Inbox.",11,CortexUi.MUTED);intro.setPadding(dp(20),0,dp(20),dp(10));root.addView(intro);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(20),0,dp(20),dp(9));refresh=CortexUi.action(this,"Run live refresh",CortexUi.BRAND,false);export=CortexUi.action(this,"Export intensive JSON",CortexUi.AURORA,false);actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(46),1);ep.setMargins(dp(8),0,0,0);actions.addView(export,ep);root.addView(actions);
        ScrollView sv=new ScrollView(this);output=CortexUi.plain(this,"Reading runtime state…",11,CortexUi.TEXT);output.setTextIsSelectable(true);output.setPadding(dp(20),dp(8),dp(20),dp(28));output.setTextDirection(android.view.View.TEXT_DIRECTION_FIRST_STRONG);sv.addView(output);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        refresh.setOnClickListener(v->load(true));export.setOnClickListener(v->{if(db!=null)CortexIntensiveDiagnosticExporter.exportAndShare(this,db);});
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void load(boolean runRefresh){
        if(destroyed||db==null)return;refresh.setEnabled(false);refresh.setText(runRefresh?"Refreshing pipeline…":"Reading…");
        new Thread(()->{JSONObject state=null;VaultDb local=null;try{local=new VaultDb(getApplicationContext());if(runRefresh)CognitiveManualRefreshV4.run(getApplicationContext(),local,()->load(false));state=CortexRuntimeDiagnosticV1.snapshot(getApplicationContext(),local);}catch(Throwable e){try{state=new JSONObject().put("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));}catch(Throwable ignored){}}finally{if(local!=null)try{local.close();}catch(Throwable ignored){}}
            final JSONObject result=state;runOnUiThread(()->{if(destroyed||isFinishing()||isDestroyed())return;output.setText(pretty(result));refresh.setEnabled(true);refresh.setText("Run live refresh");});
        },"CortexRuntimePipelineDiagnostic").start();
    }

    private static String pretty(JSONObject result){if(result==null)return"No diagnostic state available";try{return result.toString(2);}catch(Throwable ignored){return result.toString();}}
}
