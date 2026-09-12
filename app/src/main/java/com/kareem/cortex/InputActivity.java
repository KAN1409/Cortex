package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Premium capture entry surface. */
public class InputActivity extends Activity {
    boolean crashPromptChecked=false;
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onPostResume(){
        super.onPostResume();
        if(!StartupSafetyGate.active()){
            StartupMaintenance.schedule(this);
            UniversalSemanticScheduler.kick(this);
        }
        if(!isFinishing())build();
        maybeOfferCrashReport();
    }

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header());

        LinearLayout hero=CortexUi.card(this,26);hero.setPadding(dp(18),dp(17),dp(18),dp(17));
        LinearLayout heroTop=new LinearLayout(this);heroTop.setGravity(Gravity.CENTER_VERTICAL);TextView eye=CortexUi.eyebrow(this,"New capture",CortexUi.ORANGE);heroTop.addView(eye,new LinearLayout.LayoutParams(0,-2,1));TextView safe=CortexUi.chip(this,"Evidence first",CortexUi.GREEN,false);heroTop.addView(safe,new LinearLayout.LayoutParams(-2,dp(29)));hero.addView(heroTop);
        TextView hh=CortexUi.plain(this,"Bring anything into Cortex",23,CortexUi.TEXT);CortexUi.medium(hh);hh.setPadding(0,dp(11),0,0);hero.addView(hh);
        TextView hb=CortexUi.text(this,"Speak, paste, photograph or attach a file. Cortex keeps the source separate from what it understands and what it later suggests.",12,CortexUi.MUTED);hb.setPadding(0,dp(6),0,0);hero.addView(hb);
        body.addView(hero,margins(0,5,0,0));

        body.addView(CortexUi.section(this,"Choose input"));
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        addTile(row1,"Voice","Talk naturally","voice","wave",CortexUi.RED,0);addTile(row1,"Text","Paste or write","text","text",CortexUi.YELLOW,8);body.addView(row1,new LinearLayout.LayoutParams(-1,dp(136)));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        addTile(row2,"Photo","Camera or image","photo","photo",CortexUi.GREEN,0);addTile(row2,"File","Documents & audio","file","file",CortexUi.ORANGE,8);LinearLayout.LayoutParams r2=new LinearLayout.LayoutParams(-1,dp(136));r2.setMargins(0,dp(8),0,0);body.addView(row2,r2);

        body.addView(CortexUi.section(this,"Cortex everywhere"));
        boolean screenEnabled=CortexScreenAccessibilityService.enabled(this),screenConnected=CortexScreenAccessibilityService.connected();
        String screenState=screenEnabled?(screenConnected?"Connected · understand the current screen on demand":"Enabled · reconnects automatically"):"One-time setup required in Android Accessibility";
        body.addView(featureCard("Understand this screen",screenState,"open",screenEnabled?CortexUi.GREEN:CortexUi.ORANGE,screenEnabled?"Open Quick Settings":"Enable screen understanding",v->{try{startActivity(new Intent(screenEnabled?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}}),margins(0,0,0,0));
        body.addView(featureCard("Share to Cortex","From any app, Android Share can send text, links, screenshots, audio or files into the same grounded understanding flow.","nodes",CortexUi.LIME,"How sharing works",v->new AlertDialog.Builder(this).setTitle("Share to Cortex").setMessage("In any app, tap Share and choose Cortex. Cortex imports the original item safely, analyzes it, then opens the grounded result with model-generated next proposals.").setPositiveButton("Got it",null).show()),margins(0,9,0,0));
        body.addView(featureCard("Quick voice","Start one-tap voice capture now, or use the Cortex Voice Quick Settings tile from any app.","wave",CortexUi.RED,"Start quick voice",v->capture("voice")),margins(0,9,0,0));

        CortexUi.addBottomNav(this,root,"input",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void maybeOfferCrashReport(){
        if(crashPromptChecked||isFinishing())return;crashPromptChecked=true;
        final String crash=CrashRecorder.read(this,60000);if(crash.trim().isEmpty())return;
        final String hash=Fingerprint.text(crash);final android.content.SharedPreferences p=getSharedPreferences("cortex_crash_ui",MODE_PRIVATE);if(hash.equals(p.getString("prompted_hash","")))return;
        p.edit().putString("prompted_hash",hash).apply();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Cortex captured the last crash").setMessage("The full Java crash report is stored locally. Open it now and share it for diagnosis — no Wi-Fi, ADB or Shizuku needed.").setNegativeButton("Later",null).setPositiveButton("Open report",(d,w)->open(CrashReportActivity.class)).show();},350);
    }

    View header(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(10));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(CortexUi.eyebrow(this,"Cortex",CortexUi.ORANGE));TextView h=CortexUi.plain(this,"Capture",32,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(1),0,0);titles.addView(h);
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));row.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));return row;
    }

    void addTile(LinearLayout row,String title,String description,String mode,String icon,int color,int left){
        LinearLayout tile=CortexUi.card(this,22);tile.setPadding(dp(14),dp(13),dp(14),dp(12));CortexUi.pressable(this,tile,CortexUi.velvet(this,22));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView arrow=CortexUi.plain(this,"›",23,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);top.addView(arrow,new LinearLayout.LayoutParams(0,dp(44),1));tile.addView(top);
        TextView t=CortexUi.plain(this,title,16,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(8),0,0);tile.addView(t);TextView d=CortexUi.text(this,description,10,CortexUi.MUTED);d.setPadding(0,dp(3),0,0);tile.addView(d);tile.setOnClickListener(v->capture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);
    }

    LinearLayout featureCard(String title,String body,String icon,int color,String action,View.OnClickListener listener){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(14),dp(13),dp(14),dp(13));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);top.addView(tx,xp);TextView h=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.text(this,body,11,CortexUi.MUTED);b.setPadding(0,dp(4),0,0);tx.addView(b);card.addView(top);
        TextView button=CortexUi.action(this,action,color,false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(42));bp.setMargins(0,dp(11),0,0);card.addView(button,bp);button.setOnClickListener(listener);return card;
    }

    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
}
