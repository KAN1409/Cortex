package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Compact capture action sheet. Capture is an action, not a primary destination. */
public class InputActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Window w=getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(CortexUi.BG);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=.58f;w.setAttributes(lp);
        build();
    }

    @Override protected void onPostResume(){super.onPostResume();StartupMaintenance.schedule(this);}

    void build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setOnClickListener(v->finish());
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(12),dp(18),dp(22));panel.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,28));panel.setOnClickListener(v->{});CortexUi.raised(this,panel,12);

        View handle=new View(this);handle.setBackground(CortexUi.round(this,CortexUi.FAINT,Color.TRANSPARENT,999));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(42),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.setMargins(0,0,0,dp(14));panel.addView(handle,hp);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(CortexUi.glyph(this,"input",CortexUi.ORANGE,true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(8),0);head.addView(tx,xp);
        TextView title=CortexUi.plain(this,"Capture",20,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);TextView sub=CortexUi.text(this,"Add something to Cortex",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);tx.addView(sub);
        TextView close=CortexUi.plain(this,"Close",11,CortexUi.MUTED);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->finish());head.addView(close,new LinearLayout.LayoutParams(dp(54),dp(42)));panel.addView(head);

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams r1p=new LinearLayout.LayoutParams(-1,dp(118));r1p.setMargins(0,dp(16),0,0);panel.addView(row1,r1p);
        addCaptureTile(row1,"Voice","Speak naturally","voice","wave",CortexUi.RED,0);
        addCaptureTile(row1,"Text","Type or paste","text","text",CortexUi.YELLOW,8);

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams r2p=new LinearLayout.LayoutParams(-1,dp(118));r2p.setMargins(0,dp(8),0,0);panel.addView(row2,r2p);
        addCaptureTile(row2,"Photo","Camera or image","photo","photo",CortexUi.GREEN,0);
        addCaptureTile(row2,"File","Document or audio","file","file",CortexUi.ORANGE,8);

        boolean screenReady=CortexScreenAccessibilityService.connected();
        panel.addView(actionRow("Understand this screen",screenReady?"Ready from Quick Settings":"Enable screen understanding","open",screenReady?CortexUi.GREEN:CortexUi.ORANGE,v->openScreenSetup(screenReady)),margins(0,12,0,0));
        panel.addView(actionRow("Share to Cortex","From any app, use Android Share → Cortex","nodes",CortexUi.YELLOW,v->shareHelp()),margins(0,8,0,0));

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);pp.setMargins(dp(8),0,dp(8),dp(8));root.addView(panel,pp);setContentView(root);
    }

    void addCaptureTile(LinearLayout row,String title,String sub,String mode,String icon,int color,int left){
        LinearLayout tile=CortexUi.card(this,19);tile.setPadding(dp(12),dp(11),dp(12),dp(10));CortexUi.pressable(this,tile,CortexUi.velvet(this,19));
        tile.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView t=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(7),0,0);tile.addView(t);
        TextView s=CortexUi.plain(this,sub,9,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);tile.addView(s);
        tile.setOnClickListener(v->capture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);
    }

    LinearLayout actionRow(String title,String sub,String icon,int color,View.OnClickListener click){
        LinearLayout row=CortexUi.card(this,18);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(12),dp(9));CortexUi.pressable(this,row,CortexUi.velvet(this,18));
        row.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,0,0);row.addView(tx,xp);
        TextView h=CortexUi.plain(this,title,13,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.plain(this,sub,9,CortexUi.MUTED);b.setPadding(0,dp(3),0,0);tx.addView(b);row.setOnClickListener(click);return row;
    }

    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);finish();}catch(Throwable ignored){}}

    void openScreenSetup(boolean ready){try{startActivity(new Intent(ready?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}finish();}

    void shareHelp(){new AlertDialog.Builder(this).setTitle("Share to Cortex").setMessage("In any app, tap Share and choose Cortex. Text, links, screenshots, audio and files enter the same Cortex understanding flow.").setPositiveButton("Got it",null).show();}
}
