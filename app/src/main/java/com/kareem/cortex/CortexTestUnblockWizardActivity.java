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

/** Interactive setup bridge for tests Android/provider policy prevents from running automatically. */
public final class CortexTestUnblockWizardActivity extends Activity {
    private static final int REQ_PERMS=4201,REQ_TREE=4202;
    LinearLayout body;TextView summary;int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(32));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());h.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout hs=new LinearLayout(this);hs.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"Unblock exhaustive tests",25,CortexUi.TEXT);CortexUi.medium(title);hs.addView(title);hs.addView(CortexUi.text(this,"Grant or configure only what you want. Return here after each Android screen; status refreshes automatically.",11,CortexUi.MUTED));h.addView(hs,new LinearLayout.LayoutParams(0,-2,1));body.addView(h);
        summary=CortexUi.text(this,"Checking blockers…",12,CortexUi.MUTED);summary.setPadding(0,dp(14),0,dp(14));body.addView(summary);
        setContentView(root);CortexUi.fitSystemBars(this,root);refresh();
    }

    void refresh(){if(body==null)return;while(body.getChildCount()>2)body.removeViewAt(2);ArrayList<String> blocked=new ArrayList<>();
        boolean mic=granted(Manifest.permission.RECORD_AUDIO),cal=granted(Manifest.permission.READ_CALENDAR),contacts=granted(Manifest.permission.READ_CONTACTS),post=Build.VERSION.SDK_INT<33||granted(Manifest.permission.POST_NOTIFICATIONS);
        addRow("Runtime permissions",(mic&&cal&&contacts&&post)?"READY":"NEEDS ACCESS","Microphone "+yn(mic)+" · Calendar "+yn(cal)+" · Contacts "+yn(contacts)+" · Notifications "+yn(post),"Grant runtime permissions",v->requestStandardPermissions());if(!(mic&&cal&&contacts&&post))blocked.add("runtime permissions");
        boolean nl=CortexAuditSoakWorker.notificationListenerEnabled(this);addRow("Notification Access",nl?"READY":"NEEDS ACCESS",nl?"Notification Listener enabled":"Required for real notification-capture test","Open Notification Access",v->openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));if(!nl)blocked.add("notification access");
        boolean usage=PhoneUsageAccess.has(this);addRow("Usage Access",usage?"READY":"NEEDS ACCESS",usage?"Usage Access enabled":"Required for real foreground/app usage context","Open Usage Access",v->openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS));if(!usage)blocked.add("usage access");
        boolean acc=CortexScreenAccessibilityService.connected();addRow("Screen Accessibility",acc?"READY":"NEEDS ACCESS",acc?"Cortex screen context connected":"Enable Cortex in Android Accessibility","Open Accessibility",v->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));if(!acc)blocked.add("accessibility");
        boolean sa=ShizukuContextBridge.available(),sg=ShizukuContextBridge.granted();addRow("Shizuku",sg?"READY":sa?"NEEDS ACCESS":"NEEDS SETUP",ShizukuContextBridge.status(),sg?"Refresh":sa?"Request Cortex permission":"Open Phone Context help",v->{if(sa)ShizukuContextBridge.requestPermission();else open(PhoneContextAccessActivity.class);});if(!sg)blocked.add("Shizuku");
        Uri tree=ScreenshotIngestor.tree(this);addRow("Screenshot folder",tree!=null?"READY":"NEEDS SETUP",tree==null?"Select the Samsung screenshot folder":"Connected · "+ScreenshotIngestor.treeLabel(this),"Choose folder",v->chooseScreenshotFolder());if(tree==null)blocked.add("screenshot folder");
        boolean gem=GeminiKeyStore.has(this),groq=GroqKeyStore.has(this);addRow("ASR providers",gem||groq?"READY":"NEEDS SETUP","Gemini "+yn(gem)+" · Groq "+yn(groq),"Configure ASR",v->open(AsrSettingsActivity.class));if(!gem&&!groq)blocked.add("ASR provider");
        addRow("Strong Vision",gem?"PROVIDER READY":"NEEDS SETUP",gem?"Gemini credential exists; image privacy still controls live use":"Gemini is required for strong cloud vision","Configure / check model",v->open(ExternalModelCheckActivity.class));if(!gem)blocked.add("strong vision");
        boolean local=LocalModelManager.verified(this);addRow("Local Qwen",local?"READY":"NEEDS SETUP",local?"Local model verified":"Install and verify the local model","Open Local Model setup",v->open(EnvironmentActivity.class));if(!local)blocked.add("Local Qwen");
        addRow("Calendar / Contacts / Backup integrations","OPEN SETUP","Use the feature hub to configure privacy and integration behavior.","Open integrations",v->open(FeatureHubActivity.class));
        addRow("30-minute real stability soak","PROTECTED","Long-duration behavior test runs separately so it cannot be faked by a short test.","Open capability #33",v->open(CapabilityMatrixActivity.class));
        addRow("Protected live tests","CONFIRMATION REQUIRED","Microphone phrase, notification event, reversible Calendar write, document backup/restore and external draft handoffs require explicit confirmation.","Open protected tests",v->open(CortexProtectedLiveTestsActivity.class));
        addRow("Return to exhaustive Test Lab","RERUN","After unblocking anything, rerun. Only still-blocked rows remain blocked.","Back to Test Lab",v->{startActivity(new Intent(this,UserSimulationTestLabActivity.class));finish();});
        summary.setText(blocked.isEmpty()?"All setup/access blockers currently visible to Cortex are cleared. Run exhaustive verification again.":blocked.size()+" setup/access blocker(s) remain · "+android.text.TextUtils.join(" · ",blocked));
    }

    void requestStandardPermissions(){ArrayList<String> p=new ArrayList<>();if(!granted(Manifest.permission.RECORD_AUDIO))p.add(Manifest.permission.RECORD_AUDIO);if(!granted(Manifest.permission.READ_CALENDAR))p.add(Manifest.permission.READ_CALENDAR);if(!granted(Manifest.permission.WRITE_CALENDAR))p.add(Manifest.permission.WRITE_CALENDAR);if(!granted(Manifest.permission.READ_CONTACTS))p.add(Manifest.permission.READ_CONTACTS);if(Build.VERSION.SDK_INT>=33&&!granted(Manifest.permission.POST_NOTIFICATIONS))p.add(Manifest.permission.POST_NOTIFICATIONS);if(p.isEmpty()){Toast.makeText(this,"Runtime permissions already granted",Toast.LENGTH_SHORT).show();return;}requestPermissions(p.toArray(new String[0]),REQ_PERMS);}
    void chooseScreenshotFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(i,REQ_TREE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_TREE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}ScreenshotIngestor.saveTree(this,u);Toast.makeText(this,"Screenshot folder connected",Toast.LENGTH_SHORT).show();refresh();}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_PERMS)refresh();}

    void addRow(String title,String state,String detail,String action,View.OnClickListener click){LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(14),dp(12),dp(14),dp(12));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView t=CortexUi.text(this,title,13,CortexUi.TEXT);CortexUi.medium(t);top.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView badge=CortexUi.chip(this,state,state.contains("READY")?CortexUi.SAGE:state.contains("NEEDS")?CortexUi.COPPER:CortexUi.MUTED,true);top.addView(badge);card.addView(top);TextView d=CortexUi.text(this,detail,11,CortexUi.MUTED);d.setPadding(0,dp(6),0,dp(8));card.addView(d);TextView a=CortexUi.action(this,action,CortexUi.ACCENT,true);a.setOnClickListener(click);card.addView(a,new LinearLayout.LayoutParams(-1,dp(44)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));body.addView(card,lp);}
    boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}String yn(boolean b){return b?"✓":"✕";}
    void openSettings(String action){try{startActivity(new Intent(action));}catch(Throwable e){Toast.makeText(this,"Android settings screen unavailable",Toast.LENGTH_SHORT).show();}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable e){Toast.makeText(this,"Could not open Cortex setup surface",Toast.LENGTH_SHORT).show();}}
}
