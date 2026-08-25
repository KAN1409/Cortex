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

/**
 * First-run access walkthrough. It intentionally consumes the same AccessGateRegistry used by
 * Cortex Access Center so setup, status and later recovery cannot drift apart.
 */
public final class AccessOnboardingActivity extends Activity {
    public static final String PREFS="cortex_access_onboarding";
    public static final String KEY_SEEN="access_onboarding_seen_v1";
    static final int REQ_MIC=901,REQ_POST=902,REQ_CONTACTS=903,REQ_CALENDAR=904;
    static final String[] ORDER={"microphone","app_notifications","notification_listener","accessibility","usage","contacts","calendar","battery"};

    final Set<String> skipped=new HashSet<>();
    LinearLayout body;
    TextView progress,title,why,state,primary,skip,hint;
    AccessGateRegistry.Gate current;
    boolean externalRoundTrip=false;
    boolean launchAttempted=false;

    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
        renderNext();
    }

    @Override protected void onResume(){
        super.onResume();
        if(externalRoundTrip){
            externalRoundTrip=false;
            AccessGateRegistry.Gate refreshed=find(current==null?null:current.key);
            if(refreshed!=null&&refreshed.active){
                current=null;
                renderNext();
                return;
            }
            if(refreshed!=null){
                current=refreshed;
                hint.setText("Android still reports this access as off. Enable it in the screen Cortex opened, then come back — or choose Not now.");
            }
        }
        renderCurrent();
    }

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(18),dp(20),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,android.graphics.Color.TRANSPARENT,999));brand.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);if(Build.VERSION.SDK_INT>=21)cortex.setLetterSpacing(.18f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(42));cp.setMargins(dp(12),0,0,0);brand.addView(cortex,cp);
        TextView label=CortexUi.plain(this,"FIRST-RUN ACCESS",10,CortexUi.MUTED);if(Build.VERSION.SDK_INT>=21)label.setLetterSpacing(.09f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(14),0,0,0);brand.addView(label,lp);body.addView(brand);

        TextView intro=CortexUi.plain(this,"Set Cortex up once",26,CortexUi.TEXT);CortexUi.medium(intro);intro.setPadding(0,dp(16),0,0);body.addView(intro);
        TextView intro2=CortexUi.text(this,"Cortex will take each Android permission or special-access gate one at a time. For system-only gates, it opens the exact Android settings area and checks the result when you return.",12,CortexUi.MUTED);intro2.setPadding(0,dp(7),0,dp(16));body.addView(intro2);

        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(15),dp(14),dp(15),dp(15));body.addView(card);
        progress=CortexUi.plain(this,"",10,CortexUi.ORANGE);CortexUi.medium(progress);if(Build.VERSION.SDK_INT>=21)progress.setLetterSpacing(.07f);card.addView(progress);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(12),0,0);
        head.addView(CortexUi.glyph(this,"settings",CortexUi.ORANGE,true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);head.addView(tx,tp);
        title=CortexUi.plain(this,"",19,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        state=CortexUi.plain(this,"",10,CortexUi.ORANGE);CortexUi.medium(state);state.setPadding(0,dp(4),0,0);tx.addView(state);card.addView(head);

        why=CortexUi.text(this,"",12,CortexUi.MUTED);why.setPadding(0,dp(13),0,0);card.addView(why);
        hint=CortexUi.text(this,"",10,CortexUi.FAINT);hint.setPadding(0,dp(10),0,0);card.addView(hint);

        primary=CortexUi.action(this,"Continue",CortexUi.RED,false);primary.setOnClickListener(v->actCurrent());LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(46));pp.setMargins(0,dp(16),0,0);card.addView(primary,pp);
        skip=CortexUi.action(this,"Not now",CortexUi.MUTED,false);skip.setOnClickListener(v->skipCurrent());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(43));sp.setMargins(0,dp(8),0,0);card.addView(skip,sp);

        LinearLayout note=CortexUi.card(this,17);note.setPadding(dp(13),dp(12),dp(13),dp(12));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.setMargins(0,dp(12),0,0);body.addView(note,np);
        note.addView(CortexUi.text(this,"No broad storage permission is requested. Files and photos use Android's picker. Shizuku and Health Connect remain separate optional setup because standard Cortex awareness does not depend on them.",10,CortexUi.MUTED));

        TextView access=CortexUi.action(this,"Open full Access Center",CortexUi.GREEN,false);access.setOnClickListener(v->startActivity(new Intent(this,PhoneContextAccessActivity.class)));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(44));ap.setMargins(0,dp(12),0,0);body.addView(access,ap);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void renderNext(){
        current=null;
        for(String key:ORDER){
            if(skipped.contains(key))continue;
            AccessGateRegistry.Gate g=find(key);
            if(g!=null&&!g.active){current=g;break;}
        }
        if(current==null){finishSetup();return;}
        renderCurrent();
    }

    void renderCurrent(){
        if(current==null||title==null)return;
        AccessGateRegistry.Gate latest=find(current.key);if(latest!=null)current=latest;
        int done=0,total=0;for(String key:ORDER){AccessGateRegistry.Gate g=find(key);if(g==null)continue;total++;if(g.active||skipped.contains(key))done++;}
        progress.setText("STEP "+Math.min(done+1,total)+" OF "+total+(current.recommended?"  ·  RECOMMENDED":"  ·  OPTIONAL"));
        title.setText(current.title);why.setText(current.why);state.setText(current.status);state.setTextColor(current.active?CortexUi.GREEN:current.recommended?CortexUi.RED:CortexUi.ORANGE);
        hint.setText(instruction(current));
        primary.setText(actionLabel(current));primary.setTextColor(CortexUi.TEXT);
        skip.setText(current.recommended?"Not now":"Skip optional access");
        if(current.active){current=null;renderNext();}
    }

    void actCurrent(){
        if(current==null)return;CortexHaptics.press(primary);
        switch(current.key){
            case "microphone":runtime(Manifest.permission.RECORD_AUDIO,REQ_MIC);break;
            case "app_notifications":
                if(Build.VERSION.SDK_INT>=33&&!AccessGateRegistry.granted(this,Manifest.permission.POST_NOTIFICATIONS))runtime(Manifest.permission.POST_NOTIFICATIONS,REQ_POST);
                else openAppNotificationSettings();
                break;
            case "notification_listener":openExternal(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);break;
            case "accessibility":openExternal(Settings.ACTION_ACCESSIBILITY_SETTINGS);break;
            case "usage":externalRoundTrip=true;PhoneUsageAccess.openSettings(this);break;
            case "contacts":runtime(Manifest.permission.READ_CONTACTS,REQ_CONTACTS);break;
            case "calendar":runtime(Manifest.permission.READ_CALENDAR,REQ_CALENDAR);break;
            case "battery":openExternal(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);break;
            default:skipCurrent();
        }
    }

    void runtime(String permission,int code){
        if(Build.VERSION.SDK_INT<23||checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED){current=null;renderNext();return;}
        requestPermissions(new String[]{permission},code);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        boolean ok=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this,ok?"Access granted":"Access not granted",Toast.LENGTH_SHORT).show();
        if(ok){current=null;renderNext();}else renderCurrent();
    }

    void skipCurrent(){if(current==null)return;skipped.add(current.key);current=null;renderNext();}

    void finishSetup(){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_SEEN,true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Cortex access setup complete")
                .setMessage("The first-run walkthrough is done. Any access you skipped stays visible in Access Center and can be enabled later.")
                .setCancelable(false)
                .setPositiveButton("Start Cortex",(d,w)->finish())
                .show();
    }

    @Override public void onBackPressed(){
        new AlertDialog.Builder(this)
                .setTitle("Finish setup later?")
                .setMessage("Cortex can run with missing optional access. Recommended gates you leave off will reduce phone awareness until you enable them in Access Center.")
                .setNegativeButton("Keep setting up",null)
                .setPositiveButton("Finish later",(d,w)->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_SEEN,true).apply();finish();})
                .show();
    }

    AccessGateRegistry.Gate find(String key){if(key==null)return null;for(AccessGateRegistry.Gate g:AccessGateRegistry.snapshot(this))if(key.equals(g.key))return g;return null;}

    String actionLabel(AccessGateRegistry.Gate g){
        if("microphone".equals(g.key)||"contacts".equals(g.key)||"calendar".equals(g.key))return"Grant permission";
        if("app_notifications".equals(g.key)&&Build.VERSION.SDK_INT>=33&&!AccessGateRegistry.granted(this,Manifest.permission.POST_NOTIFICATIONS))return"Allow Cortex notifications";
        if("notification_listener".equals(g.key))return"Open notification access";
        if("accessibility".equals(g.key))return"Open Accessibility";
        if("usage".equals(g.key))return"Open Usage Access";
        if("battery".equals(g.key))return"Open battery optimization";
        return"Open Android settings";
    }

    String instruction(AccessGateRegistry.Gate g){
        if("notification_listener".equals(g.key))return"Android will open Notification access. Turn on Cortex, accept Android's warning if shown, then return here.";
        if("accessibility".equals(g.key))return"Android will open Accessibility. Open Cortex Screen Understanding, switch it on, then return here.";
        if("usage".equals(g.key))return"Android will open Usage Access. Find Cortex and allow usage access, then return here.";
        if("battery".equals(g.key))return"Optional: open Battery optimization and make Cortex unrestricted / not optimized if this phone aggressively stops background work.";
        if("app_notifications".equals(g.key))return"Allow Cortex to show foreground recording status and useful alerts. If Android runtime permission is already granted, this opens Cortex notification settings.";
        return g.recommended?"Android will show the standard permission prompt. Cortex checks the result before moving to the next step.":"This is optional. Grant it now for richer grounded context, or skip it and enable it later in Access Center.";
    }

    void openExternal(String action){try{externalRoundTrip=true;startActivity(new Intent(action));}catch(Throwable e){externalRoundTrip=false;Toast.makeText(this,"Android settings could not be opened on this device",Toast.LENGTH_LONG).show();}}
    void openAppNotificationSettings(){try{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());externalRoundTrip=true;startActivity(i);}catch(Throwable e){try{Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));externalRoundTrip=true;startActivity(i);}catch(Throwable ignored){externalRoundTrip=false;Toast.makeText(this,"Android notification settings could not be opened",Toast.LENGTH_LONG).show();}}}
}
