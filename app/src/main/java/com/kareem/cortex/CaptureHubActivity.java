package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

/** Capture product surface: libraries first, observability pipeline one tap away. */
public final class CaptureHubActivity extends Activity {
    private VaultDb db; private int dp(int v){return CortexUi.dp(this,v);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();}
    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));tx.addView(CortexUi.eyebrow(this,"CORTEX · EVIDENCE LIBRARIES",CortexUi.ORANGE));TextView h=CortexUi.plain(this,"Capture",34,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView pipeline=CortexUi.chip(this,"Pipeline",CortexUi.MUTED,false);pipeline.setOnClickListener(v->open(CaptureOverviewActivity.class));head.addView(pipeline,new LinearLayout.LayoutParams(-2,dp(36)));body.addView(head);
        TextView sub=CortexUi.text(this,"Your captured voice, images and files stay browsable. The pipeline view still shows what Cortex observed, understood and promoted.",11,CortexUi.MUTED);sub.setPadding(dp(1),dp(4),dp(1),dp(12));body.addView(sub);

        long voice=0,images=0,files=0;try{ArrayList<KnowledgeItem> items=db.captureSearch("",2000);for(KnowledgeItem k:items){if(k==null)continue;if("AUDIO".equalsIgnoreCase(k.type))voice++;else if("IMAGE".equalsIgnoreCase(k.type)||"SCREENSHOT".equalsIgnoreCase(k.type))images++;else if("FILE".equalsIgnoreCase(k.type)||"DOCUMENT".equalsIgnoreCase(k.type)||"PDF".equalsIgnoreCase(k.type))files++;}}catch(Throwable ignored){}
        LinearLayout hero=CortexUi.card(this,26);hero.addView(CortexUi.eyebrow(this,"CAPTURE MEMORY",CortexUi.ORANGE));TextView hh=CortexUi.plain(this,"Evidence stays available",23,CortexUi.TEXT);CortexUi.medium(hh);hh.setPadding(0,dp(10),0,0);hero.addView(hh);TextView hb=CortexUi.text(this,"Libraries are separate from judgment: captured source material remains inspectable even when Cortex decides it should not appear in Now.",11,CortexUi.MUTED);hb.setPadding(0,dp(5),0,0);hero.addView(hb);body.addView(hero);

        body.addView(CortexUi.section(this,"Libraries"));
        body.addView(library("Voice library",voice+" recordings / audio items","voice",CortexUi.ORANGE,()->open(VoiceLibraryActivity.class)),lp(0,0,0,8));
        body.addView(library("Image library",images+" captured images · visual memory has its own full index","photo",CortexUi.GREEN,()->open(VisualMemoryActivity.class)),lp(0,0,0,8));
        body.addView(library("Files",files+" imported documents","file",CortexUi.OLIVE,()->open(VaultActivity.class)),lp(0,0,0,8));

        body.addView(CortexUi.section(this,"Observe & debug"));
        body.addView(library("Source → understood → shown","Open the live pipeline and inspect each stage","nodes",CortexUi.LIME,()->open(CaptureOverviewActivity.class)),lp(0,0,0,8));
        TextView newCapture=CortexUi.action(this,"CAPTURE SOMETHING NEW",CortexUi.LIME,true);newCapture.setOnClickListener(v->CortexNavigation.openInput(this));body.addView(newCapture,new LinearLayout.LayoutParams(-1,dp(46)));
        CortexUi.addBottomNav(this,root,"capture",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private LinearLayout library(String title,String detail,String icon,int color,Runnable go){LinearLayout card=CortexUi.card(this,22);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(13),dp(12),dp(12),dp(12));card.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(6),0);card.addView(tx,xp);TextView h=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView d=CortexUi.text(this,detail,10,CortexUi.MUTED);d.setPadding(0,dp(4),0,0);tx.addView(d);TextView arrow=CortexUi.plain(this,"›",24,color);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(42)));CortexUi.pressable(this,card,CortexUi.velvet(this,22));card.setOnClickListener(v->go.run());return card;}
    private void open(Class<?> c){try{startActivity(new Intent(this,c));}catch(Throwable ignored){}}
    private LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
