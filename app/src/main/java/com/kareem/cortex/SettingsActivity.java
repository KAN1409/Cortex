package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Shallow user settings. Engineering/test internals are consolidated into one Full Diagnostic. */
public class SettingsActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onResume(){super.onResume();if(!isFinishing())build();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.plain(this,"Settings",29,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);TextView hs=CortexUi.text(this,"Core configuration only. Cortex diagnostics live in one place.",11,CortexUi.MUTED);hs.setPadding(0,dp(2),0,0);titles.addView(hs);body.addView(head);

        body.addView(CortexUi.section(this,"Capture & permissions"));
        row(body,"Capture sources","Notifications, screen awareness, phone context and permission state",CaptureOverviewActivity.class);
        actionRow(body,"Screen understanding",CortexScreenAccessibilityService.enabled(this)?"Enabled in Android · live service state will reconnect automatically":"Not enabled · open Android Accessibility settings",()->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}});
        row(body,"Voice & transcription","Recording, transcription and local Whisper setup",AsrSettingsActivity.class);

        body.addView(CortexUi.section(this,"AI & models"));
        row(body,"Reasoning model",OpenRouterKeyStore.has(this)?"Configured · "+OpenRouterModelConfig.generationModel(this):"Configure the current external reasoning provider",OpenRouterSettingsActivity.class);
        row(body,"Gemini / local device AI",GeminiKeyStore.has(this)?"Gemini configured · local Gemini Nano is probed separately":"Vision fallback and local-device AI status",GeminiSettingsActivity.class);

        body.addView(CortexUi.section(this,"Learning & knowledge"));
        row(body,"Corrections & learning","Teach Cortex from mistakes and review learned corrections",CorrectionLearningActivity.class);
        row(body,"Data, privacy & integrations","Backup, restore, storage, privacy, calendar and contacts",FeatureHubActivity.class);

        body.addView(CortexUi.section(this,"System health"));
        row(body,"Last crash report",CrashRecorder.read(this,200).trim().isEmpty()?"No uncaught Java crash is currently stored":"Crash captured locally · open, copy or share without ADB, Wi‑Fi or Shizuku",CrashReportActivity.class);
        LinearLayout diagnostic=CortexUi.card(this,20);diagnostic.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout dtop=new LinearLayout(this);dtop.setGravity(Gravity.CENTER_VERTICAL);dtop.addView(CortexUi.glyph(this,"check",CortexUi.LIME,true),new LinearLayout.LayoutParams(dp(50),dp(50)));LinearLayout dtx=new LinearLayout(this);dtx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams dx=new LinearLayout.LayoutParams(0,-2,1);dx.setMargins(dp(12),0,0,0);dtop.addView(dtx,dx);TextView dt=CortexUi.plain(this,"Full Cortex Diagnostic",17,CortexUi.TEXT);CortexUi.medium(dt);dtx.addView(dt);TextView ds=CortexUi.text(this,"One exhaustive test for app identity, permissions, capture, storage, AI, background work, reliability and evidence.",11,CortexUi.MUTED);ds.setPadding(0,dp(4),0,0);dtx.addView(ds);diagnostic.addView(dtop);TextView run=CortexUi.action(this,"OPEN FULL DIAGNOSTIC",CortexUi.LIME,true);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(46));rp.setMargins(0,dp(12),0,0);diagnostic.addView(run,rp);run.setOnClickListener(v->startActivity(new Intent(this,CortexAuditActivity.class)));body.addView(diagnostic);

        TextView advanced=CortexUi.action(this,"Advanced configuration",CortexUi.FAINT,false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(42));ap.setMargins(0,dp(12),0,0);body.addView(advanced,ap);advanced.setOnClickListener(v->startActivity(new Intent(this,PhoneContextAccessActivity.class)));
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void row(LinearLayout parent,String title,String sub,Class<?> cls){actionRow(parent,title,sub,()->startActivity(new Intent(this,cls)));}
    void actionRow(LinearLayout parent,String title,String sub,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(2),dp(14),dp(2),dp(14));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",25,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(32),dp(44)));CortexUi.pressable(this,c,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));c.setOnClickListener(v->action.run());parent.addView(c);parent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}
}
