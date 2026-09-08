package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Voice transcription setup for the zero-paid-API Cortex runtime. */
public class AsrSettingsActivity extends Activity {
    LinearLayout root;TextView status;TextView prepare;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(18),dp(8),dp(18),dp(20));
        root.addView(CortexUi.simpleHeader(this,"Voice transcription","Zero-cost ASR",v->finish()));
        TextView sub=CortexUi.text(this,"Cortex now prefers Android's on-device speech recognizer for saved voice notes. If that path is unavailable, it falls back to the phone's system recognizer. No Gemini, Groq, OpenAI, Azure or other paid API key is required or used by this route.",13,CortexUi.MUTED);sub.setPadding(dp(2),dp(15),dp(4),dp(16));root.addView(sub);
        status=CortexUi.text(this,"",13,CortexUi.TEXT);status.setPadding(dp(13),dp(12),dp(13),dp(12));status.setBackground(CortexUi.velvet(this,14));root.addView(status);
        prepare=CortexUi.action(this,"Prepare offline Arabic + English speech",CortexUi.LIME,false);TextView done=CortexUi.action(this,"Done",CortexUi.LIME,true);
        prepare.setOnClickListener(v->prepareModel());done.setOnClickListener(v->finish());add(root,prepare,12);add(root,done,20);setContentView(root);
    }

    void refresh(){
        if(status==null)return;SystemAudioTranscriber.RuntimeStatus s=SystemAudioTranscriber.runtimeStatus(this);
        StringBuilder b=new StringBuilder();b.append("Paid API billing   OFF\n");b.append("On-device ASR      ").append(s.onDevice?"Available":"Not currently exposed").append("\n");b.append("System fallback    ").append(s.platform?"Available":"Unavailable").append("\n\n");b.append(s.detail).append("\n\nVoice recordings remain stored locally even if transcription fails, so they can be retried later.");status.setText(b.toString());prepare.setEnabled(s.onDevice);
    }

    void prepareModel(){prepare.setEnabled(false);prepare.setText("Preparing…");SystemAudioTranscriber.requestOnDeviceModel(this,(ok,detail)->runOnUiThread(()->{Toast.makeText(this,detail,Toast.LENGTH_LONG).show();prepare.setText("Prepare offline Arabic + English speech");refresh();}));}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,dp(48));x.setMargins(0,dp(top),0,0);p.addView(v,x);}
}
