package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** App-wide conversation-first input surface. Capture is a tool, not a dashboard of giant cards. */
public class InputActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onPostResume(){super.onPostResume();StartupMaintenance.schedule(this);}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(24));sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        body.addView(CortexUi.simpleHeader(this,"Cortex","Input",v->open(SettingsActivity.class)));

        TextView hero=CortexUi.plain(this,"Add something",25,CortexUi.TEXT);CortexUi.medium(hero);hero.setPadding(dp(2),dp(18),0,0);body.addView(hero);
        TextView sub=CortexUi.text(this,"Speak, paste, photograph or share. Cortex keeps the evidence and connects it to what you already know.",13,CortexUi.MUTED);sub.setPadding(dp(2),dp(6),dp(8),dp(18));body.addView(sub);

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        addQuick(row1,"Voice","Speak naturally","wave",CortexUi.RED,"voice",0);
        addQuick(row1,"Text","Paste or type","text",CortexUi.YELLOW,"text",8);
        body.addView(row1,new LinearLayout.LayoutParams(-1,dp(108)));

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        addQuick(row2,"Photo","Camera or gallery","photo",CortexUi.GREEN,"photo",0);
        addQuick(row2,"File","Documents & media","file",CortexUi.ORANGE,"file",8);
        LinearLayout.LayoutParams r2=new LinearLayout.LayoutParams(-1,dp(108));r2.setMargins(0,dp(8),0,0);body.addView(row2,r2);

        TextView anywhere=CortexUi.section(this,"FROM ANYWHERE");anywhere.setPadding(dp(2),dp(24),0,dp(8));body.addView(anywhere);
        boolean screenReady=CortexScreenAccessibilityService.connected();
        body.addView(contextRow("Understand this screen",screenReady?"Ready from Quick Settings":"Enable once to use from any app","open",screenReady?CortexUi.GREEN:CortexUi.ORANGE,v->{
            try{startActivity(new Intent(screenReady?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}
        }));
        body.addView(contextRow("Share to Cortex","Text, links, screenshots, audio and files","nodes",CortexUi.YELLOW,v->new AlertDialog.Builder(this).setTitle("Share to Cortex").setMessage("In any app, tap Share and choose Cortex.").setPositiveButton("Got it",null).show()),gap());
        body.addView(contextRow("Quick voice","Start a voice capture without leaving your flow","wave",CortexUi.RED,v->capture("voice")),gap());

        CortexUi.addBottomNav(this,root,"input",null);setContentView(root);
    }

    void addQuick(LinearLayout row,String title,String description,String icon,int color,String mode,int left){
        LinearLayout tile=CortexUi.card(this,16);tile.setPadding(dp(13),dp(11),dp(13),dp(10));CortexUi.pressable(this,tile,CortexUi.velvet(this,16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(38),1);tp.setMargins(dp(9),0,0,0);top.addView(t,tp);tile.addView(top);
        TextView d=CortexUi.plain(this,description,10,CortexUi.MUTED);d.setPadding(dp(1),dp(6),0,0);tile.addView(d);tile.setOnClickListener(v->capture(mode));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);
    }

    LinearLayout contextRow(String title,String detail,String icon,int color,View.OnClickListener listener){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(11),dp(9),dp(9),dp(9));CortexUi.pressable(this,row,CortexUi.round(this,CortexUi.SURFACE,Color.TRANSPARENT,15));
        row.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(6),0);row.addView(tx,xp);
        TextView h=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView d=CortexUi.plain(this,detail,10,CortexUi.MUTED);d.setPadding(0,dp(3),0,0);tx.addView(d);
        TextView arrow=CortexUi.plain(this,"›",24,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(36)));row.setOnClickListener(listener);return row;
    }

    LinearLayout.LayoutParams gap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,0);return p;}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
}
