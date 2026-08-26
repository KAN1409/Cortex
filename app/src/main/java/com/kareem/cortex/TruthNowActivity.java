package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Clean-slate NOW.
 * Only grounded Truth Objects are allowed on this surface; raw capture chronology and legacy derived guesses stay in History.
 */
public class TruthNowActivity extends Activity {
    private LinearLayout body;
    private VaultDb db;
    private volatile boolean destroyed=false;
    private int generation=0;
    private final ExecutorService loader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-truth-now");t.setPriority(Thread.NORM_PRIORITY-1);return t;});

    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());TruthSchema.ensure(db);build();refreshAsync();
    }
    @Override protected void onResume(){super.onResume();if(!destroyed)refreshAsync();}
    @Override protected void onDestroy(){destroyed=true;generation++;loader.shutdownNow();if(db!=null)try{db.close();}catch(Throwable ignored){}db=null;super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));header();
        TextView loading=CortexUi.plain(this,"CHECKING GROUNDED STATE…",9,CortexUi.FAINT);loading.setPadding(dp(3),dp(22),0,dp(8));body.addView(loading);
        CortexUi.addBottomNav(this,root,"brief",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void header(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(7),dp(2),dp(10));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        TextView cortex=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(cortex);if(android.os.Build.VERSION.SDK_INT>=21)cortex.setLetterSpacing(.20f);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=CortexUi.divider(this);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(dp(1),dp(28));vp.setMargins(dp(12),0,dp(12),0);row.addView(divider,vp);
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView now=CortexUi.plain(this,"NOW",12,CortexUi.TEXT);CortexUi.medium(now);tx.addView(now);
        TextView sub=CortexUi.plain(this,"Grounded actions, waiting, decisions and important events.",9,CortexUi.MUTED);sub.setPadding(0,dp(2),0,0);tx.addView(sub);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        CortexGlyphView settings=CortexUi.glyph(this,"settings",CortexUi.RED,false);settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));row.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));
        body.addView(row);
    }

    private void refreshAsync(){
        if(destroyed||loader.isShutdown())return;final int g=++generation;
        try{loader.execute(()->{VaultDb local=null;try{local=new VaultDb(getApplicationContext());TruthNowEngine.Snapshot s=TruthNowEngine.snapshot(local);runOnUiThread(()->{if(!destroyed&&g==generation)render(s);});}
            catch(Throwable e){runOnUiThread(()->{if(!destroyed&&g==generation)renderError();});}
            finally{if(local!=null)try{local.close();}catch(Throwable ignored){}}});}catch(RejectedExecutionException ignored){}
    }

    private void render(TruthNowEngine.Snapshot s){
        if(body==null||destroyed)return;while(body.getChildCount()>1)body.removeViewAt(1);
        boolean any=false;
        if(!s.actions.isEmpty()){section("NEEDS YOUR ATTENTION",CortexUi.RED);for(TruthObjectStore.Item x:s.actions)itemRow(x);any=true;}
        if(!s.waiting.isEmpty()){section("WAITING FOR",CortexUi.ORANGE);for(TruthObjectStore.Item x:s.waiting)itemRow(x);any=true;}
        if(!s.decisions.isEmpty()){section("DECISIONS YOU MADE",CortexUi.YELLOW);for(TruthObjectStore.Item x:s.decisions)itemRow(x);any=true;}
        if(!s.important.isEmpty()){section("IMPORTANT",CortexUi.GREEN);for(TruthObjectStore.Item x:s.important)itemRow(x);any=true;}
        if(!any)body.addView(emptyCard(),space(dp(15)));
        body.addView(askDock(),space(dp(16)));
    }

    private void section(String label,int color){TextView h=CortexUi.plain(this,label,9,color);CortexUi.medium(h);h.setPadding(dp(2),dp(20),0,dp(8));body.addView(h);}

    private void itemRow(TruthObjectStore.Item x){
        LinearLayout row=CortexUi.card(this,18);row.setPadding(dp(13),dp(12),dp(13),dp(12));CortexUi.pressable(this,row,CortexUi.matte(this,18));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);int color=color(x.kind);
        top.addView(CortexUi.glyph(this,glyph(x.kind),color,true),new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(7),0);top.addView(tx,xp);
        TextView title=CortexUi.text(this,displayTitle(x),14,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(2);tx.addView(title);
        TextView meta=CortexUi.plain(this,label(x.kind)+" · "+sourceLabel(x.source)+" · "+Math.round(x.confidence*100)+"%",9,color);meta.setPadding(0,dp(3),0,0);tx.addView(meta);
        top.addView(CortexUi.plain(this,"›",23,CortexUi.MUTED),new LinearLayout.LayoutParams(dp(28),dp(38)));row.addView(top);
        String detail=clip(x.body,190);if(!detail.isEmpty()&&!detail.equals(displayTitle(x))){TextView b=CortexUi.text(this,detail,11,CortexUi.MUTED);b.setMaxLines(3);b.setPadding(dp(48),dp(5),0,0);row.addView(b);}
        row.setOnClickListener(v->detail(x));body.addView(row,space(dp(7)));
    }

    private View emptyCard(){
        LinearLayout c=CortexUi.card(this,22);c.setPadding(dp(16),dp(18),dp(16),dp(18));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(CortexUi.glyph(this,"check",CortexUi.GREEN,true),new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);row.addView(tx,xp);
        TextView h=CortexUi.text(this,"Nothing grounded needs surfacing right now",16,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);
        TextView b=CortexUi.text(this,"Cortex stays quiet instead of turning notification noise or ambiguous evidence into fake tasks and decisions.",11,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);tx.addView(b);c.addView(row);return c;
    }

    private View askDock(){
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(10),dp(7),dp(7),dp(7));dock.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,18));
        dock.addView(CortexUi.glyph(this,"brain",CortexUi.YELLOW,false),new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView ask=CortexUi.plain(this,"Ask what needs me, what I'm waiting for, or what I decided",11,CortexUi.MUTED);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(9),0,dp(5),0);ask.setGravity(Gravity.CENTER_VERTICAL);ask.setMaxLines(2);dock.addView(ask,ap);
        TextView go=CortexUi.action(this,"Ask",CortexUi.YELLOW,false);dock.addView(go,new LinearLayout.LayoutParams(dp(72),dp(44)));
        View.OnClickListener l=v->CortexActionExecutor.openBrain(this,0,"What actually needs my attention now? Separate grounded actions, things I am waiting for, decisions I actually made, and important events. Ignore notification noise and unsupported guesses.");
        ask.setOnClickListener(l);go.setOnClickListener(l);return dock;
    }

    private void detail(TruthObjectStore.Item x){
        if(destroyed)return;Dialog d=new Dialog(this);ScrollView sv=new ScrollView(this);LinearLayout c=CortexUi.card(this,24);c.setPadding(dp(18),dp(18),dp(18),dp(18));sv.addView(c);
        TextView h=CortexUi.text(this,displayTitle(x),20,CortexUi.TEXT);CortexUi.medium(h);c.addView(h);
        TextView meta=CortexUi.plain(this,label(x.kind)+" · "+sourceLabel(x.source)+" · confidence "+Math.round(x.confidence*100)+"%",10,color(x.kind));meta.setPadding(0,dp(5),0,dp(12));c.addView(meta);
        if(!x.body.isEmpty()){TextView b=CortexUi.text(this,x.body,13,CortexUi.TEXT);b.setTextIsSelectable(true);c.addView(b);}
        TextView truth=CortexUi.plain(this,"Grounded in event #"+x.eventId+(x.signalId>0?" · raw signal #"+x.signalId:"")+(x.memoryId>0?" · evidence #"+x.memoryId:""),9,CortexUi.MUTED);truth.setPadding(0,dp(12),0,0);c.addView(truth);
        long evidenceId=evidenceId(x);
        TextView brain=CortexUi.action(this,"Ask Brain about this",CortexUi.YELLOW,true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.setMargins(0,dp(15),0,0);c.addView(brain,bp);
        brain.setOnClickListener(v->{d.dismiss();CortexActionExecutor.openBrain(this,evidenceId,"Use this grounded Cortex Truth Object and its source evidence. Explain what it means and suggest an executable next step without inventing responsibility or intent.\n\n"+label(x.kind)+": "+x.text());});
        if(evidenceId>0){TextView source=CortexUi.action(this,"Open source evidence",CortexUi.MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(44));sp.setMargins(0,dp(8),0,0);c.addView(source,sp);source.setOnClickListener(v->{d.dismiss();try{Intent i=new Intent(this,CaptureResultActivity.class);i.putExtra("item_id",evidenceId);startActivity(i);}catch(Throwable ignored){}});}
        if(TruthObjectStore.ACTION.equals(x.kind)||TruthObjectStore.WAITING.equals(x.kind)){
            TextView done=CortexUi.action(this,"Mark resolved",CortexUi.GREEN,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(44));rp.setMargins(0,dp(8),0,0);c.addView(done,rp);
            done.setOnClickListener(v->{if(db!=null&&TruthObjectStore.resolve(db,x.id,"Resolved by user from NOW")){CortexHaptics.confirm(done);d.dismiss();refreshAsync();}});
        }else if(TruthObjectStore.IMPORTANT.equals(x.kind)){
            TextView dismiss=CortexUi.action(this,"Dismiss from NOW",CortexUi.MUTED,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(44));rp.setMargins(0,dp(8),0,0);c.addView(dismiss,rp);
            dismiss.setOnClickListener(v->{if(db!=null&&TruthObjectStore.dismiss(db,x.id,"Dismissed by user from NOW")){d.dismiss();refreshAsync();}});
        }
        TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,0);c.addView(close,cp);close.setOnClickListener(v->d.dismiss());
        d.setContentView(sv);try{d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.82f));}catch(Throwable ignored){}
    }

    private long evidenceId(TruthObjectStore.Item x){
        if(x.memoryId>0)return x.memoryId;if(x.signalId>0&&db!=null)try{return RawSignalStore.promotedItemId(db,x.signalId);}catch(Throwable ignored){}return 0;
    }
    private String label(String kind){if(TruthObjectStore.ACTION.equals(kind))return"Action";if(TruthObjectStore.WAITING.equals(kind))return"Waiting";if(TruthObjectStore.DECISION.equals(kind))return"Decision";return"Important";}
    private int color(String kind){if(TruthObjectStore.ACTION.equals(kind))return CortexUi.RED;if(TruthObjectStore.WAITING.equals(kind))return CortexUi.ORANGE;if(TruthObjectStore.DECISION.equals(kind))return CortexUi.YELLOW;return CortexUi.GREEN;}
    private String glyph(String kind){if(TruthObjectStore.ACTION.equals(kind))return"check";if(TruthObjectStore.WAITING.equals(kind))return"clock";if(TruthObjectStore.DECISION.equals(kind))return"brain";return"note";}
    private String displayTitle(TruthObjectStore.Item x){String t=x.title==null?"":x.title.trim();if(!t.isEmpty())return clip(t,120);return clip(x.text(),120);}
    private String sourceLabel(String s){String x=s==null?"":s.trim();if(x.isEmpty())return"Cortex";int p=x.lastIndexOf('.');if(p>=0&&p<x.length()-1)x=x.substring(p+1);x=x.replace('_',' ');return Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private String clip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    private LinearLayout.LayoutParams space(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,top,0,0);return p;}
    private void renderError(){if(body==null||destroyed)return;while(body.getChildCount()>1)body.removeViewAt(1);TextView t=CortexUi.text(this,"NOW could not read the local Truth ledger right now. Raw evidence was not changed.",12,CortexUi.MUTED);t.setPadding(0,dp(24),0,0);body.addView(t);}
}
