package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dedicated share target for CORTEX_RESPONSE_V1. Never imports arbitrary shared text as Memory. */
public final class DeepBrainImportActivity extends Activity {
    TextView state; volatile boolean destroyed=false; final ExecutorService worker=Executors.newSingleThreadExecutor();
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();handle(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}

    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(24),dp(32),dp(24),dp(28));root.setBackgroundColor(CortexUi.BG);TextView h=CortexUi.plain(this,"Apply ChatGPT to Cortex",25,CortexUi.TEXT);CortexUi.medium(h);root.addView(h);state=CortexUi.text(this,"Validating response…",13,CortexUi.MUTED);state.setGravity(Gravity.CENTER);state.setPadding(0,dp(18),0,dp(18));root.addView(state,new LinearLayout.LayoutParams(-1,-2));TextView open=CortexUi.action(this,"Open Deep Brain",CortexUi.ACCENT,true);open.setOnClickListener(v->{startActivity(new Intent(this,DeepBrainActivity.class));finish();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(8),0,0);root.addView(open,p);TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);close.setOnClickListener(v->finish());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(46));cp.setMargins(0,dp(8),0,0);root.addView(close,cp);setContentView(root);CortexUi.fitSystemBars(this,root);}

    void handle(Intent i){if(i==null||!Intent.ACTION_SEND.equals(i.getAction())){state.setText("Nothing to apply.");return;}CharSequence text=i.getCharSequenceExtra(Intent.EXTRA_TEXT);String raw=text==null?"":text.toString();if(!raw.contains(CognitiveDeepBrainProtocolV4.RESPONSE_MARKER)){state.setText("Rejected safely: this share is not a CORTEX_RESPONSE_V1 Deep Brain response.");return;}state.setText("Validating request ID and allowed updates…");worker.execute(()->{VaultDb db=null;try{db=new VaultDb(getApplicationContext());CognitiveDeepBrainApplyV4.Result r=CognitiveDeepBrainApplyV4.apply(db,raw);post(()->state.setText((r.answer.isEmpty()?"":r.answer+"\n\n")+r.human()));}catch(Throwable e){post(()->state.setText("Response rejected safely: "+safe(e.getMessage())));}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}});}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}String safe(String s){return s==null||s.trim().isEmpty()?"invalid response":s.trim();}
}
