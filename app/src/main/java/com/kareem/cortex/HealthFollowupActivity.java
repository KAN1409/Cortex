package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Health follow-up hub: source access, evidence imports, timeline and grounded follow-up state. */
public final class HealthFollowupActivity extends Activity {
    static final int REQ_HEALTH_CONNECT=941;
    LinearLayout body;TextView healthConnectState,samsungState,huaweiState,summary,timeline,trends,syncButton;
    volatile boolean healthReady=false;volatile long healthSyncToken=0;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{CortexHaptics.press(v);finish();});head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Health follow-up",27,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);titles.addView(CortexUi.text(this,"Evidence from health apps, watches, scans, documents and voice — one traceable timeline.",11,CortexUi.MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        body.addView(section("HEALTH SOURCES",CortexUi.GREEN));
        LinearLayout hc=sourceCard("Health Connect","Samsung Health / compatible sources","health",CortexUi.GREEN);healthConnectState=(TextView)hc.getTag();body.addView(hc);TextView grant=CortexUi.action(this,"Grant health read access",CortexUi.GREEN,false);grant.setOnClickListener(v->requestHealthPermissions());body.addView(grant,buttonParams());syncButton=CortexUi.action(this,"Sync last 30 days",CortexUi.RED,true);syncButton.setEnabled(false);syncButton.setOnClickListener(v->syncHealth());body.addView(syncButton,buttonParams());

        LinearLayout samsung=sourceCard("Samsung Health","Galaxy / Samsung Health through Health Connect","watch",CortexUi.RED);samsungState=(TextView)samsung.getTag();body.addView(samsung,margins(0,8,0,0));TextView openSamsung=CortexUi.action(this,"Open Samsung Health",CortexUi.RED,false);openSamsung.setOnClickListener(v->openPackage("com.sec.android.app.shealth","Samsung Health is not installed"));body.addView(openSamsung,buttonParams());

        LinearLayout huawei=sourceCard("Huawei Health / Watch","Health Connect evidence + optional direct Health Kit gate","watch",CortexUi.ORANGE);huaweiState=(TextView)huawei.getTag();body.addView(huawei,margins(0,8,0,0));TextView openHuawei=CortexUi.action(this,"Open Huawei Health",CortexUi.ORANGE,false);openHuawei.setOnClickListener(v->openPackage("com.huawei.health","Huawei Health is not installed"));body.addView(openHuawei,buttonParams());TextView hwNote=CortexUi.text(this,"Cortex can preserve Huawei-origin records when they are actually exposed through Health Connect. Direct Huawei Health Kit access is separate and is never faked: it still requires AppGallery Connect, signing configuration and approved health-data scopes.",10,CortexUi.MUTED);hwNote.setPadding(dp(3),0,dp(3),dp(5));body.addView(hwNote);

        body.addView(section("IMPORT HEALTH EVIDENCE",CortexUi.YELLOW));LinearLayout imports=CortexUi.card(this,18);imports.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);addImport(r1,"Scan / photo","photo","photo",CortexUi.GREEN,0);addImport(r1,"Document","file","file",CortexUi.ORANGE,7);imports.addView(r1,new LinearLayout.LayoutParams(-1,dp(58)));LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);addImport(r2,"Voice note","wave","voice",CortexUi.RED,0);addImport(r2,"Text note","text","text",CortexUi.YELLOW,7);LinearLayout.LayoutParams r2p=new LinearLayout.LayoutParams(-1,dp(58));r2p.setMargins(0,dp(7),0,0);imports.addView(r2,r2p);body.addView(imports);
        TextView importNote=CortexUi.plain(this,"Imported items keep the original file/audio in Cortex and are linked into the health evidence timeline after capture.",10,CortexUi.MUTED);importNote.setPadding(dp(3),dp(7),dp(3),0);body.addView(importNote);

        body.addView(section("HEALTH TIMELINE",CortexUi.RED));summary=statusCard("Current health evidence");timeline=statusCard("Recent measurements");
        body.addView(section("TRENDS · LOCAL / GROUNDED",CortexUi.GREEN));trends=statusCard("Descriptive trends");TextView trendBoundary=CortexUi.plain(this,"Compared locally from stored measurements only. Higher / lower / similar is descriptive, not a clinical range, diagnosis or treatment recommendation. Each metric uses one observed source to avoid silent cross-source double counting.",9,CortexUi.MUTED);trendBoundary.setPadding(dp(3),0,dp(3),dp(4));body.addView(trendBoundary);
        body.addView(section("BOUNDARY",CortexUi.MUTED));TextView boundary=CortexUi.text(this,"Cortex can organize health evidence, surface descriptive trends and pending follow-ups, and keep every insight traceable to its source. It must not turn an inferred pattern into a confirmed diagnosis or silently alter medical care.",11,CortexUi.MUTED);body.addView(boundary);

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    LinearLayout sourceCard(String title,String sub,String icon,int color){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(12),dp(11),dp(12),dp(11));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,0,0);row.addView(tx,xp);TextView t=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(t);tx.addView(t);TextView s=CortexUi.plain(this,sub,10,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tx.addView(s);TextView state=CortexUi.text(this,"Checking…",10,CortexUi.MUTED);state.setPadding(0,dp(6),0,0);tx.addView(state);c.addView(row);c.setTag(state);return c;}
    TextView statusCard(String title){LinearLayout c=CortexUi.card(this,18);c.setPadding(dp(13),dp(11),dp(13),dp(11));TextView t=CortexUi.plain(this,title,12,CortexUi.TEXT);CortexUi.medium(t);c.addView(t);TextView s=CortexUi.text(this,"",11,CortexUi.MUTED);s.setPadding(0,dp(5),0,0);c.addView(s);body.addView(c,margins(0,0,0,8));return s;}
    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.08f);h.setPadding(dp(1),dp(20),0,dp(8));return h;}

    void addImport(LinearLayout row,String label,String icon,String mode,int color,int left){LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);CortexUi.pressable(this,b,CortexUi.matte(this,15));b.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView t=CortexUi.plain(this,label,10,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMargins(dp(6),0,0,0);b.addView(t,tp);b.setOnClickListener(v->launchHealthCapture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(b,p);}

    void refresh(){
        healthReady=false;syncButton.setEnabled(false);
        boolean samsungInstalled=installed("com.sec.android.app.shealth"),huaweiInstalled=installed("com.huawei.health");
        VaultDb db=null;try{
            db=new VaultDb(this);HealthStore.Summary s=HealthStore.summary(db);summary.setText(s.metricCount+" measurements · "+s.evidenceCount+" imported evidence item"+(s.evidenceCount==1?"":"s")+" · "+s.openFollowups+" open follow-up"+(s.openFollowups==1?"":"s"));String recent=HealthStore.recentTimeline(db,8);timeline.setText(recent.isEmpty()?"No health measurements synced yet.":recent);HealthTrendEngine.Report trend=HealthTrendEngine.build(db);trends.setText(trend.available()?trend.text:"No comparable health trend yet. Sync or import more measurements over time; Cortex will only compare periods when there is enough stored data.");
            HealthStore.SourceState ss=HealthStore.sourceState(db,"samsung_health"),hs=HealthStore.sourceState(db,"huawei_health");
            if(ss!=null&&"active_via_health_connect".equals(ss.status)){samsungState.setText("ACTIVE VIA HEALTH CONNECT · Samsung-origin records observed"+(ss.lastSyncAt>0?" · "+friendlyAge(ss.lastSyncAt):""));samsungState.setTextColor(CortexUi.GREEN);}else{samsungState.setText(samsungInstalled?"INSTALLED · connect Samsung Health to Health Connect, then grant Cortex read access":"NOT INSTALLED · Health Connect may still contain data from other sources");samsungState.setTextColor(samsungInstalled?CortexUi.GREEN:CortexUi.MUTED);}
            if(hs!=null&&"active_via_health_connect".equals(hs.status)){huaweiState.setText("DATA OBSERVED VIA HEALTH CONNECT"+(hs.lastSyncAt>0?" · "+friendlyAge(hs.lastSyncAt):"")+" · direct Health Kit remains optional setup");huaweiState.setTextColor(CortexUi.GREEN);}else{huaweiState.setText(huaweiInstalled?"INSTALLED · DIRECT DATA ACCESS STILL NEEDS HEALTH KIT SETUP":"NOT INSTALLED · direct Health Kit connector remains optional");huaweiState.setTextColor(huaweiInstalled?CortexUi.ORANGE:CortexUi.MUTED);}
        }catch(Throwable e){summary.setText("Health timeline unavailable: "+e.getClass().getSimpleName());timeline.setText("");trends.setText("Health trend layer unavailable: "+e.getClass().getSimpleName());samsungState.setText(samsungInstalled?"INSTALLED":"NOT INSTALLED");huaweiState.setText(huaweiInstalled?"INSTALLED · DIRECT SETUP REQUIRED":"NOT INSTALLED");}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}

        int sdk=HealthConnectBridge.sdkStatus(this);HealthSyncPolicy.Failure sdkState=HealthSyncPolicy.sdk(sdk);if(!"READY".equals(sdkState.state)){healthConnectState.setText((HealthSyncResult.UPDATE_REQUIRED.equals(sdkState.state)?"PROVIDER UPDATE REQUIRED":"UNAVAILABLE ON THIS DEVICE")+" · "+sdkState.nextAction);healthConnectState.setTextColor(CortexUi.ORANGE);return;}
        healthConnectState.setText("AVAILABLE · checking read scopes…");healthConnectState.setTextColor(CortexUi.MUTED);
        HealthConnectBridge.permissionStatus(this,(granted,total,error)->{if(isFinishing()||isDestroyed())return;if(error!=null){healthReady=false;syncButton.setEnabled(false);healthConnectState.setText("AVAILABLE · permission check failed: "+error);healthConnectState.setTextColor(CortexUi.ORANGE);}else{healthReady=granted==total;syncButton.setEnabled(healthReady);healthConnectState.setText(healthReady?"READY · "+granted+"/"+total+" read scopes granted":"NEEDS ACCESS · "+granted+"/"+total+" read scopes granted · grant access before sync");healthConnectState.setTextColor(healthReady?CortexUi.GREEN:CortexUi.ORANGE);}});
    }

    void requestHealthPermissions(){try{startActivityForResult(HealthConnectBridge.permissionIntent(this),REQ_HEALTH_CONNECT);}catch(Throwable e){Toast.makeText(this,"Could not open Health Connect permissions",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_HEALTH_CONNECT)refresh();}
    void syncHealth(){
        if(!healthReady){CortexHaptics.reject(syncButton);Toast.makeText(this,"Grant all Health Connect read scopes before syncing.",Toast.LENGTH_LONG).show();refresh();return;}
        healthReady=false;syncButton.setEnabled(false);syncButton.setText("Syncing health data…");final long token=CortexSemanticOperation.begin("HEALTH_SYNC","Health Connect 30-day sync");healthSyncToken=token;CortexSemanticOperation.progress(token,"Reading Health Connect",15,"Read scopes confirmed; sync started");
        try{HealthConnectBridge.syncRecentDetailed(this,30,result->{String terminal="state="+result.state+" · seen="+result.seen+" · added="+result.added+(result.failureKind.isEmpty()?"":" · "+result.failureKind);if(result.success())CortexSemanticOperation.complete(token,"HEALTH_SYNC_READY · "+terminal);else CortexSemanticOperation.fail(token,"HEALTH_SYNC_FAILED · "+terminal+(result.nextAction.isEmpty()?"":" · "+result.nextAction));if(healthSyncToken==token)healthSyncToken=0;if(isFinishing()||isDestroyed())return;syncButton.setText("Sync last 30 days");if(result.success()){CortexHaptics.confirm(syncButton);Toast.makeText(this,"Health sync complete · "+result.added+" new / "+result.seen+" seen",Toast.LENGTH_LONG).show();}else{CortexHaptics.reject(syncButton);String msg="Health sync stopped · "+(result.failureKind.isEmpty()?result.state:result.failureKind)+(result.nextAction.isEmpty()?"":"\n"+result.nextAction);if(result.seen>0)msg+="\nPartial read: "+result.seen+" seen / "+result.added+" added; run not marked successful.";Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}refresh();});}
        catch(Throwable e){CortexSemanticOperation.fail(token,"HEALTH_SYNC_FAILED · "+errorText(e));if(healthSyncToken==token)healthSyncToken=0;syncButton.setText("Sync last 30 days");Toast.makeText(this,"Health sync could not start",Toast.LENGTH_LONG).show();refresh();}
    }
    void launchHealthCapture(String mode){Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);i.putExtra("health_context",true);startActivity(i);}
    boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}catch(Throwable e){return false;}}
    void openPackage(String pkg,String missing){try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);return;}}catch(Throwable ignored){}Toast.makeText(this,missing,Toast.LENGTH_LONG).show();}
    static String errorText(Throwable e){if(e==null)return"unknown error";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
    String friendlyAge(long at){long d=Math.max(0,System.currentTimeMillis()-at);if(d<60_000)return"just now";if(d<60L*60L*1000L)return(d/60_000)+"m ago";if(d<24L*60L*60L*1000L)return(d/(60L*60L*1000L))+"h ago";return(d/(24L*60L*60L*1000L))+"d ago";}
    LinearLayout.LayoutParams buttonParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));p.setMargins(0,dp(7),0,0);return p;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
