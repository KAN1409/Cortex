package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Capture home: fast capture first, evidence libraries second, technical pipeline one tap away. */
public final class CaptureHubActivity extends Activity {
    private VaultDb db;private int dp(int v){return CortexUi.dp(this,v);}
    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(getApplicationContext());build();}
    @Override protected void onDestroy(){if(db!=null)try{db.close();}catch(Throwable ignored){}super.onDestroy();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(30));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(CortexUi.header(this,"CORTEX · CAPTURE","Capture",CortexUi.ORANGE,"Pipeline",v->open(CaptureOverviewActivity.class)));

        ArrayList<KnowledgeItem> recent=new ArrayList<>();long voice=0,images=0,files=0;try{ArrayList<KnowledgeItem> items=db.captureSearch("",2000);for(KnowledgeItem k:items){if(k==null)continue;if(recent.size()<5)recent.add(k);if("AUDIO".equalsIgnoreCase(k.type))voice++;else if("IMAGE".equalsIgnoreCase(k.type)||"SCREENSHOT".equalsIgnoreCase(k.type))images++;else if("FILE".equalsIgnoreCase(k.type)||"DOCUMENT".equalsIgnoreCase(k.type)||"PDF".equalsIgnoreCase(k.type))files++;}}catch(Throwable ignored){}

        LinearLayout hero=CortexUi.card(this,CortexUi.R_HERO);hero.setPadding(dp(19),dp(18),dp(19),dp(18));hero.addView(CortexUi.eyebrow(this,"CAPTURE MEMORY",CortexUi.ORANGE));TextView hh=CortexUi.plain(this,"Turn moments into meaning",25,CortexUi.TEXT);CortexUi.medium(hh);hh.setPadding(0,dp(11),0,0);hero.addView(hh);TextView hb=CortexUi.text(this,"Capture voice, images, files or a thought. Originals stay inspectable while Cortex understands them separately.",12,CortexUi.MUTED);hb.setPadding(0,dp(6),0,dp(12));hero.addView(hb);
        LinearLayout intelligence=new LinearLayout(this);intelligence.setGravity(Gravity.CENTER_VERTICAL);intelligence.setPadding(dp(11),dp(9),dp(11),dp(9));intelligence.setBackground(CortexUi.round(this,android.graphics.Color.argb(14,240,184,56),android.graphics.Color.argb(58,240,184,56),16));View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.YELLOW,android.graphics.Color.TRANSPARENT,999));intelligence.addView(dot,new LinearLayout.LayoutParams(dp(7),dp(7)));TextView it=CortexUi.plain(this,"Evidence first · understanding stays traceable",10,CortexUi.YELLOW);CortexUi.medium(it);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.setMargins(dp(9),0,0,0);intelligence.addView(it,ip);hero.addView(intelligence);body.addView(hero);CortexMotion.hero(hero);

        body.addView(CortexUi.section(this,"Quick capture"));LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);quick.addView(quick("Voice","voice",CortexUi.ORANGE,()->capture("voice")),new LinearLayout.LayoutParams(0,dp(78),1));quick.addView(quick("Note","text",CortexUi.YELLOW,()->capture("text")),new LinearLayout.LayoutParams(0,dp(78),1));quick.addView(quick("Image","photo",CortexUi.GREEN,()->capture("photo")),new LinearLayout.LayoutParams(0,dp(78),1));quick.addView(quick("File","file",CortexUi.OLIVE,()->capture("file")),new LinearLayout.LayoutParams(0,dp(78),1));quick.addView(quick("Screen","open",CortexUi.LIME,()->open(PhoneContextAccessActivity.class)),new LinearLayout.LayoutParams(0,dp(78),1));body.addView(quick);CortexMotion.enter(quick,1);

        if(!recent.isEmpty()){
            LinearLayout recentHead=new LinearLayout(this);recentHead.setGravity(Gravity.CENTER_VERTICAL);recentHead.addView(CortexUi.section(this,"Recent evidence"),new LinearLayout.LayoutParams(0,-2,1));TextView all=CortexUi.plain(this,"See library",10,CortexUi.LIME);CortexUi.medium(all);all.setPadding(dp(10),dp(18),dp(2),dp(8));all.setOnClickListener(v->open(VaultActivity.class));recentHead.addView(all);body.addView(recentHead);
            int i=0;for(KnowledgeItem k:recent){View row=evidenceRow(k);body.addView(row);CortexMotion.enter(row,Math.min(4,i++));}
        }

        body.addView(CortexUi.section(this,"Libraries"));View vr=library("Voice Library",voice+" recording"+(voice==1?"":"s"),"voice",CortexUi.ORANGE,()->open(VoiceLibraryActivity.class));body.addView(vr,lp(0,0,0,8));View ir=library("Visual Memory",images+" captured here · full screenshot index lives in Memory","photo",CortexUi.GREEN,()->open(VisualMemoryActivity.class));body.addView(ir,lp(0,0,0,8));View fr=library("Files & documents",files+" imported source item"+(files==1?"":"s"),"file",CortexUi.OLIVE,()->open(VaultActivity.class));body.addView(fr,lp(0,0,0,8));
        TextView provenance=CortexUi.text(this,"Captured material remains source evidence. Judgment decides separately whether anything belongs in Now.",10,CortexUi.FAINT);provenance.setPadding(dp(2),dp(6),dp(2),dp(4));body.addView(provenance);

        CortexUi.addBottomNav(this,root,"capture",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private LinearLayout quick(String label,String icon,int color,Runnable go){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(2),dp(5),dp(2),dp(3));CortexUi.pressable(this,box,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,17));box.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(34),dp(34)));TextView t=CortexUi.plain(this,label,9,CortexUi.MUTED);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(5),0,0);box.addView(t);box.setOnClickListener(v->{CortexMotion.haptic(v,false);go.run();});return box;}

    private LinearLayout evidenceRow(KnowledgeItem k){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(3),dp(10),dp(3),dp(10));String type=safe(k.type).toUpperCase(Locale.ROOT);String icon="AUDIO".equals(type)?"voice":(("IMAGE".equals(type)||"SCREENSHOT".equals(type))?"photo":"file");int color="AUDIO".equals(type)?CortexUi.ORANGE:(("IMAGE".equals(type)||"SCREENSHOT".equals(type))?CortexUi.GREEN:CortexUi.OLIVE);row.addView(CortexUi.glyph(this,icon,color,false),new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(11),0,dp(6),0);row.addView(tx,tp);TextView h=CortexUi.text(this,friendlyTitle(k),12,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(1);tx.addView(h);TextView m=CortexUi.plain(this,stamp(k.createdAt)+" · "+friendlyState(k.status),9,CortexUi.MUTED);m.setPadding(0,dp(3),0,0);tx.addView(m);TextView arrow=CortexUi.plain(this,"›",22,CortexUi.FAINT);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(26),dp(36)));row.setOnClickListener(v->{Intent i=new Intent(this,VaultActivity.class);i.putExtra("item_id",k.id);startActivity(i);});return row;}

    private LinearLayout library(String title,String detail,String icon,int color,Runnable go){LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(13),dp(12),dp(12),dp(12));CortexUi.pressable(this,card,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,20));card.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,dp(6),0);card.addView(tx,xp);TextView h=CortexUi.plain(this,title,14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView d=CortexUi.text(this,detail,10,CortexUi.MUTED);d.setPadding(0,dp(4),0,0);tx.addView(d);TextView arrow=CortexUi.plain(this,"›",24,color);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(42)));card.setOnClickListener(v->go.run());return card;}
    private void capture(String mode){try{Intent i=new Intent(this,ProposalCaptureActivity.class);i.putExtra("mode",mode);startActivity(i);}catch(Throwable ignored){CortexNavigation.openInput(this);}}
    private void open(Class<?> c){try{startActivity(new Intent(this,c));}catch(Throwable ignored){}}
    private String friendlyTitle(KnowledgeItem k){String x=safe(k.title);if(x.isEmpty()||x.matches("(?i)screenshot[_-].*"))x=first(k.summary,k.extractedText,k.rawText);if(x.isEmpty())x="Captured evidence";x=x.replaceAll("\\s+"," ").trim();return x.length()>70?x.substring(0,70)+"…":x;}
    private String friendlyState(String s){String x=safe(s).toLowerCase(Locale.ROOT);if(x.isEmpty()||"done".equals(x)||"analyzed".equals(x))return"ready";if("queued".equals(x)||"analyzing".equals(x)||"processing".equals(x))return"processing";return x.replace('_',' ');}
    private String stamp(long t){return new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(t));}
    private static String first(String...xs){if(xs!=null)for(String x:xs)if(x!=null&&!x.trim().isEmpty())return x.trim();return"";}
    private static String safe(String s){return s==null?"":s.trim();}
    private LinearLayout.LayoutParams lp(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
}
