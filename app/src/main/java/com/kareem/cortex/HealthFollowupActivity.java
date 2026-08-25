package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Health follow-up hub: source access, evidence imports, timeline and grounded follow-up state. */
public final class HealthFollowupActivity extends Activity {
    static final int REQ_HEALTH_CONNECT=941;
    LinearLayout body;TextView healthConnectState,samsungState,huaweiState,summary,timeline,syncButton;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{CortexHaptics.press(v);finish();});head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Health follow-up",27,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);titles.addView(CortexUi.text(this,"Evidence from health apps, watches, scans, documents and voice — one traceable timeline.",11,CortexUi.MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        body.addView(section("HEALTH SOURCES",CortexUi.GREEN));
        LinearLayout hc=sourceCard("Health Connect","Samsung Health / compatible sources","health",CortexUi.GREEN);healthConnectState=(TextView)hc.getTag();body.addView(hc);TextView grant=CortexUi.action(this,"Grant health read access",CortexUi.GREEN,false);grant.setOnClickListener(v->requestHealthPermissions());body.addView(grant,buttonParams());syncButton=CortexUi.action(this,"Sync last 30 days",CortexUi.RED,true);syncButton.setOnClickListener(v->syncHealth());body.addView(syncButton,buttonParams());

        LinearLayout samsung=sourceCard("Samsung Health","Galaxy / Samsung Health through Health Connect","watch",CortexUi.RED);samsungState=(TextView)samsung.getTag();body.addView(samsung,margins(0,8,0,0));TextView openSamsung=CortexUi.action(this,"Open Samsung Health",CortexUi.RED,false);openSamsung.setOnClickListener(v->openPackage("com.sec.android.app.shealth","Samsung Health is not installed"));body.addView(openSamsung,buttonParams());

        LinearLayout huawei=sourceCard("Huawei Health / Watch","Direct Health Kit connector gate","watch",CortexUi.ORANGE);huaweiState=(TextView)huawei.getTag();body.addView(huawei,margins(0,8,0,0));TextView openHuawei=CortexUi.action(this,"Open Huawei Health",CortexUi.ORANGE,false);openHuawei.setOnClickListener(v->openPackage("com.huawei.health","Huawei Health is not installed"));body.addView(openHuawei,buttonParams());TextView hwNote=CortexUi.text(this,"Direct Huawei watch history is not faked: Huawei Health Kit requires an AppGallery Connect app, signing configuration and approved health-data scopes. Cortex keeps this source as SETUP REQUIRED until those credentials/scopes exist.",10,CortexUi.MUTED);hwNote.setPadding(dp(3),0,dp(3),dp(5));body.addView(hwNote);

        body.addView(section("IMPORT HEALTH EVIDENCE",CortexUi.YELLOW));LinearLayout imports=CortexUi.card(this,18);imports.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);addImport(r1,"Scan / photo","photo","photo",CortexUi.GREEN,0);addImport(r1,"Document","file","file",CortexUi.ORANGE,7);imports.addView(r1,new LinearLayout.LayoutParams(-1,dp(58)));LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);addImport(r2,"Voice note","wave","voice",CortexUi.RED,0);addImport(r2,"Text note","text","text",CortexUi.YELLOW,7);LinearLayout.LayoutParams r2p=new LinearLayout.LayoutParams(-1,dp(58));r2p.setMargins(0,dp(7),0,0);imports.addView(r2,r2p);body.addView(imports);
        TextView importNote=CortexUi.plain(this,"Imported items keep the original file/audio in Cortex and are linked into the health evidence timeline after capture.",10,CortexUi.MUTED);importNote.setPadding(dp(3),dp(7),dp(3),0);body.addView(importNote);

        body.addView(section("HEALTH TIMELINE",CortexUi.RED));summary=statusCard("Current health evidence");timeline=statusCard("Recent measurements");
        body.addView(section("BOUNDARY",CortexUi.MUTED));TextView boundary=CortexUi.text(this,"Cortex can organize health evidence, surface trends and pending follow-ups, and keep every insight traceable to its source. It must not turn an inferred pattern into a confirmed diagnosis or silently alter medical care.",11,CortexUi.MUTED);body.addView(boundary);

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    LinearLayout sourceCard(String title,String sub,String icon,int color){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(12),dp(11),dp(12),dp(11));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,0,0);row.addView(tx,xp);TextView t=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(t);tx.addView(t);TextView s=CortexUi.plain(this,sub,10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tx.addView(s);TextView state=CortexUi.text(this,"Checking…",10,CortexUi.MUTED);state.setPadding(0,dp(6),0,0);tx.addView(state);c.addView(row);c.setTag(state);return c;}
    TextView statusCard(String title){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(13),dp(11),dp(13),dp(11));TextView t=CortexUi.plain(this,title,12,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);TextView s=CortexUi.text(this,"",11,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);c.addView(s);body.addView(c,margins(0,0,0,8));return s;}
    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.08f);h.setPadding(dp(1),dp(20),0,dp(8));return h;}

    void addImport(LinearLayout row,String label,String icon,String mode,int color,int left){LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);CortexUi.pressable(this,b,CortexUi.matte(this,15));b.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView t=CortexUi.plain(this,label,10,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(dp(6),0,0,0);b.addView(t,tp);b.setOnClickListener(v->launchHealthCapture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(b,p);}

    void refresh(){
        boolean samsung=installed("com.sec.android.app.shealth"),huawei=installed("com.huawei.health");samsungState.setText(samsung?"INSTALLED · connect Samsung Health to Health Connect, then grant Cortex read access":"NOT INSTALLED · Health Connect may still contain data from other sources");samsungState.setTextColor(samsung?CortexUi.GREEN:CortexUi.MUTED);huaweiState.setText(huawei?"INSTALLED · DIRECT DATA ACCESS NEEDS HEALTH KIT SETUP":"NOT INSTALLED · Health Kit connector remains optional");huaweiState.setTextColor(huawei?CortexUi.ORANGE:CortexUi.MUTED);
        int sdk=HealthConnectBridge.sdkStatus(this);if(sdk!=3){healthConnectState.setText(sdk==2?"PROVIDER UPDATE REQUIRED":"UNAVAILABLE ON THIS DEVICE");healthConnectState.setTextColor(CortexUi.ORANGE);syncButton.setEnabled(false);}else{syncButton.setEnabled(true);HealthConnectBridge.permissionStatus(this,(granted,total,error)->{if(isFinishing()||isDestroyed())return;if(error!=null){healthConnectState.setText("AVAILABLE · permission check failed: "+error);healthConnectState.setTextColor(CortexUi.ORANGE);}else{healthConnectState.setText(granted==total?"ACTIVE · "+granted+"/"+total+" read scopes granted":"NEEDS ACCESS · "+granted+"/"+total+" read scopes granted");healthConnectState.setTextColor(granted==total?CortexUi.GREEN:CortexUi.ORANGE);}});}
        VaultDb db=null;try{db=new VaultDb(this);HealthStore.Summary s=HealthStore.summary(db);summary.setText(s.metricCount+" measurements · "+s.evidenceCount+" imported evidence item"+(s.evidenceCount==1?"":"s")+" · "+s.openFollowups+" open follow-up"+(s.openFollowups==1?"":"s"));String recent=HealthStore.recentTimeline(db,8);timeline.setText(recent.isEmpty()?"No health measurements synced yet.":recent);}catch(Throwable e){summary.setText("Health timeline unavailable: "+e.getClass().getSimpleName());timeline.setText("");}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    void requestHealthPermissions(){try{startActivityForResult(HealthConnectBridge.permissionIntent(this),REQ_HEALTH_CONNECT);}catch(Throwable e){Toast.makeText(this,"Could not open Health Connect permissions",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_HEALTH_CONNECT)refresh();}
    void syncHealth(){syncButton.setEnabled(false);syncButton.setText("Syncing health data…");HealthConnectBridge.syncRecent(this,30,(seen,added,error)->{if(isFinishing()||isDestroyed())return;syncButton.setEnabled(true);syncButton.setText("Sync last 30 days");if(error==null){CortexHaptics.confirm(syncButton);Toast.makeText(this,"Health sync: "+added+" new / "+seen+" seen",Toast.LENGTH_LONG).show();}else{CortexHaptics.reject(syncButton);Toast.makeText(this,"Health sync stopped: "+error,Toast.LENGTH_LONG).show();}refresh();});}
    void launchHealthCapture(String mode){Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);i.putExtra("health_context",true);startActivity(i);}
    boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}catch(Throwable e){return false;}}
    void openPackage(String pkg,String missing){try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);return;}}catch(Throwable ignored){}Toast.makeText(this,missing,Toast.LENGTH_LONG).show();}
    LinearLayout.LayoutParams buttonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));p.setMargins(0,dp(7),0,0);return p;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
