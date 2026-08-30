package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Capture history is a provenance surface, deliberately separate from durable Memory. */
public final class CaptureHistoryActivity extends Activity {
    private static final int BG=Color.rgb(8,10,8),SURFACE=Color.rgb(24,28,24),BORDER=Color.rgb(55,62,55),TEXT=Color.rgb(244,246,242),MUTED=Color.rgb(164,171,163),FAINT=Color.rgb(112,120,112),BRAND=Color.rgb(143,226,67),BLUE=Color.rgb(75,158,255),AMBER=Color.rgb(238,174,60);
    private CortexDb db;private LinearLayout page;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);db=new CortexDb(this);ScrollView s=new ScrollView(this);s.setBackgroundColor(BG);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(18),dp(20),dp(30));s.addView(page,new ScrollView.LayoutParams(-1,-2));setContentView(s);render();}
    @Override protected void onResume(){super.onResume();if(page!=null)render();}@Override protected void onDestroy(){try{db.close();}catch(Throwable ignored){}super.onDestroy();}
    private void render(){page.removeAllViews();LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",34,MUTED,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));top.addView(text("Capture history",24,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));page.addView(top);TextView p=text("Raw captures and their analysis status live here. This is not Memory.",13,MUTED,false);p.setPadding(0,dp(8),0,dp(14));page.addView(p);
        List<CaptureRecordStore.Record> rows=CaptureRecordStore.recent(db,60);if(rows.isEmpty()){page.addView(card("No captures yet","Voice, photo and file evidence will appear here without being promoted automatically.",FAINT),margin(dp(8)));return;}
        for(CaptureRecordStore.Record r:rows){String title=r.displayName.isEmpty()?r.kind:r.displayName;String body=r.isImage()?CaptureRecordStore.visionSummary(r):r.body;if(body.isEmpty())body="No analysis yet";LinearLayout c=card(title,clip(body,180),r.state.contains("failed")?AMBER:r.isImage()?BLUE:BRAND);if(r.isImage()){c.setClickable(true);c.setOnClickListener(v->{Intent i=new Intent(this,CaptureDetailActivity.class);i.putExtra("evidence_id",r.id);startActivity(i);});}TextView meta=text(new SimpleDateFormat("dd MMM · h:mm a",Locale.getDefault()).format(new Date(r.occurredAt))+" · "+r.state,10,FAINT,false);meta.setPadding(0,dp(8),0,0);c.addView(meta);page.addView(c,margin(dp(8)));}
    }
    private LinearLayout card(String title,String body,int accent){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(round(SURFACE,BORDER,18,1));c.addView(text(title,16,TEXT,true));TextView b=text(body,13,MUTED,false);b.setLineSpacing(0,1.15f);b.setPadding(0,dp(6),0,0);c.addView(b);TextView a=text("● "+(accent==BLUE?"PHOTO":accent==AMBER?"NEEDS RETRY":"CAPTURE"),9,accent,true);a.setPadding(0,dp(8),0,0);c.addView(a);return c;}
    private LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,top,0,0);return p;}private TextView text(String v,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.create("sans",Typeface.BOLD));return t;}private GradientDrawable round(int fill,int stroke,int radius,int width){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(width>0)d.setStroke(dp(width),stroke);return d;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
