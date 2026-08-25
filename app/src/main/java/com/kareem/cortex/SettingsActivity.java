package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Settings contains configuration only. Technical/test internals live under Advanced. */
public class SettingsActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{CortexHaptics.press(v);finish();});head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));TextView h=CortexUi.plain(this,"Settings",29,CortexUi.TEXT);CortexUi.medium(h);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        body.addView(CortexUi.section(this,"Brain"));
        String remote=OpenRouterKeyStore.has(this)?"Primary · "+OpenRouterModelConfig.generationModel(this)+" via OpenRouter":"Configure OpenRouter · default model stealth/ox-alpha";
        row(body,"Reasoning model",remote,OpenRouterSettingsActivity.class);
        row(body,"Vision / fallback",GeminiKeyStore.has(this)?"Gemini configured · retained for vision and provider fallback":"Optional Gemini fallback and cloud vision",GeminiSettingsActivity.class);

        body.addView(CortexUi.section(this,"Capture & phone awareness"));
        row(body,"Access Center","Runtime permissions, Notification Listener, Accessibility, Usage Access, background reliability and optional Shizuku",PhoneContextAccessActivity.class);
        row(body,"Transcription","Voice transcription providers and preferences",AsrSettingsActivity.class);
        actionRow(body,"Screen understanding",CortexScreenAccessibilityService.connected()?"Ready · explicit Understand screen capture + local phone context":"Enable screen/window understanding",()->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}});

        body.addView(CortexUi.section(this,"Health"));
        row(body,"Health follow-up","Samsung Health / Health Connect, Huawei Health gate, scans, documents, voice and a grounded health timeline",HealthFollowupActivity.class);

        body.addView(CortexUi.section(this,"Learning"));
        row(body,"Prompt Library","Reuse prompts, run them through Brain, keep results and ratings",PromptLibraryActivity.class);
        row(body,"Corrections & learning","Correct recent Cortex understanding/transcripts and manage user-approved learning",CorrectionLearningActivity.class);
        body.addView(CortexUi.section(this,"Data"));row(body,"Data & integrations","Backup, validated restore, privacy, calendar and contacts",FeatureHubActivity.class);
        body.addView(CortexUi.section(this,"Advanced"));row(body,"43 capabilities","See what is ACTIVE, READY, needs access/setup, or failed right now",CapabilityMatrixActivity.class);row(body,"Advanced diagnostics","External model health, phone-context health, runtime, audits, OCR and recovery tools",EnvironmentActivity.class);row(body,"Review queue","Resolve uncertain actions, waiting items, decisions and projects",ReviewQueueActivity.class);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }
    void row(LinearLayout parent,String title,String sub,Class<?> cls){actionRow(parent,title,sub,()->startActivity(new Intent(this,cls)));}
    void actionRow(LinearLayout parent,String title,String sub,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(2),dp(14),dp(2),dp(14));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",25,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(32),dp(44)));CortexUi.pressable(this,c,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));c.setOnClickListener(v->action.run());parent.addView(c);parent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}
}
