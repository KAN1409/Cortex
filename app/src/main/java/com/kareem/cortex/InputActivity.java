package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** PRIME input surface using the locked matte warm premium design language. */
public class InputActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();}
    @Override protected void onPostResume(){super.onPostResume();StartupMaintenance.schedule(this);}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(header());

        LinearLayout intro=CortexUi.card(this,22);intro.setPadding(0,0,0,0);LinearLayout core=new LinearLayout(this);core.setGravity(Gravity.CENTER_VERTICAL);core.setPadding(dp(14),dp(14),dp(13),dp(14));View rail=new View(this);rail.setBackground(CortexUi.round(this,CortexUi.ORANGE,Color.TRANSPARENT,999));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(2),dp(54));rp.setMargins(0,0,dp(12),0);core.addView(rail,rp);core.addView(CortexUi.glyph(this,"input",CortexUi.ORANGE,true),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);core.addView(tx,tp);TextView ih=CortexUi.plain(this,"Capture anything",18,CortexUi.TEXT);CortexUi.medium(ih);tx.addView(ih);TextView is=CortexUi.text(this,"Speak, paste, photograph or share. Cortex keeps the evidence and turns the result into useful next proposals.",12,CortexUi.MUTED);is.setPadding(0,dp(5),0,0);tx.addView(is);intro.addView(core);body.addView(intro,margins(0,dp(8),0,0));

        body.addView(section("CAPTURE",CortexUi.ORANGE));LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);addTile(row1,"Voice","Speak naturally\nArabic + English","voice","wave",CortexUi.RED,0);addTile(row1,"Text / Paste","Notes, prompts, ideas\nor copied text","text","text",CortexUi.YELLOW,8);body.addView(row1,new LinearLayout.LayoutParams(-1,dp(150)));LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);addTile(row2,"Photo","Take or import\nvisual evidence","photo","photo",CortexUi.GREEN,0);addTile(row2,"File","Documents, audio\nand attachments","file","file",CortexUi.ORANGE,8);LinearLayout.LayoutParams r2=new LinearLayout.LayoutParams(-1,dp(150));r2.setMargins(0,dp(8),0,0);body.addView(row2,r2);

        body.addView(section("EVERYWHERE CORTEX",CortexUi.GREEN));
        boolean screenReady=CortexScreenAccessibilityService.connected();body.addView(featureCard("Understand this screen",screenReady?"READY · Use the Understand screen Quick Settings tile while any app is visible.":"SETUP NEEDED · Enable screen understanding once, then add the Quick Settings tile.","open",screenReady?CortexUi.GREEN:CortexUi.ORANGE,screenReady?"Open Quick Settings setup":"Enable screen understanding",v->{try{startActivity(new Intent(screenReady?"android.settings.QUICK_SETTINGS_SETTINGS":Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable e){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Throwable ignored){}}}),margins(0,0,0,0));
        body.addView(featureCard("From another app","READY · Android Share → Cortex accepts text, links, screenshots, audio and files and returns to the same understanding flow.","nodes",CortexUi.YELLOW,"Show share instruction",v->new AlertDialog.Builder(this).setTitle("Share to Cortex").setMessage("In any app, tap Share and choose Cortex. Cortex imports the original item safely, analyzes it, then opens the grounded result with model-generated next proposals.").setPositiveButton("Got it",null).show()),margins(0,dp(9),0,0));
        body.addView(featureCard("Quick voice","Start the same one-tap voice capture now, or use the Cortex Voice Quick Settings tile from any app.","wave",CortexUi.RED,"Start quick voice now",v->capture("voice")),margins(0,dp(9),0,0));

        CortexUi.addBottomNav(this,root,"input",null);setContentView(root);
    }

    View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(8),dp(2),dp(11));View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.ORANGE,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));TextView c=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(c,cp);View d=CortexUi.divider(this);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(d,dpv);TextView sys=CortexUi.plain(this,"INPUT",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.ORANGE,false);settings.setOnClickListener(v->open(SettingsActivity.class));row.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));return row;}

    TextView section(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.09f);h.setPadding(dp(1),dp(19),0,dp(8));return h;}

    void addTile(LinearLayout row,String title,String description,String mode,String icon,int color,int left){
        LinearLayout tile=CortexUi.card(this,20);tile.setPadding(dp(13),dp(12),dp(13),dp(12));CortexUi.pressable(this,tile,CortexUi.velvet(this,20));tile.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(46),dp(46)));TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);t.setPadding(0,dp(8),0,0);tile.addView(t);TextView d=CortexUi.text(this,description,10,CortexUi.MUTED);d.setPadding(0,dp(4),0,0);tile.addView(d);tile.setOnClickListener(v->capture(mode));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(left),0,0,0);row.addView(tile,p);
    }

    LinearLayout featureCard(String title,String body,String icon,int color,String action,View.OnClickListener listener){
        LinearLayout card=CortexUi.card(this,20);card.setPadding(0,0,0,0);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(13),dp(13),dp(13),dp(10));top.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);top.addView(tx,xp);TextView h=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.text(this,body,11,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);tx.addView(b);card.addView(top);TextView button=CortexUi.action(this,action,color,false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(43));bp.setMargins(dp(13),0,dp(13),dp(13));card.addView(button,bp);button.setOnClickListener(listener);return card;
    }

    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){}}
    void open(Class<?> cls){try{startActivity(new Intent(this,cls));}catch(Throwable ignored){}}
}
