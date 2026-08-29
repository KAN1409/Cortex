package com.kareem.cortex;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Current-truth layer used by Ask before historical memory retrieval. */
public final class SituationTruthResolver {
    private SituationTruthResolver(){}

    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        String q=n(question);
        if(!isSituationQuestion(q))return null;
        try{BrainSituationStore.reconcile(db);}catch(Throwable ignored){}
        ArrayList<BrainSituationStore.Item> all=BrainSituationStore.current(db,160);
        long now=System.currentTimeMillis();
        ArrayList<BrainSituationStore.Item> eligible=new ArrayList<>();
        for(BrainSituationStore.Item s:all){if(!productRelevant(s,now))continue;eligible.add(s);}
        eligible.sort((a,b)->Double.compare(rank(b,now),rank(a,now)));

        if(eligible.isEmpty())return new GroundedAnswer(question,
                "I don't have a grounded current situation that needs surfacing right now.",
                0.86,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());

        boolean wantsWaiting=has(q,"waiting on","who am i waiting","مين مستني","مستني مين","waiting for");
        boolean wantsChanged=has(q,"what changed","changed today","changed recently","ايه اتغير","إيه اتغير","اتغير النهاردة");
        boolean wantsNeeds=has(q,"needs my attention","attention right now","what needs me","محتاج انتباهي","محتاج اهتمام","محتاجني");

        ArrayList<BrainSituationStore.Item> chosen=new ArrayList<>();
        for(BrainSituationStore.Item s:eligible){
            String k=n(s.kind).toUpperCase(Locale.ROOT);
            if(wantsWaiting&&!"WAITING".equals(k))continue;
            if(wantsChanged&&!("CHANGE".equals(k)||"ALERT".equals(k)||"EVENT".equals(k)||now-s.lastChangedAt<24L*60L*60L*1000L))continue;
            if(wantsNeeds&&!("ACTION".equals(k)||"REMINDER".equals(k)||"DECISION".equals(k)||"WAITING".equals(k)))continue;
            chosen.add(s);if(chosen.size()>=6)break;
        }
        if(chosen.isEmpty())chosen.addAll(eligible.subList(0,Math.min(6,eligible.size())));

        ArrayList<SemanticHit> sources=new ArrayList<>();
        StringBuilder answer=new StringBuilder();
        if(wantsWaiting)answer.append("You're currently waiting on:\n");
        else if(wantsChanged)answer.append("What changed in the current picture:\n");
        else answer.append("Current grounded situations:\n");

