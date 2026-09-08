package com.kareem.cortex;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Visible progress UI for local model import and code-switch transcription. */
public class WhisperProgressActivity extends Activity {
    long itemId; Handler h=new Handler(Looper.getMainLooper());
    TextView stage,detail,amount; ProgressBar bar; Button close;
    int bg=Color.rgb(16,17,20),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255);

    @Override public void onCreate(Bundle b){super.onCreate(b);itemId=getIntent()==null?0:getIntent().getLongExtra("item_id",0);build();h.post(poll);}
    @Override protected void onDestroy(){h.removeCallbacks(poll);super.onDestroy();}
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);} TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);return v;}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setBackgroundColor(bg);root.setPadding(dp(24),dp(34),dp(24),dp(24));
        TextView title=tv("LOCAL CODE-SWITCH ASR",21,text);title.setTypeface(null,1);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=tv("Egyptian Arabic + English • single context + span rescue + tail retry",14,muted);sub.setPadding(0,dp(6),0,dp(26));root.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        stage=tv("Preparing…",18,text);root.addView(stage,new LinearLayout.LayoutParams(-1,-2));
        bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(0);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(18));bp.setMargins(0,dp(18),0,dp(8));root.addView(bar,bp);
        amount=tv("Waiting…",14,accent);root.addView(amount,new LinearLayout.LayoutParams(-1,-2));
        detail=tv("",13,muted);detail.setPadding(0,dp(14),0,dp(24));root.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        close=new Button(this);close.setText("RUNNING…");close.setEnabled(false);close.setOnClickListener(v->finish());root.addView(close,new LinearLayout.LayoutParams(-1,dp(54)));
        setContentView(root);
    }

    final Runnable poll=new Runnable(){public void run(){
        String s=WhisperRuntimeState.stageName(WhisperProgressActivity.this),d=WhisperRuntimeState.detailText(WhisperProgressActivity.this);
        int pct=WhisperRuntimeState.progressPercent(WhisperProgressActivity.this);stage.setText(pretty(s));detail.setText(d==null?"":d);
        if("importing local model".equals(s)){bar.setIndeterminate(false);bar.setProgress(pct);amount.setText(WhisperRuntimeState.progressText(WhisperProgressActivity.this));}
        else if("model ready".equals(s)){
            if(itemId>0){bar.setIndeterminate(true);amount.setText("Model ready • starting re-analysis…");}
            else{bar.setIndeterminate(false);bar.setProgress(100);amount.setText("100% • model ready");close.setText("DONE");close.setEnabled(true);}
        }
        else if("queued local ASR".equals(s)){bar.setIndeterminate(true);amount.setText("Queued for local ASR…");}
        else if("detecting speech".equals(s)){bar.setIndeterminate(true);amount.setText("Finding real onset/end • keeping pauses in context…");}
        else if("loading model".equals(s)){bar.setIndeterminate(true);amount.setText("Local model ready • loading…");}
        else if("primary decode".equals(s)){bar.setIndeterminate(true);amount.setText("One multilingual context window • auto decode…");}
        else if("english rescue".equals(s)){bar.setIndeterminate(true);amount.setText("English span detected • local acoustic re-decode…");}
        else if("tail retry".equals(s)){bar.setIndeterminate(true);amount.setText("Checking final speech • recovering trailing switch…");}
        else if("transcribing".equals(s)){bar.setIndeterminate(true);amount.setText("Multilingual decode…");}
        else if("ready".equals(s)){bar.setIndeterminate(false);bar.setProgress(100);amount.setText("100% • transcription complete");close.setText("DONE");close.setEnabled(true);}
        else if("failed".equals(s)){bar.setIndeterminate(false);amount.setText("FAILED");close.setText("CLOSE");close.setEnabled(true);}
        if(!isFinishing()&&!isDestroyed()&&!close.isEnabled())h.postDelayed(this,400);
    }};

    String pretty(String s){if(s==null||s.isEmpty())return "Preparing…";String x=s.replace('_',' ');return Character.toUpperCase(x.charAt(0))+x.substring(1);}
}
