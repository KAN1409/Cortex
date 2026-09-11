package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Shallow user settings. Engineering/test internals are consolidated into one system status surface. */
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
        row(body,"ChatGPT Teacher",CortexChatGptBridgeConfig.enabled(this)?"Bounded judgment teacher enabled · tap to sync or review":"Configure private teacher relay · optional, never required for Cortex",ChatGptTeacherActivity.class);
        body.addView(CortexUi.section(this,"Learning & knowledge"));
        row(body,"Corrections & learning","Teach Cortex from mistakes and review learned corrections",CorrectionLearningActivity.class);
        row(body,"Data, privacy & integrations","Backup, restore, storage, privacy, calendar and contacts",FeatureHubActivity.class);
        body.addView(CortexUi.section(this,"System health"));

        String crash=CrashRecorder.read(this,60000);
        String processExit=ProcessExitRecorder.read(this,120000);
        actionRow(body,"Share Java crash report",crash.trim().isEmpty()?"No uncaught Java crash is currently stored":"Java exception captured locally · exports as a .txt attachment",()->shareCrash(crash));
        actionRow(body,"Refresh + share Android process exit",processExit.trim().isEmpty()?"Reads Android's last process exit on demand, then exports the full trace":"Stored exit exists · tap to refresh from Android and export the newest full trace",this::refreshAndShareProcessExit);
        actionRow(body,"Export attention trace","Saves the FULL JSON to Downloads/Cortex, then opens Share",this::shareAttentionTrace);

        LinearLayout diagnostic=CortexUi.card(this,20);diagnostic.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout dtop=new LinearLayout(this);dtop.setGravity(Gravity.CENTER_VERTICAL);dtop.addView(CortexUi.glyph(this,"check",CortexUi.LIME,true),new LinearLayout.LayoutParams(dp(50),dp(50)));LinearLayout dtx=new LinearLayout(this);dtx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams dx=new LinearLayout.LayoutParams(0,-2,1);dx.setMargins(dp(12),0,0,0);dtop.addView(dtx,dx);TextView dt=CortexUi.plain(this,"Cortex System Status",17,CortexUi.TEXT);CortexUi.medium(dt);dtx.addView(dt);TextView ds=CortexUi.text(this,"One authoritative extensive test plus live GREEN / AMBER / RED / QUARANTINED status for models, brain, capture, database, background work and runtime safety.",11,CortexUi.MUTED);ds.setPadding(0,dp(4),0,0);dtx.addView(ds);diagnostic.addView(dtop);TextView run=CortexUi.action(this,"OPEN SYSTEM STATUS",CortexUi.LIME,true);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(46));rp.setMargins(0,dp(12),0,0);diagnostic.addView(run,rp);run.setOnClickListener(v->startActivity(new Intent(this,CortexAuditActivity.class)));body.addView(diagnostic);
        TextView advanced=CortexUi.action(this,"Advanced configuration",CortexUi.FAINT,false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(42));ap.setMargins(0,dp(12),0,0);body.addView(advanced,ap);advanced.setOnClickListener(v->startActivity(new Intent(this,PhoneContextAccessActivity.class)));
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void shareAttentionTrace(){
        Toast.makeText(this,"Building full Attention Trace…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            VaultDb helper=null;
            try{
                helper=new VaultDb(getApplicationContext());
                String trace=AttentionTraceExporter.export(helper.getReadableDatabase());
                String fileName="cortex-attention-trace-v4-"+System.currentTimeMillis()+".json";
                Uri saved=null;
                try{saved=DiagnosticFileShare.saveToDownloads(getApplicationContext(),fileName,trace);}catch(Throwable ignored){}
                final boolean persisted=saved!=null;
                runOnUiThread(()->{
                    if(persisted)Toast.makeText(this,"Full JSON saved to Downloads/Cortex",Toast.LENGTH_LONG).show();
                    else Toast.makeText(this,"Public Downloads save unavailable · opening JSON share",Toast.LENGTH_LONG).show();
                    shareAttachment("Cortex attention trace","Share Cortex attention trace",fileName,trace);
                });
            }catch(Throwable e){
                runOnUiThread(()->Toast.makeText(this,"Attention trace export failed: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show());
            }finally{
                if(helper!=null)try{helper.close();}catch(Throwable ignored){}
            }
        },"cortex-attention-trace-export").start();
    }

    void shareCrash(String crash){
        if(crash==null||crash.trim().isEmpty()){Toast.makeText(this,"No Java crash report stored",Toast.LENGTH_LONG).show();return;}
        shareAttachment("Cortex Java crash report","Share Cortex Java crash report","cortex-java-crash.txt",crash);
    }

    void refreshAndShareProcessExit(){
        Toast.makeText(this,"Reading Android process-exit trace…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{ProcessExitRecorder.captureHistoricalExit(getApplicationContext());}catch(Throwable ignored){}
            final String report=ProcessExitRecorder.read(getApplicationContext(),120000);
            runOnUiThread(()->{
                if(report.trim().isEmpty()){Toast.makeText(this,"Android did not provide a process-exit trace",Toast.LENGTH_LONG).show();return;}
                shareAttachment("Cortex Android process exit","Share Cortex Android process exit","cortex-process-exit.txt",report);
            });
        },"cortex-exit-trace-export").start();
    }

    void shareAttachment(String subject,String chooser,String fileName,String text){
        try{DiagnosticFileShare.share(this,subject,chooser,fileName,text);}
        catch(Throwable shareError){
            try{
                String summary=DiagnosticFileShare.summary(text);
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText(subject,summary));
                Toast.makeText(this,"Attachment share failed · short summary copied instead",Toast.LENGTH_LONG).show();
            }catch(Throwable ignored){Toast.makeText(this,"Could not export report",Toast.LENGTH_LONG).show();}
        }
    }

    void row(LinearLayout parent,String title,String sub,Class<?> cls){actionRow(parent,title,sub,()->startActivity(new Intent(this,cls)));}
    void actionRow(LinearLayout parent,String title,String sub,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(2),dp(14),dp(2),dp(14));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",25,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(32),dp(44)));CortexUi.pressable(this,c,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));c.setOnClickListener(v->{try{action.run();}catch(Throwable e){Toast.makeText(this,"Action failed",Toast.LENGTH_LONG).show();}});parent.addView(c);parent.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}
}
