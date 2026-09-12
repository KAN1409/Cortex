package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Dedicated read-only library for voice captures. Uses the same knowledge_items source as Capture counts. */
public final class VoiceLibraryActivity extends Activity {
    private VaultDb db; private LinearLayout list; private int dp(int v){return CortexUi.dp(this,v);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();render();}
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);head.addView(tx,new LinearLayout.LayoutParams(0,-2,1));tx.addView(CortexUi.eyebrow(this,"CAPTURE · VOICE MEMORY",CortexUi.ORANGE));TextView h=CortexUi.plain(this,"Voice Library",30,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);body.addView(head);
        TextView sub=CortexUi.text(this,"Recordings, transcriptions and their grounded analysis — read from the same source that powers the Capture count.",11,CortexUi.MUTED);sub.setPadding(dp(2),dp(4),dp(2),dp(12));body.addView(sub);
        TextView capture=CortexUi.action(this,"NEW VOICE CAPTURE",CortexUi.ORANGE,true);capture.setOnClickListener(v->{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode","voice");startActivity(i);});body.addView(capture,new LinearLayout.LayoutParams(-1,dp(44)));
        body.addView(CortexUi.section(this,"Voice evidence"));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private void render(){if(list==null||db==null)return;list.removeAllViews();ArrayList<KnowledgeItem> all=db.captureSearch("",1200);int n=0;for(KnowledgeItem k:all){if(k==null||!"AUDIO".equalsIgnoreCase(k.type))continue;add(k);n++;}if(n==0){LinearLayout empty=CortexUi.card(this,22);TextView h=CortexUi.plain(this,"No voice captures yet",18,CortexUi.TEXT);CortexUi.medium(h);empty.addView(h);TextView b=CortexUi.text(this,"New recordings will appear here immediately, before and after transcription.",11,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);empty.addView(b);list.addView(empty);}}

    private void add(KnowledgeItem k){LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(14),dp(13),dp(14),dp(13));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,"voice",CortexUi.ORANGE,true),new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(8),0);top.addView(tx,xp);String title=safe(k.title);if(title.isEmpty())title="Voice capture";TextView h=CortexUi.text(this,title,14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView meta=CortexUi.plain(this,stamp(k.createdAt)+" · "+safe(k.status),9,CortexUi.MUTED);meta.setPadding(0,dp(3),0,0);tx.addView(meta);card.addView(top);String body=first(k.summary,k.extractedText,k.rawText);if(!body.isEmpty()){TextView p=CortexUi.text(this,clip(body,420),11,CortexUi.MUTED);p.setPadding(0,dp(9),0,0);p.setMaxLines(7);card.addView(p);}if(!safe(k.analysisError).isEmpty()){TextView err=CortexUi.text(this,"Analysis: "+clip(k.analysisError,180),10,CortexUi.YELLOW);err.setPadding(0,dp(7),0,0);card.addView(err);}card.setOnClickListener(v->{Intent i=new Intent(this,VaultActivity.class);i.putExtra("item_id",k.id);startActivity(i);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(9));list.addView(card,p);}
    private String stamp(long t){return new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(t));}
    private static String first(String...xs){if(xs!=null)for(String x:xs)if(x!=null&&!x.trim().isEmpty())return x.trim();return"";}
    private static String safe(String s){return s==null?"":s.trim();}
    private static String clip(String s,int n){String x=safe(s).replaceAll("\\s+"," ");return x.length()<=n?x:x.substring(0,n)+"…";}
}
