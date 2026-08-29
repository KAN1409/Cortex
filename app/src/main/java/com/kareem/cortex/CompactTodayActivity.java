package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Brain-first Now surface. Evidence never renders as a feed here. Now only shows the
 * reconciled current situations that still deserve attention after lifecycle maintenance.
 */
public final class CompactTodayActivity extends CortexOrbBriefActivity {
    private View loadingView;
    private SwipeRefreshLayout swipe;

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(CortexUi.aurora(this));
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(24));sv.addView(content);
        swipe=new SwipeRefreshLayout(this);swipe.setColorSchemeColors(CortexUi.BRAND,CortexUi.BLUE,CortexUi.ORANGE);swipe.setProgressBackgroundColorSchemeColor(CortexUi.SURFACE);swipe.addView(sv,new android.view.ViewGroup.LayoutParams(-1,-1));swipe.setOnRefreshListener(this::refreshAsync);
        root.addView(swipe,new LinearLayout.LayoutParams(-1,0,1));systemHeader();
        TextView loading=CortexUi.plain(this,"Reconciling current situations…",10,CortexUi.FAINT);loading.setPadding(dp(3),dp(13),0,dp(5));loadingView=loading;content.addView(loading);
        CortexUi.addBottomNav(this,root,"today",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    @Override void systemHeader(){
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(1),dp(8),dp(1),dp(4));
        CortexGlyphView brand=CortexUi.glyph(this,"brand",CortexUi.BRAND,false);top.addView(brand,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView cortex=CortexUi.plain(this,"C  O  R  T  E  X",14,CortexUi.TEXT);CortexUi.medium(cortex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMargins(dp(10),0,dp(10),0);top.addView(cortex,cp);
        View divider=new View(this);divider.setBackgroundColor(CortexUi.BORDER_SOFT);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(30));dpv.setMargins(dp(2),0,dp(10),0);top.addView(divider,dpv);
        TextView context=CortexUi.plain(this,"NOW",11,CortexUi.MUTED);context.setLetterSpacing(.08f);top.addView(context,new LinearLayout.LayoutParams(0,-2,1));
        CortexGlyphView menu=CortexUi.glyph(this,"menu",CortexUi.MUTED,false);menu.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});top.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(40)));content.addView(top);

        LinearLayout hero=CortexUi.card(this,16);hero.setPadding(dp(15),dp(13),dp(15),dp(13));
        LinearLayout hr=new LinearLayout(this);hr.setGravity(Gravity.CENTER_VERTICAL);TextView h=CortexUi.plain(this,"What matters now",18,CortexUi.TEXT);CortexUi.medium(h);hr.addView(h,new LinearLayout.LayoutParams(0,-2,1));TextView chip=CortexUi.chip(this,"LIVE",CortexUi.BRAND,false);hr.addView(chip,new LinearLayout.LayoutParams(-2,dp(27)));hero.addView(hr);
        TextView hb=CortexUi.text(this,"One current-state view synthesized from Relay, memory and your captures. Newer evidence replaces stale meaning instead of adding another card.",12,CortexUi.MUTED);hb.setPadding(0,dp(5),0,0);hero.addView(hb);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(10),0,dp(2));content.addView(hero,hp);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;
        if(loadingView!=null){content.removeView(loadingView);loadingView=null;}
        while(content.getChildCount()>2)content.removeViewAt(2);
        collectAudio(s);
        content.addView(brainStatusCard(),margins(0,dp(10),0,0));
        if(!s.actions.isEmpty())derivedSection("NEEDS YOU NOW",CortexUi.BRAND,s.actions,"action",3);
        if(!s.waiting.isEmpty())derivedSection("WAITING ON",CortexUi.AURORA,s.waiting,"waiting",3);
        if(!s.decisions.isEmpty())derivedSection("DECISIONS TO MOVE",CortexUi.BRAND,s.decisions,"decision",2);
        if(!s.changes.isEmpty())derivedSection("CHANGED RECENTLY",CortexUi.BLUE,s.changes,"change",3);
        if(!s.worthKnowing.isEmpty())derivedSection("WORTH KNOWING",CortexUi.MUTED,s.worthKnowing,"info",3);
        if(!s.reviews.isEmpty())reviewRow(s.reviews.size());
        if(s.attentionEmpty()&&!audioItems.isEmpty()){sectionTitle("RECENT CAPTURE",CortexUi.MUTED);content.addView(audioCard(s),margins(0,0,0,0));}
        if(s.attentionEmpty()){
            LinearLayout e=CortexUi.card(this,16);e.setPadding(dp(16),dp(17),dp(16),dp(17));TextView h=CortexUi.plain(this,"Clear horizon",18,CortexUi.TEXT);CortexUi.medium(h);e.addView(h);TextView b=CortexUi.text(this,"Nothing currently requires your attention. Cortex is still learning from Relay and your captures in the background.",12,CortexUi.MUTED);b.setPadding(0,dp(5),0,0);e.addView(b);content.addView(e,margins(0,dp(12),0,0));
        }
        content.addView(promptDock(),margins(0,dp(14),0,dp(6)));finishRefresh();
    }

    private View brainStatusCard(){
        RelayHealthStore.Status relay=RelayHealthStore.read(this,db);boolean model=LocalModelManager.installed(this);CognitiveAuthorityMode mode=CognitiveFeatureFlags.authorityMode(this);
        boolean primary=model&&mode==CognitiveAuthorityMode.V2_PRIMARY;boolean healthy=relay.healthy();
        LinearLayout card=CortexUi.card(this,14);card.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        View dot=new View(this);int color=healthy&&primary?CortexUi.BRAND:CortexUi.ORANGE;dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(7),dp(7));dpv.setMargins(0,0,dp(9),0);row.addView(dot,dpv);
        TextView h=CortexUi.plain(this,relay.label(),11,CortexUi.TEXT);CortexUi.medium(h);row.addView(h,new LinearLayout.LayoutParams(0,-2,1));String brain=model?(primary?"QWEN · PRIMARY":"QWEN · "+mode.name()):"MODEL NOT READY";TextView chip=CortexUi.chip(this,brain,color,false);row.addView(chip,new LinearLayout.LayoutParams(-2,dp(27)));card.addView(row);
        String detail=relay.recentEvidence?"Authenticated Relay evidence is reaching Cortex. Only reconciled current situations can surface here.":"Evidence stays in the background. Only unresolved current situations can appear on Now.";
        TextView sub=CortexUi.plain(this,detail,9,CortexUi.MUTED);sub.setPadding(dp(16),dp(4),0,0);card.addView(sub);return card;
    }

    @Override void renderError(){super.renderError();finishRefresh();}
    @Override View signalCard(PrimeBriefStore.Snapshot s){return brainStatusCard();}
    private void finishRefresh(){if(swipe!=null)swipe.setRefreshing(false);}

    @Override View audioCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=CortexUi.card(this,16);card.setGravity(Gravity.CENTER_VERTICAL);card.setOrientation(LinearLayout.HORIZONTAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));
        CortexGlyphView wave=CortexUi.glyph(this,"wave",CortexUi.BRAND,true);card.addView(wave,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,dp(8),0);card.addView(text,tp);
        KnowledgeItem k=audioItems.get(Math.max(0,Math.min(audioIndex,audioItems.size()-1)));TextView title=CortexUi.text(this,k.title==null||k.title.trim().isEmpty()?"Latest voice note":clipLocal(k.title,56),14,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(1);text.addView(title);
        TextView meta=CortexUi.plain(this,"Voice context  ·  "+ageLocal(k.createdAt),10,CortexUi.MUTED);meta.setPadding(0,dp(3),0,0);text.addView(meta);TextView open=CortexUi.plain(this,"Open",10,CortexUi.BRAND);open.setGravity(Gravity.CENTER);card.addView(open,new LinearLayout.LayoutParams(dp(46),dp(30)));card.setOnClickListener(v->showVoicePlayer(s));return card;
    }

    void showVoicePlayer(PrimeBriefStore.Snapshot s){android.app.Dialog d=new android.app.Dialog(this);ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(12),dp(12),dp(12));sv.addView(box);View player=super.audioCard(s);box.addView(player);TextView close=CortexUi.action(this,"Close",CortexUi.MUTED,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,0);box.addView(close,cp);close.setOnClickListener(v->d.dismiss());d.setContentView(sv);try{d.show();if(d.getWindow()!=null)d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.80f));}catch(Throwable ignored){}}

    @Override void derivedSection(String title,int color,List<PrimeBriefStore.Item> xs,String glyph,int limit){
        sectionTitle(title,color);int n=Math.min(limit,xs.size());for(int i=0;i<n;i++){
            PrimeBriefStore.Item x=xs.get(i);String semanticTitle=CandidateConsolidator.presentationTitle(x);String tt=semanticTitle==null||semanticTitle.trim().isEmpty()?friendlyFallback(x.attentionKind):semanticTitle.trim(),body=x.body==null?"":x.body.trim();boolean focus=i==0&&"action".equals(glyph);
            LinearLayout card=CortexUi.card(this,16);card.setPadding(0,0,0,0);if(focus)card.setBackground(CortexUi.round(this,CortexUi.SURFACE,Color.argb(82,Color.red(CortexUi.BRAND),Color.green(CortexUi.BRAND),Color.blue(CortexUi.BRAND)),16));
            LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(dp(12),focus?dp(14):dp(11),dp(10),focus?dp(10):dp(9));View marker=new View(this);marker.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(3),focus?dp(72):dp(52));mp.setMargins(0,0,dp(12),0);main.addView(marker,mp);
            LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);main.addView(txt,new LinearLayout.LayoutParams(0,-2,1));TextView h=CortexUi.text(this,clipLocal(tt,110),focus?18:15,CortexUi.TEXT);CortexUi.medium(h);h.setMaxLines(focus?3:2);txt.addView(h);
            if(!body.isEmpty()&&!sameMeaning(tt,body)){TextView m=CortexUi.text(this,clipLocal(body,145),11,CortexUi.MUTED);m.setMaxLines(focus?2:1);m.setPadding(0,dp(5),0,0);txt.addView(m);}TextView timing=CortexUi.plain(this,friendlyTiming(x),9,focus?CortexUi.BRAND:CortexUi.MUTED);timing.setMaxLines(1);timing.setPadding(0,dp(6),0,0);txt.addView(timing);
            if(!focus){CortexGlyphView arrow=CortexUi.glyph(this,"arrow",CortexUi.MUTED,false);main.addView(arrow,new LinearLayout.LayoutParams(dp(36),dp(36)));arrow.setOnClickListener(v->{AttentionLearning.record(db,x.id,"opened");derivedDetail(x);});}
            card.addView(main);main.setOnClickListener(v->{AttentionLearning.record(db,x.id,"opened");derivedDetail(x);});if(focus)card.addView(compactActions(x),new LinearLayout.LayoutParams(-1,dp(38)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);if(i>0)cp.setMargins(0,dp(7),0,0);content.addView(card,cp);
        }if(xs.size()>n){TextView more=CortexUi.plain(this,"See "+(xs.size()-n)+" more",9,CortexUi.FAINT);more.setGravity(Gravity.RIGHT);more.setPadding(0,dp(7),dp(3),0);content.addView(more);}
    }

    private boolean sameMeaning(String a,String b){String x=a==null?"":a.replaceAll("\\s+"," ").trim().toLowerCase();String y=b==null?"":b.replaceAll("\\s+"," ").trim().toLowerCase();return !x.isEmpty()&&!y.isEmpty()&&(x.equals(y)||y.startsWith(x)||x.startsWith(y));}
    private String friendlyTiming(PrimeBriefStore.Item x){
        if(x.dueAt>0){long delta=x.dueAt-System.currentTimeMillis();if(delta<=0)return"Due now / past due";long mins=Math.max(1,delta/60000L);if(mins<90)return"Due in "+mins+" min";long hours=Math.max(1,Math.round(delta/3600000f));String clock=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(x.dueAt));if(hours<=6)return"Due in "+hours+" h · "+clock;if(hours<=24)return"Later today · "+clock;if(hours<=48)return"Tomorrow · "+clock;return"Upcoming · "+new SimpleDateFormat("EEE HH:mm",Locale.getDefault()).format(new Date(x.dueAt));}
        String band=x.attentionBand==null?"":x.attentionBand.name();if("NOW".equals(band))return"Needs attention";if("WATCHING".equals(band))return"Waiting for an update";if("LATER".equals(band))return"Not due yet";return"Relevant context";
    }
    View compactActions(PrimeBriefStore.Item x){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),0,dp(8),dp(5));TextView why=small("Why",CortexUi.MUTED),done=small("Done",CortexUi.BRAND),snooze=small("Later",CortexUi.MUTED),dismiss=small("Hide",CortexUi.FAINT);why.setOnClickListener(v->{RelayEvidenceStore.Item p=x.signalId>0?RelayEvidenceStore.bySignalId(db,x.signalId):null;String detail=x.attentionBand+" · "+x.attentionScore+"/100\n\n"+x.whyNow;if(p!=null)detail+="\n\nSource: "+p.provenanceLine()+"\n"+p.finalReason;new android.app.AlertDialog.Builder(this).setTitle("Why now").setMessage(detail).setPositiveButton("OK",null).show();});done.setOnClickListener(v->{AttentionLearning.record(db,x.id,"acted");try{CognitiveStore.feedback(db,"derived",x.id,"resolved_by_user","{}",AttentionLearning.VERSION);}catch(Throwable ignored){}resolveDerived(x.id);refreshAsync();});snooze.setOnClickListener(v->{AttentionLearning.snooze(db,x.id,System.currentTimeMillis()+3L*60L*60L*1000L);refreshAsync();});dismiss.setOnClickListener(v->{AttentionLearning.record(db,x.id,"dismissed");refreshAsync();});row.addView(why,new LinearLayout.LayoutParams(0,dp(30),1));row.addView(done,new LinearLayout.LayoutParams(0,dp(30),1));row.addView(snooze,new LinearLayout.LayoutParams(0,dp(30),1));row.addView(dismiss,new LinearLayout.LayoutParams(0,dp(30),1));return row;}
    TextView small(String s,int color){TextView t=CortexUi.plain(this,s,9,color);t.setGravity(Gravity.CENTER);return t;}
    void reviewRow(int count){sectionTitle("NEEDS REVIEW",CortexUi.AURORA);TextView row=CortexUi.text(this,count+" high-value item"+(count==1?"":"s")+" need your judgement",12,CortexUi.TEXT);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(10),dp(13),dp(10));row.setBackground(CortexUi.velvet(this,16));row.setOnClickListener(v->{try{startActivity(new Intent(this,ReviewQueueActivity.class));}catch(Throwable ignored){}});content.addView(row,new LinearLayout.LayoutParams(-1,dp(46)));}
    @Override void sectionTitle(String title,int color){TextView h=CortexUi.plain(this,title,10,color);CortexUi.medium(h);if(android.os.Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.10f);h.setPadding(dp(1),dp(17),0,dp(7));content.addView(h);}
}
