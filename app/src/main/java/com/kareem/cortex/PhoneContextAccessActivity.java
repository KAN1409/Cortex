package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** One authoritative place to inspect, grant and explain Android access gates used by Cortex. */
public final class PhoneContextAccessActivity extends Activity {
    static final int REQ_MIC=801,REQ_POST=802,REQ_CONTACTS=803,REQ_CALENDAR=804;
    LinearLayout body,gateHost;TextView overall,timeline;volatile boolean destroyed=false;final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener=(requestCode,grantResult)->{if(requestCode!=ShizukuContextBridge.REQUEST_CODE||destroyed)return;Toast.makeText(this,grantResult==PackageManager.PERMISSION_GRANTED?"Shizuku access granted":"Shizuku access not granted",Toast.LENGTH_SHORT).show();refresh();};
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);try{Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);}catch(Throwable ignored){}build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();PhoneContextScheduler.schedule(this);}
    @Override protected void onDestroy(){destroyed=true;try{Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);}catch(Throwable ignored){}worker.shutdownNow();super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{CortexHaptics.press(v);finish();});head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout hs=new LinearLayout(this);hs.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Cortex Access Center",27,CortexUi.TEXT);CortexUi.medium(h);hs.addView(h);hs.addView(CortexUi.text(this,"Real Android gates only: Cortex requests what powers a current feature and leaves unrelated privileged access alone.",11,CortexUi.MUTED));head.addView(hs,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        overall=statusCard("Phone environment coverage");
        gateHost=new LinearLayout(this);gateHost.setOrientation(LinearLayout.VERTICAL);body.addView(gateHost);

        body.addView(section("LIVE LOCAL CONTEXT",CortexUi.RED));timeline=statusCard("Recent phone context");TextView sync=CortexUi.action(this,"Sync recent app usage now",CortexUi.ORANGE,false);sync.setOnClickListener(v->syncNow());body.addView(sync,buttonParams());

        body.addView(section("INTENTIONALLY NOT REQUESTED",CortexUi.MUTED));
        LinearLayout boundary=CortexUi.card(this,18);boundary.setPadding(dp(13),dp(12),dp(13),dp(12));boundary.addView(CortexUi.text(this,"Camera — photo/scan import currently uses Android's picker.\n\nAll files / broad storage — files and media use scoped URI grants.\n\nOverlay — no current Cortex feature requires drawing over other apps.\n\nExact alarm — current scheduling does not require exact-alarm privilege.\n\nBluetooth / Location — Samsung Health enters through Health Connect and the current Huawei connector is an app-level Health Kit gate, not direct sensor streaming.\n\nDirect calendar write — Cortex reads Calendar only; external event changes open approval-first drafts in the owning calendar app.",11,CortexUi.MUTED));body.addView(boundary);
        TextView policy=CortexUi.text(this,"Foreground-service and network permissions are manifest capabilities, not user grant screens. Package visibility is also declarative; it is not a runtime access gate. Shizuku remains optional and Cortex does not expose arbitrary shell execution through this screen.",10,CortexUi.FAINT);policy.setPadding(dp(3),dp(9),dp(3),0);body.addView(policy);

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void refresh(){
        if(destroyed||gateHost==null)return;List<AccessGateRegistry.Gate> gates=AccessGateRegistry.snapshot(this);int active=0,total=0;for(AccessGateRegistry.Gate g:gates)if(g.recommended){total++;if(g.active)active++;}overall.setText(active+"/"+total+" recommended awareness gates active"+(active==total?" · standard phone environment ready":" · enable missing gates below"));overall.setTextColor(active==total?CortexUi.GREEN:CortexUi.ORANGE);
        gateHost.removeAllViews();AccessGateRegistry.Kind last=null;for(AccessGateRegistry.Gate g:gates){if(last!=g.kind){last=g.kind;gateHost.addView(section(kindTitle(last),kindColor(last)));}gateHost.addView(gateCard(g),margins(0,0,0,8));}
        loadTimeline();
    }

    View gateCard(AccessGateRegistry.Gate g){
        int color=g.active?CortexUi.GREEN:g.recommended?CortexUi.RED:CortexUi.ORANGE;LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(12),dp(11),dp(12),dp(11));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,gateIcon(g.key),color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,0,0);row.addView(text,xp);TextView title=CortexUi.plain(this,g.title,14,CortexUi.TEXT);CortexUi.medium(title);text.addView(title);TextView why=CortexUi.text(this,g.why,10,CortexUi.MUTED);why.setPadding(0,dp(3),0,0);text.addView(why);TextView state=CortexUi.plain(this,g.status,9,color);CortexUi.medium(state);state.setPadding(0,dp(6),0,0);text.addView(state);card.addView(row);
        String action=actionLabel(g);if(action!=null){TextView b=CortexUi.action(this,action,color,false);b.setOnClickListener(v->act(g));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(41));bp.setMargins(0,dp(8),0,0);card.addView(b,bp);}return card;
    }

    void act(AccessGateRegistry.Gate g){switch(g.key){
        case "microphone":runtime(Manifest.permission.RECORD_AUDIO,REQ_MIC);break;
        case "app_notifications":if(Build.VERSION.SDK_INT>=33&&!AccessGateRegistry.granted(this,Manifest.permission.POST_NOTIFICATIONS))runtime(Manifest.permission.POST_NOTIFICATIONS,REQ_POST);else openAppNotificationSettings();break;
        case "notification_listener":open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);break;
        case "accessibility":open(Settings.ACTION_ACCESSIBILITY_SETTINGS);break;
        case "usage":PhoneUsageAccess.openSettings(this);break;
        case "contacts":runtime(Manifest.permission.READ_CONTACTS,REQ_CONTACTS);break;
        case "calendar":runtime(Manifest.permission.READ_CALENDAR,REQ_CALENDAR);break;
        case "battery":open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);break;
        case "health":startActivity(new Intent(this,HealthFollowupActivity.class));break;
        case "shizuku":shizukuAction();break;
    }}

    void runtime(String permission,int code){if(Build.VERSION.SDK_INT<23||checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED){refresh();return;}requestPermissions(new String[]{permission},code);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);boolean ok=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;CortexHaptics.confirm(overall);Toast.makeText(this,ok?"Access granted":"Access not granted",Toast.LENGTH_SHORT).show();refresh();}

    String actionLabel(AccessGateRegistry.Gate g){if("saf".equals(g.key))return null;if("health".equals(g.key))return"Open Health access";if("shizuku".equals(g.key))return g.active?"Sync process snapshot":ShizukuContextBridge.available()?"Grant Shizuku access":"Open / start Shizuku";if("battery".equals(g.key))return g.active?"Open battery settings":"Review battery optimization";if(g.active&&("notification_listener".equals(g.key)||"accessibility".equals(g.key)||"usage".equals(g.key)))return"Open Android settings";if(g.active&&"app_notifications".equals(g.key))return"Notification settings";if(g.active)return null;return g.kind==AccessGateRegistry.Kind.RUNTIME?"Grant access":"Open Android settings";}
    String gateIcon(String key){if(key.contains("microphone"))return"wave";if(key.contains("notification"))return"bolt";if(key.contains("accessibility"))return"open";if(key.contains("usage"))return"nodes";if(key.contains("contacts"))return"people";if(key.contains("calendar"))return"decision";if(key.contains("battery"))return"bolt";if(key.contains("health"))return"health";if(key.contains("shizuku"))return"settings";if(key.contains("saf"))return"file";return"info";}
    String kindTitle(AccessGateRegistry.Kind k){if(k==AccessGateRegistry.Kind.RUNTIME)return"RUNTIME PERMISSIONS";if(k==AccessGateRegistry.Kind.SPECIAL_ACCESS)return"ANDROID SPECIAL ACCESS";if(k==AccessGateRegistry.Kind.HEALTH)return"HEALTH DATA";if(k==AccessGateRegistry.Kind.OPTIONAL)return"OPTIONAL SYSTEM VISIBILITY";return"NO-GATE CAPABILITIES";}
    int kindColor(AccessGateRegistry.Kind k){if(k==AccessGateRegistry.Kind.RUNTIME)return CortexUi.RED;if(k==AccessGateRegistry.Kind.SPECIAL_ACCESS)return CortexUi.ORANGE;if(k==AccessGateRegistry.Kind.HEALTH)return CortexUi.GREEN;if(k==AccessGateRegistry.Kind.OPTIONAL)return CortexUi.YELLOW;return CortexUi.MUTED;}

    TextView statusCard(String title){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(13),dp(11),dp(13),dp(11));TextView t=CortexUi.plain(this,title,12,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);TextView s=CortexUi.text(this,"",11,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);c.addView(s);body.addView(c,margins(0,0,0,8));return s;}
    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.08f);h.setPadding(dp(1),dp(18),0,dp(8));return h;}

    void loadTimeline(){if(destroyed||worker.isShutdown())return;try{worker.execute(()->{VaultDb db=null;String text="";long count=0,apps=0;try{db=new VaultDb(getApplicationContext());PhoneContextStore.ensure(db);long since=System.currentTimeMillis()-24L*60L*60L*1000L;count=PhoneContextStore.countSince(db,since);apps=PhoneContextStore.distinctAppsSince(db,since);text=PhoneContextStore.recentSummary(db,6L*60L*60L*1000L,10);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}final String x=text;final long c=count,ap=apps;post(()->timeline.setText(c+" event"+(c==1?"":"s")+" · "+ap+" app/process source"+(ap==1?"":"s")+" in the last 24h"+(x.isEmpty()?"\nNo recent context captured yet.":"\n\n"+x)));});}catch(RejectedExecutionException ignored){}}
    void syncNow(){if(!PhoneUsageAccess.has(this)){PhoneUsageAccess.openSettings(this);return;}Toast.makeText(this,"Syncing recent app usage…",Toast.LENGTH_SHORT).show();try{worker.execute(()->{VaultDb db=null;int n=-1;try{db=new VaultDb(getApplicationContext());n=PhoneUsageAccess.syncRecent(getApplicationContext(),db,System.currentTimeMillis()-24L*60L*60L*1000L);}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}final int count=n;post(()->{Toast.makeText(this,count<0?"Usage Access is still missing":count+" new usage event"+(count==1?"":"s")+" indexed locally",Toast.LENGTH_LONG).show();refresh();});});}catch(RejectedExecutionException ignored){}}

    void shizukuAction(){if(!ShizukuContextBridge.available()){Intent launch=null;try{launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");}catch(Throwable ignored){}if(launch!=null){try{startActivity(launch);return;}catch(Throwable ignored){}}Toast.makeText(this,"Start Shizuku first. Standard Cortex phone awareness still works without it.",Toast.LENGTH_LONG).show();return;}if(!ShizukuContextBridge.granted()){if(ShizukuContextBridge.needsRationale())Toast.makeText(this,"Allow Cortex in Shizuku, then return here.",Toast.LENGTH_LONG).show();else ShizukuContextBridge.requestPermission();return;}captureProcesses();}
    void captureProcesses(){try{worker.execute(()->{VaultDb db=null;ShizukuContextBridge.Snapshot s;try{db=new VaultDb(getApplicationContext());s=ShizukuContextBridge.captureProcessSnapshot(getApplicationContext(),db);}catch(Throwable e){s=new ShizukuContextBridge.Snapshot(false,0,0,e.getClass().getSimpleName());}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}final ShizukuContextBridge.Snapshot x=s;post(()->{Toast.makeText(this,x.detail,Toast.LENGTH_LONG).show();refresh();});});}catch(RejectedExecutionException ignored){}}

    void openAppNotificationSettings(){try{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}catch(Throwable e){openAppDetails();}}
    void openAppDetails(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Throwable e){Toast.makeText(this,"Android app settings could not be opened",Toast.LENGTH_LONG).show();}}
    void open(String action){try{startActivity(new Intent(action));}catch(Throwable e){Toast.makeText(this,"Android settings could not be opened",Toast.LENGTH_LONG).show();}}
    LinearLayout.LayoutParams buttonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));p.setMargins(0,dp(7),0,0);return p;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    void post(Runnable r){if(destroyed||isFinishing()||isDestroyed())return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
}