        for(BrainSituationStore.Item s:chosen){
            answer.append("• ").append(display(s));
            String timing=timing(s,now);if(!timing.isEmpty())answer.append(" — ").append(timing);
            if(s.evidenceCount>1)answer.append(" · ").append(s.evidenceCount).append(" linked evidence items");
            SemanticHit support=firstSupporting(s,SemanticIndex.searchForAskRaw(db,s.title+" "+s.body,4));
            if(support!=null&&!containsSource(sources,support)){
                sources.add(support);answer.append(" [M").append(sources.size()).append("]");
            }
            answer.append('\n');
        }
        return new GroundedAnswer(question,answer.toString().trim(),0.94,sources,
                new ArrayList<String>(),new ArrayList<String>());
    }

    public static boolean allowAskMemory(VaultDb db,KnowledgeItem item){
        if(item==null)return false;
        String text=canonical(item.title+" "+item.summary+" "+item.extractedText+" "+item.rawText);
        if(text.isEmpty())return true;
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT title,body,state,resolved_at FROM derived_items WHERE state IN ('dismissed','resolved','done','closed','superseded','expired') ORDER BY updated_at DESC LIMIT 320",null);
        try{
            while(c.moveToNext()){
                String d=canonical(n(c.getString(0))+" "+n(c.getString(1)));
                if(d.length()<16)continue;
                if(tokenOverlap(text,d)>=0.72)return false;
            }
        }finally{c.close();}
        return true;
    }

    private static boolean isSituationQuestion(String q){
        return has(q,"attention right now","needs my attention","what needs me","ongoing situations","ongoing situation",
                "upcoming deadlines","appointments","reminders matter","situations not memories","what is still open",
                "what's still open","what is open","what changed","changed today","changed recently","waiting on",
                "who am i waiting","brief me on today","today brief","محتاج انتباهي","محتاج اهتمام","محتاجني",
                "المواقف","المواعيد","التذكيرات","الحاجات المفتوحة","مين مستني","مستني مين","اتغير النهاردة","ايه اتغير","إيه اتغير");
    }

    private static boolean productRelevant(BrainSituationStore.Item s,long now){
        if(s==null||!"active".equalsIgnoreCase(s.state))return false;
        String k=n(s.kind).toUpperCase(Locale.ROOT);
        if("CONTENT".equals(k)&&s.importance<70)return false;
        if(("EVENT".equals(k)||"CHANGE".equals(k)||"ALERT".equals(k))&&now-s.lastChangedAt>72L*60L*60L*1000L)return false;
        if(s.dueAt>0&&now-s.dueAt>96L*60L*60L*1000L)return false;
        return true;
    }

    private static double rank(BrainSituationStore.Item s,long now){
        String k=n(s.kind).toUpperCase(Locale.ROOT);
        double action="ACTION".equals(k)||"REMINDER".equals(k)?1.0:"DECISION".equals(k)?.82:"WAITING".equals(k)?.70:.48;
        double time=.28;
        if(s.dueAt>0){double h=(s.dueAt-now)/3600000.0;if(h<=0&&h>=-12)time=.95;else if(h>0&&h<=6)time=1;else if(h<=24)time=.80;else if(h<=48)time=.55;else time=.22;}
        double freshness=Math.exp(-Math.max(0,now-s.lastChangedAt)/3600000.0/96.0);
        return .34*action+.28*time+.18*clamp01(s.importance/100.0)+.10*clamp01(s.confidence)+.10*freshness;
    }

    private static String display(BrainSituationStore.Item s){String t=n(s.title);if(t.isEmpty())t=n(s.body);if(t.isEmpty())t=friendly(s.kind);return t;}
    private static String timing(BrainSituationStore.Item s,long now){
        if(s.dueAt<=0){if(s.lastChangedAt>0&&now-s.lastChangedAt<2L*60L*60L*1000L)return"changed recently";return"current";}
        double h=(s.dueAt-now)/3600000.0;if(h<0)return h>=-12?"due / recently overdue":"past-dated";if(h<=6)return"within a few hours";if(h<=24)return"within 24 hours";if(h<=48)return"within about 2 days";return"scheduled ahead";
    }

    private static SemanticHit firstSupporting(BrainSituationStore.Item s,ArrayList<SemanticHit> hits){
        String target=canonical(s.title+" "+s.body);for(SemanticHit h:hits){String evidence=canonical((h.item.title==null?"":h.item.title)+" "+(h.item.summary==null?"":h.item.summary)+" "+h.snippet);if(tokenOverlap(target,evidence)>=0.30)return h;}return null;
    }
    private static boolean containsSource(ArrayList<SemanticHit> xs,SemanticHit x){for(SemanticHit old:xs)if(old.item.id==x.item.id)return true;return false;}
    private static String friendly(String kind){String x=n(kind).toLowerCase(Locale.ROOT).replace('_',' ');return x.isEmpty()?"Current situation":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static String canonical(String s){String x=LocalSemanticEmbedder.norm(n(s));StringBuilder b=new StringBuilder();for(String w:x.split("[^\\p{L}\\p{Nd}]+")){if(w.length()<2||STOP.contains(w))continue;if(b.length()>0)b.append(' ');b.append(w);}return b.toString();}
    private static double tokenOverlap(String a,String b){HashSet<String>x=new HashSet<>(Arrays.asList(a.split(" "))),y=new HashSet<>(Arrays.asList(b.split(" ")));x.remove("");y.remove("");if(x.isEmpty()||y.isEmpty())return 0;int inter=0;for(String w:x)if(y.contains(w))inter++;return inter/(double)Math.min(x.size(),y.size());}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","a","an","to","of","and","or","for","in","on","at","is","are","be","my","your","this","that","من","في","على","الى","إلى","اللي","ده","دي","و","او","أو"));
    private static boolean has(String q,String...xs){String z=LocalSemanticEmbedder.norm(q);for(String x:xs)if(z.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static double clamp01(double x){return Math.max(0,Math.min(1,x));}
    private static String n(String s){return s==null?"":s.trim();}
}
