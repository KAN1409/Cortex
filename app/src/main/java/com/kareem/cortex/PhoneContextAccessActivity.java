package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** One place to enable/verify the Android signals that power Cortex phone awareness. */
public final class PhoneContextAccessActivity extends Activity {
    LinearLayout body;TextView overall,notifState,accessState,usageState,timeline;volatile boolean destroyed=false;final ExecutorService worker=Executors.newSingleThreadExecutor();
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();PhoneContextScheduler.schedule(this);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}

    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout hs=new LinearLayout(this);hs.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Phone context access",27,CortexUi.TEXT);CortexUi.medium(h);hs.addView(h);hs.addView(CortexUi.text(this,"Notifications + current app + recent app usage + bounded on-screen context, kept local by default.",11,CortexUi.MUTED));head.addView(hs,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);
        overall=statusCard("Overall phone awareness");
        body.addView(CortexUi.section(this,"Signals"));notifState=statusCard("All notifications");button("Open notification access",()->open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));accessState=statusCard("Current app + window context");button("Open Accessibility access",()->open(Settings.ACTION_ACCESSIBILITY_SETTINGS));usageState=statusCard("Foreground / recent app usage");button("Open Usage Access",()->PhoneUsageAccess.openSettings(this));
        body.addView(CortexUi.section(this,"Live local context"));timeline=statusCard("Recent phone context");button("Sync recent app usage now",this::syncNow);
        body.addView(CortexUi.section(this,"Boundary"));TextView note=CortexUi.text(this,"Cortex can continuously observe the Android signals exposed by Notification Listener, Accessibility and Usage Access. Password fields are redacted. The phone-context timeline is local-only by default and is bounded instead of becoming permanent memory. Android still does not expose another app's private database/process memory without stronger system privileges such as Shizuku/root/device-owner access.",12,CortexUi.MUTED);note.setPadding(dp(2),0,dp(2),dp(8));body.addView(note);setContentView(root);CortexUi.fitSystemBars(this,root);}

    TextView statusCard(String title){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(14),dp(12),dp(14),dp(12));TextView t=CortexUi.plain(this,title,13,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);TextView s=CortexUi.text(this,"",12,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);c.addView(s);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));body.addView(c,p);return s;}
    void button(String label,Runnable r){TextView b=CortexUi.action(this,label,CortexUi.ACCENT,false);b.setOnClickListener(v->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.setMargins(0,0,0,dp(8));body.addView(b,p);}

    void refresh(){boolean n=notificationEnabled(),a=CortexScreenAccessibilityService.connected()||accessibilityEnabled(),u=PhoneUsageAccess.has(this);int active=(n?1:0)+(a?1:0)+(u?1:0);overall.setText(active==3?"ACTIVE · Cortex has the full standard Android context stack":""+active+"/3 signal layers active"+(active<3?" · enable the missing access below":""));overall.setTextColor(active==3?CortexUi.SAGE:CortexUi.COPPER);notifState.setText(n?"ACTIVE · future notifications are ingested into the raw signal/thread pipeline":"NEEDS ACCESS · Cortex cannot see other apps' notifications yet");accessState.setText(a?"ACTIVE · app/window changes feed the bounded local phone-context timeline":"NEEDS ACCESS · Cortex cannot continuously know the current foreground/window context");usageState.setText(u?"ACTIVE · recent foreground/background app events can be synchronized":"NEEDS ACCESS · Android Usage Access has not been granted");loadTimeline();}
    void loadTimeline(){if(destroyed||worker.isShutdown())return;try{worker.execute(()->{VaultDb db=null;String text="";long count=0,apps=0;try{db=new VaultDb(getApplicationContext());PhoneContextStore.ensure(db);long since=System.currentTimeMillis()-24L*60L*60L*1000L;count=PhoneContextStore.countSince(db,since);apps=PhoneContextStore.distinctAppsSince(db,since);text=PhoneContextStore.recentSummary(db,6L*60L*60L*1000L,12);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}final String x=text;final long c=count,ap=apps;post(()->timeline.setText(c+" event"+(c==1?"":"s")+" · "+ap+" app"+(ap==1?"":"s")+" in the last 24h"+(x.isEmpty()?"\nNo recent context captured yet.":"\n\n"+x)));});}catch(RejectedExecutionException ignored){}}
    void syncNow(){if(!PhoneUsageAccess.has(this)){PhoneUsageAccess.openSettings(this);return;}Toast.makeText(this,"Syncing recent app usage…",Toast.LENGTH_SHORT).show();try{worker.execute(()->{VaultDb db=null;int n=-1;try{db=new VaultDb(getApplicationContext());n=PhoneUsageAccess.syncRecent(getApplicationContext(),db,System.currentTimeMillis()-24L*60L*60L*1000L);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}final int count=n;post(()->{Toast.makeText(this,count<0?"Usage Access is still missing":count+" new usage event"+(count==1?"":"s")+" indexed locally",Toast.LENGTH_LONG).show();refresh();});});}catch(RejectedExecutionException ignored){}}

    boolean notificationEnabled(){try{String x=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return x!=null&&x.contains(getPackageName());}catch(Throwable e){return false;}}
    boolean accessibilityEnabled(){try{String x=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return x!=null&&x.contains(getPackageName());}catch(Throwable e){return false;}}
    void open(String action){try{startActivity(new Intent(action));}catch(Throwable e){Toast.makeText(this,"Android settings could not be opened",Toast.LENGTH_LONG).show();}}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
}
