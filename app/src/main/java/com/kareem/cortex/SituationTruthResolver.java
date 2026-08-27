package com.kareem.cortex;

import android.database.Cursor;
import java.util.*;

/**
 * V4 truth/situation layer between stored evidence and cognitive retrieval.
 * It reconciles lifecycle state before ranking, then presents canonical situations
 * instead of treating every semantically-similar memory as a live obligation.
 */
public final class SituationTruthResolver {
    private SituationTruthResolver(){}

    private static final Set<String> LIVE_KINDS=new HashSet<>(Arrays.asList(
            "ACTION","REMINDER","WAITING","DECISION","ALERT","CHANGE","GOAL_SIGNAL","PROJECT_CANDIDATE"));

    public static GroundedAnswer tryAnswer(VaultDb db,String question){
        String q=n(question);
        if(!(has(q,"attention right now","needs my attention","ongoing situations","ongoing situation","episodes","upcoming deadlines","appointments","reminders matter","situations, not memories","situations not memories","what is still open","what's still open","what is open","محتاج انتباهي","محتاج اهتمام","المواقف","المواعيد","التذكيرات","الحاجات المفتوحة"))) return null;

        ArrayList<Situation> all=liveSituations(db,120);
        if(all.isEmpty()) return new GroundedAnswer(question,"I don't have a reconciled live situation that needs surfacing right now.",0.62,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());

        long now=System.currentTimeMillis();
        for(Situation s:all) s.rank=rank(s,now);
        all.sort((a,b)->Double.compare(b.rank,a.rank));

        ArrayList<Situation> chosen=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        for(Situation s:all){
            String key=canonical(s.title+" "+s.body);
            if(key.isEmpty()||!seen.add(key))continue;
            chosen.add(s); if(chosen.size()>=6)break;
        }

        ArrayList<SemanticHit> sources=new ArrayList<>();
        StringBuilder ans=new StringBuilder("The live situations I can support after lifecycle/temporal reconciliation are:\n");
        int idx=1;
        for(Situation s:chosen){
            String timing=timing(s,now);
            String next=nextMove(s);
            ans.append("• ").append(s.title);
            if(!timing.isEmpty())ans.append(" — ").append(timing);
            if(!next.isEmpty())ans.append(". Next: ").append(next);
            ArrayList<SemanticHit> ev=SemanticIndex.searchForAskRaw(db,s.title+" "+s.body,2);
            if(!ev.isEmpty()){
                SemanticHit h=ev.get(0); boolean exists=false; for(SemanticHit old:sources)if(old.item.id==h.item.id){exists=true;break;}
                if(!exists){sources.add(h);ans.append(" [M").append(sources.size()).append("]");}
            }
            ans.append('\n'); idx++;
        }
        return new GroundedAnswer(question,ans.toString().trim(),0.86,sources,new ArrayList<String>(),new ArrayList<String>());
    }

    /** Ask retrieval guard: semantically similar evidence is not allowed to resurrect a closed/dismissed obligation. */
    public static boolean allowAskMemory(VaultDb db,KnowledgeItem item){
        if(item==null)return false;
        String text=canonical(item.title+" "+item.summary+" "+item.extractedText+" "+item.rawText);
        if(text.isEmpty())return true;
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,body,state,resolved_at FROM derived_items WHERE state IN ('dismissed','resolved','done','closed') ORDER BY updated_at DESC LIMIT 240",null);
        try{while(c.moveToNext()){
            String d=canonical(n(c.getString(0))+" "+n(c.getString(1)));
            if(d.length()<16)continue;
            double sim=tokenOverlap(text,d);
            if(sim>=0.72)return false;
        }}finally{c.close();}
        return true;
    }

