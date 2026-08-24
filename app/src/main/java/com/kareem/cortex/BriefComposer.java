package com.kareem.cortex;

import java.text.SimpleDateFormat;
import java.util.*;

/** Human brief generated from real PRIME state only; never injects fake tasks or decisions. */
public final class BriefComposer {
    private BriefComposer(){}
    public static String compose(VaultDb db,boolean weekly){PrimeBriefStore.Snapshot s=PrimeBriefStore.load(db);StringBuilder b=new StringBuilder();String label=weekly?"Weekly Cortex Brief":"Daily Cortex Brief";b.append(label).append(" · ").append(new SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(new Date())).append("\n\n");
        if(s.empty()){b.append("Nothing in the current grounded Cortex state needs a brief right now.");return b.toString();}
        section(b,"Needs you",s.actions,weekly?10:6);section(b,"Waiting",s.waiting,weekly?8:5);section(b,"Decisions",s.decisions,weekly?8:5);section(b,"Worth knowing",s.worthKnowing,weekly?8:5);section(b,"Changed & evolving",s.changes,weekly?8:5);
        if(!s.reviews.isEmpty()){b.append("\nNeeds your review\n");for(int i=0;i<Math.min(weekly?8:4,s.reviews.size());i++){ReviewQueueStore.Item x=s.reviews.get(i);b.append("• ").append(clean(x.title)).append(" — possible ").append(clean(x.candidateKind).toLowerCase(Locale.ROOT)).append('\n');}}
        if(!s.recent.isEmpty()){b.append("\nRecent captures\n");long cutoff=System.currentTimeMillis()-(weekly?7L:1L)*24L*60L*60L*1000L;int added=0;for(KnowledgeItem k:s.recent){if(k.createdAt<cutoff)continue;b.append("• ").append(clean(k.title)).append(" — ").append(clean(k.status).replace('_',' ')).append('\n');if(++added>=(weekly?10:6))break;}}
        return b.toString().trim();}
    private static void section(StringBuilder b,String title,ArrayList<PrimeBriefStore.Item> xs,int limit){if(xs==null||xs.isEmpty())return;b.append('\n').append(title).append('\n');for(int i=0;i<Math.min(limit,xs.size());i++){PrimeBriefStore.Item x=xs.get(i);String text=!clean(x.body).isEmpty()?clean(x.body):clean(x.title);b.append("• ").append(clip(text,220)).append("  [").append(Math.round(x.confidence*100)).append("%]").append('\n');}}
    private static String clean(String s){return s==null?"":s.trim();}private static String clip(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
}
