package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Runtime truth for Cortex's zero-cost local AI stack. */
public final class LocalBrainSettingsActivity extends Activity {
    LinearLayout root;TextView nano,qwen;TextView prepareNano,qwenAction;volatile boolean destroyed;
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}

    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(18),dp(8),dp(18),dp(24));root.addView(CortexUi.simpleHeader(this,"Local Brain","Zero paid API",v->finish()));
        TextView intro=CortexUi.text(this,"Cortex v64 uses on-device intelligence first. Gemini Nano runs through Android AICore with no API key. The existing local Qwen model is an optional offline fallback. Paid OpenRouter/Gemini/Groq-style API routes are disabled by the v64 product policy.",13,CortexUi.MUTED);intro.setPadding(dp(2),dp(14),dp(2),dp(16));root.addView(intro);
        root.addView(CortexUi.section(this,"Gemini Nano"));nano=box();root.addView(nano);prepareNano=CortexUi.action(this,"Prepare Gemini Nano",CortexUi.LIME,false);prepareNano.setOnClickListener(v->prepareNano());add(prepareNano,10);
        root.addView(CortexUi.section(this,"Offline fallback"));qwen=box();root.addView(qwen);qwenAction=CortexUi.action(this,"",CortexUi.LIME,false);qwenAction.setOnClickListener(v->qwenAction());add(qwenAction,10);
        TextView note=CortexUi.text(this,"The Qwen model is several GB and is optional. Gemini Nano is the preferred local brain on supported devices. Voice transcription has its own Android on-device/system recognizer path and does not require either model.",11,CortexUi.MUTED);note.setPadding(dp(2),dp(18),dp(2),0);root.addView(note);setContentView(root);
    }
    TextView box(){TextView t=CortexUi.text(this,"Checking…",13,CortexUi.TEXT);t.setPadding(dp(13),dp(12),dp(13),dp(12));t.setBackground(CortexUi.velvet(this,14));return t;}
    void add(View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(top),0,0);root.addView(v,p);}

    void refresh(){
        LocalModelManager.Status qs=LocalModelManager.status(this);LocalLlmRuntime.State rs=LocalLlmRuntime.state(this);qwen.setText(LocalModelManager.MODEL_NAME+"\n"+qs.label+" · "+qs.percent+"%\nRuntime: "+rs.label());qwenAction.setText(LocalModelManager.installed(this)?"Qwen ready":(qs.partialBytes>0?"Resume Qwen download":"Download optional Qwen fallback"));qwenAction.setEnabled(!LocalModelManager.installed(this));
        nano.setText("Checking AICore / Gemini Nano…");prepareNano.setEnabled(false);new Thread(()->{NanoLocalBrain.State s=NanoLocalBrain.statusBlocking(getApplicationContext());if(!destroyed)runOnUiThread(()->{if(destroyed)return;nano.setText(s.label()+(s.getDetail().isEmpty()?"":"\n"+s.getDetail())+"\nNo API key · no token billing");prepareNano.setEnabled(!s.getAvailable());prepareNano.setText(s.getDownloading()?"Gemini Nano downloading…":"Prepare Gemini Nano");});},"cortex-nano-status").start();
    }
    void prepareNano(){prepareNano.setEnabled(false);prepareNano.setText("Preparing…");NanoLocalBrain.prepare(this,state->{if(destroyed)return;Toast.makeText(this,state.label(),Toast.LENGTH_LONG).show();refresh();});}
    void qwenAction(){LocalModelManager.Status s=LocalModelManager.status(this);if(s.partialBytes>0)LocalModelManager.resumeDownload(this);else LocalModelManager.startDownload(this);Toast.makeText(this,"Optional local Qwen download started. You can keep using Cortex meanwhile.",Toast.LENGTH_LONG).show();refresh();}
}