    private static ArrayList<Situation> liveSituations(VaultDb db,int limit){
        ArrayList<Situation> out=new ArrayList<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,kind,title,body,state,confidence,importance,thread_id,anchor_signal_id,created_at,updated_at,resolved_at FROM derived_items WHERE state IN ('open','pending') ORDER BY updated_at DESC LIMIT ?",new String[]{String.valueOf(limit*3)});
        try{while(c.moveToNext()){
            String kind=n(c.getString(1)).toUpperCase(Locale.ROOT); if(!LIVE_KINDS.contains(kind))continue;
            Situation s=new Situation();s.id=c.getLong(0);s.kind=kind;s.title=n(c.getString(2));s.body=n(c.getString(3));s.state=n(c.getString(4));s.confidence=c.getDouble(5);s.importance=c.getInt(6);s.threadId=c.getLong(7);s.signalId=c.getLong(8);s.createdAt=c.getLong(9);s.updatedAt=c.getLong(10);s.targetAt=TemporalResolver.resolveForAttention(s.title+" "+s.body,s.updatedAt>0?s.updatedAt:System.currentTimeMillis());
            if(shadowedByClosed(db,s))continue;
            out.add(s);if(out.size()>=limit)break;
        }}finally{c.close();}
        return out;
    }

    private static boolean shadowedByClosed(VaultDb db,Situation live){
        String key=canonical(live.title+" "+live.body); if(key.isEmpty())return false;
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,body,updated_at FROM derived_items WHERE id<>? AND state IN ('dismissed','resolved','done','closed') AND updated_at>=? ORDER BY updated_at DESC LIMIT 80",new String[]{String.valueOf(live.id),String.valueOf(Math.max(0,live.updatedAt-86400000L))});
        try{while(c.moveToNext()){
            String x=canonical(n(c.getString(0))+" "+n(c.getString(1)));
            if(tokenOverlap(key,x)>=0.78 && c.getLong(2)>=live.updatedAt)return true;
        }}finally{c.close();}
        return false;
    }

    private static double rank(Situation s,long now){
        double importance=Math.max(0,Math.min(1,s.importance/100.0));
        double confidence=Math.max(0,Math.min(1,s.confidence));
        double temporal=.35;
        if(s.targetAt>0){double h=(s.targetAt-now)/3600000.0;if(h<=0)temporal=.92;else if(h<=6)temporal=1;else if(h<=24)temporal=.94;else if(h<=48)temporal=.75;else if(h<=96)temporal=.48;else temporal=.25;}
        double action=("ACTION".equals(s.kind)||"REMINDER".equals(s.kind))?1:("WAITING".equals(s.kind)?.62:("DECISION".equals(s.kind)?.72:.50));
        double age=Math.max(0,(now-s.updatedAt)/3600000.0);double freshness=Math.exp(-age/168.0);
        return .34*temporal+.28*action+.20*importance+.10*confidence+.08*freshness;
    }

    private static String timing(Situation s,long now){
        if(s.targetAt<=0)return "unresolved";double h=(s.targetAt-now)/3600000.0;
        if(h<=0)return "stated time reached/passed";if(h<=6)return "within a few hours";if(h<=24)return "within 24 hours";if(h<=48)return "within about 2 days";return "future-dated";
    }
    private static String nextMove(Situation s){
        if("WAITING".equals(s.kind))return "check whether the dependency changed, then follow up only if still unresolved";
        if("DECISION".equals(s.kind))return "resolve the decision or identify the missing information";
        if("ALERT".equals(s.kind)||"CHANGE".equals(s.kind))return "verify impact before creating an action";
        return "complete it, schedule it, or explicitly dismiss it so it cannot resurface";
    }

    private static String canonical(String s){String x=LocalSemanticEmbedder.norm(n(s));StringBuilder b=new StringBuilder();for(String w:x.split("[^\\p{L}\\p{Nd}]+")){if(w.length()<2||STOP.contains(w))continue;if(b.length()>0)b.append(' ');b.append(w);}return b.toString();}
    private static double tokenOverlap(String a,String b){HashSet<String>x=new HashSet<>(Arrays.asList(a.split(" "))),y=new HashSet<>(Arrays.asList(b.split(" ")));x.remove("");y.remove("");if(x.isEmpty()||y.isEmpty())return 0;int inter=0;for(String w:x)if(y.contains(w))inter++;return inter/(double)Math.min(x.size(),y.size());}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","a","an","to","of","and","or","for","in","on","at","is","are","be","my","your","this","that","من","في","على","الى","إلى","اللي","ده","دي","و","او","أو"));
    private static boolean has(String q,String... xs){String z=LocalSemanticEmbedder.norm(q);for(String x:xs)if(z.contains(LocalSemanticEmbedder.norm(x)))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}
    private static final class Situation{long id,threadId,signalId,createdAt,updatedAt,targetAt;String kind,title,body,state;double confidence,rank;int importance;}
}
