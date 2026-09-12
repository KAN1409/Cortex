package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Premium top-level intelligence surface. Presentation never re-decides attention. */
public final class NowActivity extends PremiumHomeActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(30));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(topBar());TextView loading=CortexUi.eyebrow(this,"BUILDING CURRENT PICTURE",CortexUi.FAINT);loading.setPadding(dp(2),dp(30),0,dp(12));content.addView(loading);
        CortexUi.addBottomNav(this,root,"now",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private LinearLayout topBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(11));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(CortexUi.eyebrow(this,"CORTEX · PERSONAL INTELLIGENCE",CortexUi.LIME));TextView title=CortexUi.plain(this,"Now",34,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(1),0,0);brand.addView(title);
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->open(SettingsActivity.class));row.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));return row;
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);
        ArrayList<PrimeBriefStore.Item> judged=new ArrayList<>();LinkedHashSet<String> seen=new LinkedHashSet<>();
        addUnique(judged,seen,s.actions,16);addUnique(judged,seen,s.waiting,16);addUnique(judged,seen,s.decisions,16);
        judged.removeIf(x->NowQualityPolicy.suppress(x.kind,x.source,x.title,x.body)||AttentionNoisePolicy.suppress(x.source,x.title,x.body,x.kind,""));
        int actions=count(judged,"ACTION"),waiting=count(judged,"WAITING"),decisions=count(judged,"DECISION"),maxNow=CortexPersonalPolicy.maxNowItems(this),active=Math.min(maxNow,actions+waiting+decisions);
        WorkVaultScanner.Counts work;try{work=WorkVaultScanner.counts(db);}catch(Throwable ignored){work=new WorkVaultScanner.Counts();}

        content.addView(attentionHero(active,actions,waiting,decisions),margins(0,4,0,0));content.addView(commandStrip(work),margins(0,11,0,0));
        ArrayList<Situation> situations=buildSituations(judged);if(!situations.isEmpty()){content.addView(sectionHeader("Situations",situations.size(),CortexUi.OLIVE));for(int i=0;i<Math.min(3,situations.size());i++)situationRow(situations.get(i));}

        int shown=0;for(String kind:new String[]{"ACTION","WAITING","DECISION"}){if(shown>=maxNow)break;int c=count(judged,kind);if(c==0)continue;String heading="ACTION".equals(kind)?"Needs you":("WAITING".equals(kind)?"Waiting":"Decisions");int color="ACTION".equals(kind)?CortexUi.LIME:("WAITING".equals(kind)?CortexUi.YELLOW:CortexUi.OLIVE);content.addView(sectionHeader(heading,c,color));for(PrimeBriefStore.Item x:judged){if(shown>=maxNow)break;if(!kind.equalsIgnoreCase(x.kind))continue;intelligenceRow(x,color);shown++;}}
        if(shown==0)content.addView(clearState(),margins(0,18,0,0));

        ArrayList<PrimeBriefStore.Item> worth=new ArrayList<>();if(s.worthKnowing!=null)for(PrimeBriefStore.Item x:s.worthKnowing){if(x==null||NowQualityPolicy.suppress(x.kind,x.source,x.title,x.body)||AttentionNoisePolicy.suppress(x.source,x.title,x.body,x.kind,""))continue;worth.add(x);if(worth.size()>=4)break;}
        if(!worth.isEmpty()){content.addView(sectionHeader("Worth knowing",worth.size(),CortexUi.GREEN));for(PrimeBriefStore.Item x:worth)intelligenceRow(x,CortexUi.GREEN);}
        if(s.reviews!=null&&!s.reviews.isEmpty())content.addView(reviewSurface(s.reviews.size()),margins(0,18,0,0));
        LinearLayout status=CortexPipelineStatusBar.build(this,db);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(18),0,0);content.addView(status,sp);
    }

    private LinearLayout attentionHero(int active,int actions,int waiting,int decisions){
        LinearLayout card=CortexUi.card(this,30);card.setPadding(dp(20),dp(19),dp(20),dp(18));card.setBackground(CortexUi.gradient(this,CortexUi.SURFACE_3,CortexUi.SURFACE,CortexUi.HAIRLINE,30));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.eyebrow(this,"ATTENTION · JUDGED",active>0?CortexUi.LIME:CortexUi.GREEN),new LinearLayout.LayoutParams(0,-2,1));top.addView(CortexUi.chip(this,active>0?active+" active":"clear",active>0?CortexUi.LIME:CortexUi.GREEN,true),new LinearLayout.LayoutParams(-2,dp(30)));card.addView(top);
        String headline=active==0?"Nothing has earned your attention":(active==1?"One thing matters now":active+" things matter now");TextView h=CortexUi.plain(this,headline,26,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(14),0,0);card.addView(h);
        TextView b=CortexUi.text(this,active==0?"Cortex is still observing, connecting and judging in the background. Silence is intentional.":"Already reduced from the noise. What remains crossed your attention policy and stays tied to grounded context.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,dp(15));card.addView(b);
        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);metrics.addView(CortexUi.metric(this,String.valueOf(actions),"NEEDS YOU",actions>0?CortexUi.LIME:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(70),1));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,dp(70),1);wp.setMargins(dp(7),0,dp(7),0);metrics.addView(CortexUi.metric(this,String.valueOf(waiting),"WAITING",waiting>0?CortexUi.YELLOW:CortexUi.FAINT),wp);metrics.addView(CortexUi.metric(this,String.valueOf(decisions),"DECISIONS",decisions>0?CortexUi.OLIVE:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(70),1));card.addView(metrics);return card;
    }

    private LinearLayout commandStrip(WorkVaultScanner.Counts work){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout ask=new LinearLayout(this);ask.setGravity(Gravity.CENTER_VERTICAL);ask.setPadding(dp(14),dp(9),dp(10),dp(9));CortexUi.pressable(this,ask,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,19));ask.addView(CortexUi.glyph(this,"bolt",CortexUi.LIME,true),new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(10),0,dp(6),0);ask.addView(copy,xp);TextView ah=CortexUi.plain(this,"Ask Cortex",14,CortexUi.TEXT);CortexUi.medium(ah);copy.addView(ah);TextView as=CortexUi.plain(this,"Reason across your grounded context",10,CortexUi.MUTED);as.setPadding(0,dp(2),0,0);copy.addView(as);TextView arrow=CortexUi.plain(this,"›",25,CortexUi.LIME);arrow.setGravity(Gravity.CENTER);ask.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(40)));ask.setOnClickListener(v->CortexActionExecutor.openBrain(this,0,"Review my current grounded Cortex context. Tell me what matters most now, what is connected, and the highest-leverage next step."));wrap.addView(ask,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(8),0,0);row.addView(shortcut("Work",work.projects>0?work.projects+" projects":"Professional memory","project",CortexUi.LIME,v->open(WorkWorkspaceActivity.class)),new LinearLayout.LayoutParams(0,dp(82),1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(82),1);mp.setMargins(dp(8),0,dp(8),0);row.addView(shortcut("Memory","Visual recall","photo",CortexUi.GREEN,v->open(VisualMemoryActivity.class)),mp);row.addView(shortcut("Brief","Daily picture","text",CortexUi.OLIVE,v->showComposed(false)),new LinearLayout.LayoutParams(0,dp(82),1));wrap.addView(row);return wrap;
    }

    private LinearLayout shortcut(String title,String subtitle,String icon,int color,View.OnClickListener click){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(12),dp(9),dp(12),dp(9));CortexUi.pressable(this,box,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,18));box.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(28),dp(28)));TextView h=CortexUi.plain(this,title,12,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(4),0,0);box.addView(h);TextView s=CortexUi.plain(this,subtitle,8,CortexUi.MUTED);s.setMaxLines(1);box.addView(s);box.setOnClickListener(click);return box;}

    private void intelligenceRow(PrimeBriefStore.Item x,int accent){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);CortexUi.pressable(this,card,CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,20));View rail=new View(this);rail.setBackground(CortexUi.round(this,accent,Color.TRANSPARENT,999));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(3),-1);rp.setMargins(0,dp(12),0,dp(12));card.addView(rail,rp);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(13),dp(13),dp(13));card.addView(body,new LinearLayout.LayoutParams(0,-2,1));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);String kind=x.kind==null?"CONTEXT":x.kind.toUpperCase(Locale.ROOT);top.addView(CortexUi.eyebrow(this,kind,accent),new LinearLayout.LayoutParams(0,-2,1));top.addView(CortexUi.plain(this,Math.round(Math.max(0,Math.min(1,x.confidence))*100)+"%",9,CortexUi.FAINT));body.addView(top);
        String title=nz(x.title);if(title.isEmpty())title=kind.substring(0,1)+kind.substring(1).toLowerCase(Locale.ROOT);TextView h=CortexUi.text(this,title,14,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(6),0,0);h.setMaxLines(2);body.addView(h);String preview=nz(x.body);if(!preview.isEmpty()){TextView p=CortexUi.text(this,clipLocal(preview,180),11,CortexUi.MUTED);p.setPadding(0,dp(4),0,0);p.setMaxLines(3);body.addView(p);}TextView meta=CortexUi.plain(this,"Judgment · grounded context · tap for why",9,CortexUi.FAINT);meta.setPadding(0,dp(7),0,0);body.addView(meta);card.setOnClickListener(v->derivedDetail(x));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(8));content.addView(card,cp);
    }

    private LinearLayout sectionHeader(String label,int count,int color){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(25),dp(1),dp(10));View dot=new View(this);dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));TextView h=CortexUi.plain(this,label,12,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(9),0,0,0);row.addView(h,hp);row.addView(CortexUi.plain(this,String.valueOf(count),10,CortexUi.MUTED));return row;}
    private LinearLayout clearState(){LinearLayout card=CortexUi.card(this,25);card.setPadding(dp(19),dp(23),dp(19),dp(23));card.addView(CortexUi.eyebrow(this,"QUIET BY DESIGN",CortexUi.GREEN));TextView h=CortexUi.plain(this,"You're clear",22,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(9),0,0);card.addView(h);TextView b=CortexUi.text(this,"Nothing currently justifies interruption. Cortex keeps observing and will surface something only when it earns attention.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);card.addView(b);return card;}
    private LinearLayout reviewSurface(int count){LinearLayout row=CortexUi.card(this,22);row.setPadding(dp(15),dp(14),dp(15),dp(14));CortexUi.pressable(this,row,CortexUi.velvet(this,22));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.glyph(this,"review",CortexUi.YELLOW,true),new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(11),0,0,0);top.addView(tx,xp);TextView h=CortexUi.plain(this,"Needs your review",14,CortexUi.TEXT);CortexUi.medium(h);tx.addView(h);TextView b=CortexUi.plain(this,count+" ambiguous item"+(count==1?"":"s")+" kept out of canonical knowledge",10,CortexUi.MUTED);b.setPadding(0,dp(3),0,0);tx.addView(b);row.addView(top);row.setOnClickListener(v->open(ReviewQueueActivity.class));return row;}

    private ArrayList<Situation> buildSituations(List<PrimeBriefStore.Item> items){LinkedHashMap<Long,ArrayList<PrimeBriefStore.Item>> grouped=new LinkedHashMap<>();for(PrimeBriefStore.Item x:items){if(x==null||x.threadId<=0)continue;ArrayList<PrimeBriefStore.Item> bucket=grouped.get(x.threadId);if(bucket==null){bucket=new ArrayList<>();grouped.put(x.threadId,bucket);}bucket.add(x);}ArrayList<Situation> out=new ArrayList<>();for(Map.Entry<Long,ArrayList<PrimeBriefStore.Item>> e:grouped.entrySet())if(e.getValue().size()>=2)out.add(new Situation(e.getValue()));out.sort((a,b)->Integer.compare(b.items.size(),a.items.size()));return out;}
    private void situationRow(Situation s){PrimeBriefStore.Item first=s.items.get(0);LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(15),dp(14),dp(15),dp(14));CortexUi.pressable(this,card,CortexUi.velvet(this,22));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.eyebrow(this,"CONNECTED SITUATION",CortexUi.OLIVE),new LinearLayout.LayoutParams(0,-2,1));top.addView(CortexUi.chip(this,s.items.size()+" signals",CortexUi.OLIVE,false),new LinearLayout.LayoutParams(-2,dp(27)));card.addView(top);String title=nz(first.title);if(title.isEmpty())title="Related context moving together";TextView h=CortexUi.text(this,title,14,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(7),0,0);h.setMaxLines(2);card.addView(h);TextView b=CortexUi.plain(this,"Already-judged items sharing the same Cortex situation thread.",10,CortexUi.MUTED);b.setPadding(0,dp(4),0,0);card.addView(b);card.setOnClickListener(v->derivedDetail(first));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));content.addView(card,p);}

    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static int count(List<PrimeBriefStore.Item> xs,String kind){int n=0;for(PrimeBriefStore.Item x:xs)if(x!=null&&kind.equalsIgnoreCase(x.kind))n++;return n;}
    private static void addUnique(ArrayList<PrimeBriefStore.Item> out,Set<String> seen,List<PrimeBriefStore.Item> xs,int cap){if(xs==null)return;int n=0;for(PrimeBriefStore.Item x:xs){if(x==null)continue;String k=key(x);if(seen.add(k)){out.add(x);if(++n>=cap)break;}}}
    private static String key(PrimeBriefStore.Item x){if(x.threadId>0)return (x.kind==null?"":x.kind)+"|thread:"+x.threadId;String text=LocalSemanticEmbedder.norm((x.kind==null?"":x.kind)+" "+nz(x.title)+" "+nz(x.body));return text.length()>220?text.substring(0,220):text;}
    private static String nz(String s){return s==null?"":s.trim();}private static String clipLocal(String s,int n){String x=nz(s);return x.length()<=n?x:x.substring(0,n).trim()+"…";}
    private static final class Situation{final ArrayList<PrimeBriefStore.Item> items;Situation(ArrayList<PrimeBriefStore.Item> xs){items=xs;}}
}
