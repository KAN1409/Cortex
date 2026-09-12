package com.kareem.cortex;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/**
 * Premium top-level attention surface.
 * Consumes only the already-judged projection; presentation never re-decides attention.
 */
public final class NowActivity extends PremiumHomeActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(28));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        content.addView(topBar());
        TextView loading=CortexUi.eyebrow(this,"Building current picture",CortexUi.FAINT);loading.setPadding(dp(2),dp(28),0,dp(12));content.addView(loading);

        CortexUi.addBottomNav(this,root,"now",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    private LinearLayout topBar(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(8),dp(1),dp(10));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView cortex=CortexUi.eyebrow(this,"Cortex",CortexUi.LIME);brand.addView(cortex);
        TextView title=CortexUi.plain(this,"Now",32,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(1),0,0);brand.addView(title);
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));
        return row;
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;
        while(content.getChildCount()>1)content.removeViewAt(1);

        ArrayList<PrimeBriefStore.Item> judged=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        addUnique(judged,seen,s.actions,12);addUnique(judged,seen,s.waiting,12);addUnique(judged,seen,s.decisions,12);
        judged.removeIf(x->NowQualityPolicy.suppress(x.kind,x.source,x.title,x.body)||AttentionNoisePolicy.suppress(x.source,x.title,x.body,x.kind,""));

        int actions=count(judged,"ACTION"),waiting=count(judged,"WAITING"),decisions=count(judged,"DECISION");
        int maxNow=CortexPersonalPolicy.maxNowItems(this);
        int available=Math.min(maxNow,actions+waiting+decisions);

        content.addView(attentionHero(available,actions,waiting,decisions),margins(0,4,0,0));

        LinearLayout shortcuts=new LinearLayout(this);shortcuts.setOrientation(LinearLayout.HORIZONTAL);shortcuts.setPadding(0,dp(11),0,0);
        TextView ask=CortexUi.action(this,"Ask Cortex",CortexUi.LIME,true);ask.setOnClickListener(v->CortexActionExecutor.openBrain(this,0,"Review my current grounded Cortex context. Tell me what matters most now, what is connected, and the highest-leverage next step."));shortcuts.addView(ask,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView brief=CortexUi.action(this,"Daily brief",CortexUi.MUTED,false);brief.setOnClickListener(v->showComposed(false));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(44),1);bp.setMargins(dp(8),0,0,0);shortcuts.addView(brief,bp);content.addView(shortcuts);

        LinearLayout status=CortexPipelineStatusBar.build(this,db);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,dp(14),0,0);content.addView(status,sp);

        int shown=0;
        for(String kind:new String[]{"ACTION","WAITING","DECISION"}){
            if(shown>=maxNow)break;
            int c=count(judged,kind);if(c==0)continue;
            String heading="ACTION".equals(kind)?"Needs you":("WAITING".equals(kind)?"Waiting":"Decisions");
            int color="ACTION".equals(kind)?CortexUi.LIME:("WAITING".equals(kind)?CortexUi.YELLOW:CortexUi.OLIVE);
            content.addView(sectionHeader(heading,c,color));
            for(PrimeBriefStore.Item x:judged){
                if(shown>=maxNow)break;if(!kind.equalsIgnoreCase(x.kind))continue;derivedRow(x);shown++;
            }
        }

        if(shown==0)content.addView(clearState(),margins(0,18,0,0));
    }

    private LinearLayout attentionHero(int available,int actions,int waiting,int decisions){
        LinearLayout card=CortexUi.card(this,26);card.setPadding(dp(18),dp(17),dp(18),dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView eye=CortexUi.eyebrow(this,"Current attention",available>0?CortexUi.LIME:CortexUi.GREEN);top.addView(eye,new LinearLayout.LayoutParams(0,-2,1));TextView live=CortexUi.chip(this,available>0?available+" active":"clear",available>0?CortexUi.LIME:CortexUi.GREEN,true);top.addView(live,new LinearLayout.LayoutParams(-2,dp(30)));card.addView(top);
        String headline=available==0?"Nothing needs you right now":(available==1?"One thing deserves your attention":available+" things deserve your attention");
        TextView h=CortexUi.plain(this,headline,23,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(12),0,0);card.addView(h);
        String copy=available==0?"Cortex is still observing. Nothing currently crosses your attention policy.":"Already filtered from the noise — grounded items only, ordered around what is most useful to act on now.";
        TextView b=CortexUi.text(this,copy,12,CortexUi.MUTED);b.setPadding(0,dp(6),0,dp(14));card.addView(b);

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(CortexUi.metric(this,String.valueOf(actions),"NEEDS YOU",actions>0?CortexUi.LIME:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,dp(68),1);wp.setMargins(dp(7),0,dp(7),0);metrics.addView(CortexUi.metric(this,String.valueOf(waiting),"WAITING",waiting>0?CortexUi.YELLOW:CortexUi.FAINT),wp);
        metrics.addView(CortexUi.metric(this,String.valueOf(decisions),"DECISIONS",decisions>0?CortexUi.OLIVE:CortexUi.FAINT),new LinearLayout.LayoutParams(0,dp(68),1));card.addView(metrics);
        return card;
    }

    private LinearLayout sectionHeader(String label,int count,int color){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(1),dp(24),dp(1),dp(9));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,color,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(6),dp(6)));
        TextView h=CortexUi.plain(this,label,12,CortexUi.TEXT);CortexUi.medium(h);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMargins(dp(9),0,0,0);row.addView(h,hp);
        TextView n=CortexUi.plain(this,String.valueOf(count),10,CortexUi.MUTED);row.addView(n);return row;
    }

    private LinearLayout clearState(){
        LinearLayout card=CortexUi.card(this,24);card.setPadding(dp(18),dp(22),dp(18),dp(22));
        TextView eye=CortexUi.eyebrow(this,"Quiet by design",CortexUi.GREEN);card.addView(eye);
        TextView h=CortexUi.plain(this,"You're clear",21,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(8),0,0);card.addView(h);
        TextView b=CortexUi.text(this,"Cortex is observing in the background. New information will surface here only when it earns your attention.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);card.addView(b);return card;
    }

    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private static int count(List<PrimeBriefStore.Item> xs,String kind){int n=0;for(PrimeBriefStore.Item x:xs)if(x!=null&&kind.equalsIgnoreCase(x.kind))n++;return n;}

    private static void addUnique(ArrayList<PrimeBriefStore.Item> out,Set<String> seen,List<PrimeBriefStore.Item> xs,int cap){
        if(xs==null)return;int n=0;for(PrimeBriefStore.Item x:xs){if(x==null)continue;String k=key(x);if(seen.add(k)){out.add(x);if(++n>=cap)break;}}
    }

    private static String key(PrimeBriefStore.Item x){
        if(x.threadId>0)return (x.kind==null?"":x.kind)+"|thread:"+x.threadId;
        String text=LocalSemanticEmbedder.norm((x.kind==null?"":x.kind)+" "+(x.title==null?"":x.title)+" "+(x.body==null?"":x.body));
        return text.length()>220?text.substring(0,220):text;
    }
}
