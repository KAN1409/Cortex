package com.kareem.cortex;

import android.content.Intent;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/**
 * Top-level Now destination.
 *
 * v84 rule: Now consumes only the already-judged projection. It must never merge legacy
 * cognitive_surface decisions or re-judge items with CortexPersonalPolicy helpers.
 */
public final class NowActivity extends PremiumHomeActivity {
    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(2),dp(8),dp(2),dp(8));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=CortexUi.plain(this,"Now",30,CortexUi.TEXT);CortexUi.medium(title);titles.addView(title);
        TextView sub=CortexUi.text(this,"What deserves your attention right now.",11,CortexUi.MUTED);sub.setPadding(0,dp(3),0,0);titles.addView(sub);
        TextView settings=CortexUi.chip(this,"Settings",CortexUi.MUTED,false);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});head.addView(settings,new LinearLayout.LayoutParams(-2,dp(36)));
        content.addView(head);

        TextView loading=CortexUi.plain(this,"CHECKING CURRENT PRIORITIES…",9,CortexUi.FAINT);loading.setPadding(dp(2),dp(22),0,dp(10));content.addView(loading);
        CortexUi.addBottomNav(this,root,"now",null);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;
        while(content.getChildCount()>1)content.removeViewAt(1);

        LinearLayout status=CortexPipelineStatusBar.build(this,db);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0,dp(10),0,dp(4));
        content.addView(status,sp);

        ArrayList<PrimeBriefStore.Item> judged=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();

        // CortexJudgedBriefProjection already passed these sections through CortexAttentionJudge.
        addUnique(judged,seen,s.actions,12);
        addUnique(judged,seen,s.waiting,12);
        addUnique(judged,seen,s.decisions,12);

        // Final defensive quality gate only; this is not another attention decision.
        judged.removeIf(x->NowQualityPolicy.suppress(x.kind,x.source,x.title,x.body)
                ||AttentionNoisePolicy.suppress(x.source,x.title,x.body,x.kind,""));

        int maxNow=CortexPersonalPolicy.maxNowItems(this);
        int shown=0;

        for(String kind:new String[]{"ACTION","WAITING","DECISION"}){
            if(shown>=maxNow)break;
            int count=0;
            for(PrimeBriefStore.Item x:judged)if(kind.equalsIgnoreCase(x.kind))count++;
            if(count==0)continue;

            String heading="ACTION".equals(kind)?"Needs you":
                    ("WAITING".equals(kind)?"Waiting for someone / follow-up":"Decisions");
            content.addView(CortexUi.section(this,heading));

            for(PrimeBriefStore.Item x:judged){
                if(shown>=maxNow)break;
                if(!kind.equalsIgnoreCase(x.kind))continue;
                derivedRow(x);
                shown++;
            }
        }

        if(shown==0){
            LinearLayout card=CortexUi.card(this,24);card.setPadding(dp(18),dp(24),dp(18),dp(24));
            TextView h=CortexUi.plain(this,"Nothing needs you right now",19,CortexUi.TEXT);CortexUi.medium(h);card.addView(h);
            TextView b=CortexUi.text(this,
                    "Cortex is observing, but CortexAttentionJudge did not find anything that crosses your current attention policy.",
                    12,CortexUi.MUTED);
            b.setPadding(0,dp(7),0,0);card.addView(b);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);content.addView(card,p);
        }
    }

    private static void addUnique(ArrayList<PrimeBriefStore.Item> out,Set<String> seen,List<PrimeBriefStore.Item> xs,int cap){
        if(xs==null)return;
        int n=0;
        for(PrimeBriefStore.Item x:xs){
            if(x==null)continue;
            String k=key(x);
            if(seen.add(k)){
                out.add(x);
                if(++n>=cap)break;
            }
        }
    }

    private static String key(PrimeBriefStore.Item x){
        if(x.threadId>0)return (x.kind==null?"":x.kind)+"|thread:"+x.threadId;
        String text=LocalSemanticEmbedder.norm((x.kind==null?"":x.kind)+" "+(x.title==null?"":x.title)+" "+(x.body==null?"":x.body));
        return text.length()>220?text.substring(0,220):text;
    }
}
