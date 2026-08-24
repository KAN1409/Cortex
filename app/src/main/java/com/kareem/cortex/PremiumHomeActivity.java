package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** PRIME Brief: a calm read model over unified derived intelligence, never legacy task guesses. */
public class PremiumHomeActivity extends Activity {
    VaultDb db;LinearLayout content;volatile int refreshGeneration=0;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);CognitiveStore.ensure(db);build();refreshAsync();
        new Thread(()->{try{TemporalResolver.backfill(db,250);V41Maintenance.run(this,db);ContactSafetyMaintenance.run(db);}catch(Throwable ignored){}},"CortexMaintenance").start();
    }
    @Override protected void onResume(){super.onResume();if(db!=null)refreshAsync();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        header();
        TextView loading=CortexUi.text(this,"Building your brief…",12,CortexUi.MUTED);loading.setPadding(0,dp(24),0,0);content.addView(loading);
        CortexUi.addBottomNav(this,root,"brief",null);setContentView(root);
    }

    void header(){
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView title=CortexUi.plain(this,"Brief",31,CortexUi.TEXT);CortexUi.medium(title);titles.addView(title);
        TextView subtitle=CortexUi.text(this,"What needs you now, what is waiting, and what changed.",11,CortexUi.MUTED);subtitle.setPadding(0,dp(3),0,0);titles.addView(subtitle);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));content.addView(head);
    }

    void refreshAsync(){
        final int g=++refreshGeneration;
        new Thread(()->{try{PrimeBriefStore.Snapshot s=PrimeBriefStore.load(db);runOnUiThread(()->{if(g!=refreshGeneration||isFinishing())return;render(s);});}
            catch(Throwable e){runOnUiThread(()->{if(g==refreshGeneration&&!isFinishing())renderError();});}},"CortexPrimeBrief").start();
    }

    void render(PrimeBriefStore.Snapshot s){
        while(content.getChildCount()>1)content.removeViewAt(1);
        if(s.empty()){
            LinearLayout empty=CortexUi.card(this,24);empty.setPadding(dp(18),dp(28),dp(18),dp(28));TextView h=CortexUi.plain(this,"Nothing needs you right now",19,CortexUi.TEXT);CortexUi.medium(h);empty.addView(h);
            TextView b=CortexUi.text(this,"Cortex will surface confirmed actions, waiting items, reviews and meaningful changes here — not ordinary notification noise.",12,CortexUi.MUTED);b.setPadding(0,dp(7),0,0);empty.addView(b);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(0,dp(22),0,0);content.addView(empty,ep);return;
        }
        if(!s.actions.isEmpty()){content.addView(CortexUi.section(this,"Needs you"));for(int i=0;i<Math.min(6,s.actions.size());i++)derivedRow(s.actions.get(i));}
        if(!s.waiting.isEmpty()){content.addView(CortexUi.section(this,"Waiting"));for(int i=0;i<Math.min(5,s.waiting.size());i++)derivedRow(s.waiting.get(i));}
        if(!s.reviews.isEmpty()){
            content.addView(CortexUi.section(this,"Needs your review"));
            for(int i=0;i<Math.min(4,s.reviews.size());i++)reviewRow(s.reviews.get(i));
            TextView all=CortexUi.action(this,"Review all ("+s.reviews.size()+")",CortexUi.MUTED,false);all.setOnClickListener(v->open(ReviewQueueActivity.class));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(42));ap.setMargins(0,dp(8),0,0);content.addView(all,ap);
        }
        if(!s.changes.isEmpty()){content.addView(CortexUi.section(this,"Changed & evolving"));for(int i=0;i<Math.min(6,s.changes.size());i++)derivedRow(s.changes.get(i));}
    }

    void derivedRow(PrimeBriefStore.Item x){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(2),dp(13),dp(2),dp(13));CortexUi.pressable(this,row,CortexUi.round(this,Color.TRANSPARENT,Color.TRANSPARENT,12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=CortexUi.text(this,cleanTitle(x.title,x.kind),14,CortexUi.TEXT);CortexUi.medium(title);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView badge=CortexUi.chip(this,friendlyKind(x.kind),kindColor(x.kind),true);top.addView(badge,new LinearLayout.LayoutParams(-2,dp(30)));row.addView(top);
        String preview=cleanBody(x.body);if(!preview.isEmpty()){TextView body=CortexUi.text(this,clip(preview,180),12,CortexUi.MUTED);body.setPadding(0,dp(5),0,0);body.setMaxLines(3);row.addView(body);}
        TextView meta=CortexUi.plain(this,sourceLabel(x.source)+"  •  "+age(x.updatedAt)+(x.importance>0?"  •  priority "+x.importance:""),10,CortexUi.MUTED);meta.setPadding(0,dp(6),0,0);row.addView(meta);
        row.setOnClickListener(v->derivedDetail(x));content.addView(row);content.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
    }

    void reviewRow(ReviewQueueStore.Item x){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(2),dp(13),dp(2),dp(13));CortexUi.pressable(this,row,CortexUi.round(this,Color.TRANSPARENT,Color.TRANSPARENT,12));
        TextView title=CortexUi.text(this,cleanTitle(x.title,x.candidateKind),14,CortexUi.TEXT);CortexUi.medium(title);row.addView(title);
        String body=cleanBody(x.body);if(!body.isEmpty()){TextView b=CortexUi.text(this,clip(body,180),12,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);b.setMaxLines(3);row.addView(b);}
        TextView meta=CortexUi.plain(this,"Possible "+friendlyKind(x.candidateKind)+"  •  "+sourceLabel(x.sourceKey)+"  •  "+age(x.createdAt),10,CortexUi.ACCENT);meta.setPadding(0,dp(6),0,0);row.addView(meta);
        row.setOnClickListener(v->open(ReviewQueueActivity.class));content.addView(row);content.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
    }

    void derivedDetail(PrimeBriefStore.Item x){
        Dialog d=new Dialog(this);LinearLayout c=CortexUi.card(this,25);c.setPadding(dp(18),dp(18),dp(18),dp(18));
        TextView h=CortexUi.text(this,cleanTitle(x.title,x.kind),21,CortexUi.TEXT);CortexUi.medium(h);c.addView(h);
        TextView meta=CortexUi.plain(this,friendlyKind(x.kind)+"  •  "+sourceLabel(x.source)+"  •  "+exactTime(x.updatedAt),10,CortexUi.MUTED);meta.setPadding(0,dp(5),0,dp(12));c.addView(meta);
        if(!cleanBody(x.body).isEmpty()){TextView b=CortexUi.text(this,cleanBody(x.body),13,CortexUi.TEXT);b.setTextIsSelectable(true);c.addView(b);}
        TextView confidence=CortexUi.plain(this,"Confidence "+Math.round(x.confidence*100)+"%  •  priority "+x.importance,10,CortexUi.MUTED);confidence.setPadding(0,dp(12),0,0);c.addView(confidence);
        TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(16),0,0);c.addView(close,cp);close.setOnClickListener(v->d.dismiss());d.setContentView(c);Window w=d.getWindow();if(w!=null)w.setBackgroundDrawableResource(android.R.color.transparent);d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),-2);
    }

    void renderError(){while(content.getChildCount()>1)content.removeViewAt(1);TextView t=CortexUi.text(this,"Brief could not load right now. Your data is still safe.",12,CortexUi.MUTED);t.setPadding(0,dp(24),0,0);content.addView(t);}
    void open(Class<?> c){Intent i=new Intent(this,c);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}
    String friendlyKind(String k){String x=n(k).toUpperCase(Locale.ROOT);if("ACTION".equals(x))return"Action";if("WAITING".equals(x))return"Waiting";if("DECISION".equals(x))return"Decision";if("PROJECT_CANDIDATE".equals(x))return"Project";if("GOAL_SIGNAL".equals(x))return"Goal";return x.isEmpty()?"Update":x;}
    int kindColor(String k){if("ACTION".equalsIgnoreCase(k))return CortexUi.ACCENT;if("WAITING".equalsIgnoreCase(k))return CortexUi.GOLD;if("DECISION".equalsIgnoreCase(k))return CortexUi.SAGE;return CortexUi.MUTED;}
    String sourceLabel(String s){String x=n(s);if(x.isEmpty())return"Cortex";int p=x.lastIndexOf('.');if(p>=0&&p<x.length()-1)x=x.substring(p+1);x=x.replace('_',' ');return Character.toUpperCase(x.charAt(0))+x.substring(1);}
    String cleanTitle(String s,String kind){String x=n(s);if(x.isEmpty())return friendlyKind(kind);x=x.replace(" · Action","").replace(" · Waiting","").replace(" · Decision","").replace(" · Review action","").replace(" · Review waiting","").replace(" · Review decision","");return x.trim();}
    String cleanBody(String s){return n(s).replace("\n---\n","\n").trim();}
    String clip(String s,int max){return s.length()<=max?s:s.substring(0,max)+"…";}
    String age(long ms){long m=Math.max(0,System.currentTimeMillis()-ms)/60000;if(m<1)return"now";if(m<60)return m+"m";long h=m/60;if(h<24)return h+"h";long d=h/24;return d<7?d+"d":new SimpleDateFormat("dd MMM",Locale.getDefault()).format(new Date(ms));}
    String exactTime(long ms){return new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(ms));}
    String n(String s){return s==null?"":s.trim();}
}
