package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** PRIME input surface: one calm entry point for intentional capture. */
public class InputActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(16),dp(20),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView h=CortexUi.plain(this,"Input",31,CortexUi.TEXT);CortexUi.medium(h);titles.addView(h);TextView sub=CortexUi.text(this,"Capture anything. Cortex will understand where it belongs.",12,CortexUi.MUTED);sub.setPadding(0,dp(4),0,0);titles.addView(sub);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));body.addView(head);

        body.addView(CortexUi.section(this,"Capture"));
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);addTile(row1,"Voice","Speak naturally\nArabic + English","voice",0);addTile(row1,"Text / Paste","Notes, prompts, ideas\nor copied text","text",8);body.addView(row1,new LinearLayout.LayoutParams(-1,dp(132)));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);addTile(row2,"Photo","Take or import\nvisual evidence","photo",0);addTile(row2,"File","Documents, audio\nand attachments","file",8);LinearLayout.LayoutParams r2=new LinearLayout.LayoutParams(-1,dp(132));r2.setMargins(0,dp(8),0,0);body.addView(row2,r2);

        LinearLayout share=CortexUi.card(this,20);share.setPadding(dp(16),dp(15),dp(16),dp(15));TextView st=CortexUi.plain(this,"From another app",14,CortexUi.TEXT);CortexUi.medium(st);share.addView(st);TextView sb=CortexUi.text(this,"Share text, links, screenshots, audio or files to Cortex. Shared items keep their origin metadata when Android provides it.",12,CortexUi.MUTED);sb.setPadding(0,dp(6),0,0);share.addView(sb);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(20),0,0);body.addView(share,sp);

        CortexUi.addBottomNav(this,root,"input",null);setContentView(root);
    }

    void addTile(LinearLayout row,String title,String description,String mode,int left){LinearLayout tile=CortexUi.card(this,22);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(16),dp(14),dp(16),dp(14));CortexUi.pressable(this,tile,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,22));TextView t=CortexUi.plain(this,title,16,CortexUi.TEXT);CortexUi.medium(t);tile.addView(t);TextView d=CortexUi.text(this,description,11,CortexUi.MUTED);d.setPadding(0,dp(6),0,0);tile.addView(d);tile.setOnClickListener(v->capture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);}
    void capture(String mode){Intent i=new Intent(this,CaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}void open(Class<?> cls){startActivity(new Intent(this,cls));}
}
