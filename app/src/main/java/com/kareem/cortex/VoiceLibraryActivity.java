package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Reference-led voice evidence library backed by the canonical capture store. */
public final class VoiceLibraryActivity extends Activity {
    private VaultDb db;private LinearLayout list,filters;private String filter="all";private final Handler ui=new Handler(Looper.getMainLooper());private boolean resumed;private int dp(int v){return CortexUi.dp(this,v);}
    private final Runnable liveRefresh=new Runnable(){public void run(){if(!resumed)return;render();}};
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();}
    @Override protected void onResume(){super.onResume();resumed=true;try{AnalysisQueue.kick(this,null,null);}catch(Throwable ignored){}render();}
    @Override protected void onPause(){resumed=false;ui.removeCallbacks(liveRefresh);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(30));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(40),dp(48)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));tx.addView(CortexUi.eyebrow(this,"CORTEX · VOICE MEMORY",CortexUi.LIME));TextView h=CortexUi.plain(this,"Voice Library",30,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);body.addView(head);
        TextView sub=CortexUi.text(this,"Original recordings stay inspectable while transcription and understanding update separately.",11,CortexUi.MUTED);sub.setPadding(dp(2),dp(4),dp(2),dp(12));body.addView(sub);
        TextView capture=CortexUi.action(this,"NEW VOICE CAPTURE",CortexUi.LIME,true);capture.setOnClickListener(v->{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode","voice");startActivity(i);});body.addView(capture,new LinearLayout.LayoutParams(-1,dp(46)));
        filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);filters.setPadding(0,dp(12),0,0);body.addView(filters);body.addView(CortexUi.section(this,"Voice evidence"));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void render(){
        if(list==null||db==null)return;ui.removeCallbacks(liveRefresh);list.removeAllViews();filters.removeAllViews();ArrayList<KnowledgeItem> all=db.captureSearch("",1200);int total=0,ready=0,processing=0;for(KnowledgeItem k:all){if(k==null||!"AUDIO".equalsIgnoreCase(k.type)||!IntentionalCapturePolicy.visibleInCapturedLibrary(k))continue;total++;if(isReady(k.status))ready++;else if(isProcessing(k.status))processing++;}
        addFilter("All",total,"all",0);addFilter("Analyzed",ready,"ready",7);addFilter("Processing",processing,"processing",7);
        int n=0;for(KnowledgeItem k:all){if(k==null||!"AUDIO".equalsIgnoreCase(k.type)||!IntentionalCapturePolicy.visibleInCapturedLibrary(k))continue;if("ready".equals(filter)&&!isReady(k.status))continue;if("processing".equals(filter)&&!isProcessing(k.status))continue;View card=card(k);list.addView(card,margin(0,0,0,9));CortexMotion.enter(card,Math.min(n++,5));}
        if(n==0){LinearLayout empty=CortexUi.card(this,22);empty.addView(CortexUi.eyebrow(this,"VOICE MEMORY",CortexUi.MUTED));TextView eh=CortexUi.plain(this,total==0?"No voice captures yet":"Nothing in this filter",18,CortexUi.TEXT);CortexUi.medium(eh);eh.setPadding(0,dp(8),0,0);empty.addView(eh);TextView eb=CortexUi.text(this,total==0?"Record a thought and Cortex will keep the original WAV beside its transcript and understanding.":"Choose another filter to see your recordings.",11,CortexUi.MUTED);eb.setPadding(0,dp(6),0,0);empty.addView(eb);list.addView(empty);CortexMotion.enter(empty,0);}
        if(resumed&&processing>0)ui.postDelayed(liveRefresh,1200);
    }

    private void addFilter(String title,int count,String key,int left){boolean on=key.equals(filter);int active="all".equals(key)?CortexUi.YELLOW:CortexUi.LIME;TextView b=CortexUi.chip(this,title+" ("+count+")",on?active:CortexUi.MUTED,on);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->{filter=key;CortexMotion.haptic(v,false);render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);p.setMargins(dp(left),0,0,0);filters.addView(b,p);}

    private View card(KnowledgeItem k){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(13),dp(14),dp(12));CortexUi.pressable(this,card,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,21));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView play=CortexUi.plain(this,"▶",17,CortexUi.LIME);play.setGravity(Gravity.CENTER);play.setBackground(CortexUi.round(this,android.graphics.Color.argb(10,190,221,82),android.graphics.Color.argb(70,190,221,82),999));top.addView(play,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(8),0);top.addView(tx,xp);String title=safe(k.title);if(title.isEmpty()||"Voice recording".equalsIgnoreCase(title)){String text=first(k.summary,k.extractedText,k.rawText);title=text.isEmpty()?"Voice recording":clip(text,58);}TextView h=CortexUi.text(this,title,14,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(2);tx.addView(h);long duration=CortexWaveformView.durationMs(k.attachmentPath);String meta=stamp(k.createdAt)+(duration>0?" · "+duration(duration):"");TextView m=CortexUi.plain(this,meta,9,CortexUi.MUTED);m.setPadding(0,dp(3),0,0);tx.addView(m);String state=friendlyState(k.status);int color=isReady(k.status)?CortexUi.LIME:(isProcessing(k.status)?CortexUi.YELLOW:CortexUi.RED);top.addView(CortexUi.chip(this,state,color,isProcessing(k.status)),new LinearLayout.LayoutParams(-2,dp(28)));card.addView(top);
        CortexWaveformView wave=new CortexWaveformView(this);wave.setAccent(CortexUi.LIME);wave.setAudioPath(k.attachmentPath);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(48));wp.setMargins(dp(2),dp(10),dp(2),0);card.addView(wave,wp);String body=first(k.summary,k.extractedText,k.rawText);if(!body.isEmpty()){TextView p=CortexUi.text(this,clip(body,180),10,CortexUi.MUTED);p.setPadding(0,dp(8),0,0);p.setMaxLines(2);card.addView(p);}if(isFailed(k.status)){TextView retry=CortexUi.action(this,"Retry analysis",CortexUi.YELLOW,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(40));rp.setMargins(0,dp(8),0,0);card.addView(retry,rp);retry.setOnClickListener(v->{v.setEnabled(false);db.retry(k.id);AnalysisQueue.kick(this,null,null);render();});}else if(!safe(k.analysisError).isEmpty()){TextView err=CortexUi.text(this,"Needs review · "+clip(k.analysisError,120),9,CortexUi.YELLOW);err.setPadding(0,dp(6),0,0);card.addView(err);}card.setOnClickListener(v->{Intent i=new Intent(this,VoiceDetailActivity.class);i.putExtra("item_id",k.id);startActivity(i);});return card;
    }

    private boolean isReady(String s){String x=safe(s).toLowerCase(Locale.ROOT);return"analyzed".equals(x)||"done".equals(x)||"ready".equals(x)||"complete".equals(x);}private boolean isProcessing(String s){String x=safe(s).toLowerCase(Locale.ROOT);return x.isEmpty()||"queued".equals(x)||"analyzing".equals(x)||"processing".equals(x)||"pending".equals(x);}private boolean isFailed(String s){String x=safe(s).toLowerCase(Locale.ROOT);return x.contains("failed")||x.contains("error");}private String friendlyState(String s){if(isReady(s))return"Analyzed";if(isProcessing(s))return"Processing";if(isFailed(s))return"Needs retry";String x=safe(s);return x.isEmpty()?"Captured":x.replace('_',' ');}
    private String stamp(long t){return new SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.getDefault()).format(new Date(t));}private String duration(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}private static String first(String...xs){if(xs!=null)for(String x:xs)if(x!=null&&!x.trim().isEmpty())return x.trim();return"";}private static String safe(String s){return s==null?"":s.trim();}private static String clip(String s,int n){String x=safe(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
