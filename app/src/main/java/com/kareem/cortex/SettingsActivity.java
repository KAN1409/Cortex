package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** User-facing settings. Engineering/test internals stay out of the normal product surface. */
public class SettingsActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onResume(){super.onResume();if(!isFinishing())build();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.plain(this,"Settings",29,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);TextView hs=CortexUi.text(this,"Core configuration only. Technical diagnostics stay outside the normal product flow.",11,CortexUi.MUTED);hs.setPadding(0,dp(2),0,0);titles.addView(hs);body.addView(head);

        body.addView(CortexUi.section(this,"Capture & permissions"));
        row(body,"Capture sources","Notifications, screen awareness and intentional capture permissions",CaptureOverviewActivity.class);
        actionRow(body,"Screen understanding",CortexScreenAccessibilityService.enabled(this)?"Enabled in Android":"Open Android Accessibility settings",()->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}});
        row(body,"Voice & transcription","Recording, transcription and local Whisper setup",AsrSettingsActivity.class);
        row(body,"Phone context & access","Usage access, phone context and special permissions",PhoneContextAccessActivity.class);

        body.addView(CortexUi.section(this,"AI & models"));
        row(body,"Reasoning model",OpenRouterKeyStore.has(this)?"Configured · "+OpenRouterModelConfig.generationModel(this):"Configure the current external reasoning provider",OpenRouterSettingsActivity.class);
        row(body,"Gemini / local device AI",GeminiKeyStore.has(this)?"Gemini configured · local device AI remains optional":"Vision fallback and local-device AI setup",GeminiSettingsActivity.class);
        row(body,"ChatGPT Teacher",CortexChatGptBridgeConfig.enabled(this)?"Bounded judgment teacher enabled":"Optional bounded judgment teacher",ChatGptTeacherActivity.class);

        body.addView(CortexUi.section(this,"Learning & data"));
        row(body,"Corrections & learning","Review learned corrections and teach Cortex from mistakes",CorrectionLearningActivity.class);
        row(body,"Data, privacy & integrations","Backup, restore, storage, privacy, calendar and contacts",FeatureHubActivity.class);

        body.addView(CortexUi.section(this,"System"));
        actionRow(body,"System Health","One simple live state: Healthy, Needs Attention, or Degraded",()->CortexNavigation.openPrimary(this,CortexDestinationRegistry.SYSTEM_HEALTH));

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void row(LinearLayout parent,String title,String sub,Class<?> cls){actionRow(parent,title,sub,()->startActivity(new Intent(this,cls)));}
    void actionRow(LinearLayout parent,String title,String sub,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(2),dp(14),dp(2),dp(14));c.setMinimumHeight(dp(48));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",25,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(40),dp(48)));c.setContentDescription(title+". "+sub);CortexUi.pressable(this,c,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));c.setOnClickListener(v->{try{action.run();}catch(Throwable e){Toast.makeText(this,"Action failed",Toast.LENGTH_LONG).show();}});parent.addView(c);parent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}
}
