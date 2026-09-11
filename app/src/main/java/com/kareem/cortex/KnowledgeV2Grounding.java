package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/**
 * Direct structured grounding over Knowledge V2. This complements vector retrieval so Brain can
 * recover exact names, amounts, dates, references and entity-linked evidence even when an embedding
 * is weak or unavailable. Raw evidence remains the source of truth; results are mapped back to the
 * durable Knowledge V2 projection before they are exposed as Brain sources.
 */
public final class KnowledgeV2Grounding {
    private KnowledgeV2Grounding(){}

    public static ArrayList<SemanticHit> search(VaultDb db,String question,int limit){
        ArrayList<SemanticHit> out=new ArrayList<>();
        if(db==null||question==null||question.trim().isEmpty()||limit<=0)return out;
        SQLiteDatabase s=db.getReadableDatabase();
        KnowledgeV2Schema.ensure(s);
        Set<String> q=words(question);
        if(q.isEmpty())return out;

        ArrayList<Candidate> candidates=new ArrayList<>();
        Cursor c=s.rawQuery(
                "SELECT e.id,e.raw_text,e.observed_at,COALESCE(u.title,''),COALESCE(u.summary,''),COALESCE(u.category,''),"+
                "COALESCE((SELECT group_concat(f.object_value,' | ') FROM kv2_facts f JOIN kv2_fact_evidence fe ON fe.fact_id=f.id WHERE fe.evidence_id=e.id AND f.state='active'),''),"+
                "COALESCE((SELECT group_concat(n.canonical_name,' | ') FROM kv2_entity_mentions em JOIN entity_nodes n ON n.id=em.resolved_entity_id WHERE em.evidence_id=e.id AND n.status='active'),'') " +
                "FROM kv2_evidence e JOIN kv2_understanding u ON u.evidence_id=e.id " +
                "WHERE e.knowledge_eligible=1 AND e.self_reference_score<0.72 " +
                "ORDER BY e.observed_at DESC LIMIT 1400",null);
        try{
            while(c.moveToNext()){
                long evidenceId=c.getLong(0),observedAt=c.getLong(2);
                String raw=n(c.getString(1)),title=n(c.getString(3)),summary=n(c.getString(4)),category=n(c.getString(5)),facts=n(c.getString(6)),entities=n(c.getString(7));
                double score=score(q,question,title,summary,facts,entities,category,raw,observedAt);
                if(score<0.16)continue;
                String snippet=bestSnippet(question,title,summary,facts,entities,raw);
                candidates.add(new Candidate(evidenceId,score,snippet));
            }
        }finally{c.close();}

        candidates.sort((a,b)->Double.compare(b.score,a.score));
        LinkedHashSet<Long> seenItems=new LinkedHashSet<>();
        for(Candidate x:candidates){
            long itemId=projectionItemId(s,x.evidenceId);
            if(itemId<=0||!seenItems.add(itemId))continue;
            KnowledgeItem item=db.getById(itemId);
            if(item==null)continue;
            out.add(new SemanticHit(item,x.score,x.snippet));
            if(out.size()>=limit)break;
        }
        return out;
    }

    public static ArrayList<SemanticHit> merge(ArrayList<SemanticHit> semantic,ArrayList<SemanticHit> structured,int limit){
        LinkedHashMap<Long,SemanticHit> best=new LinkedHashMap<>();
        if(semantic!=null)for(SemanticHit h:semantic)putBest(best,h);
        if(structured!=null)for(SemanticHit h:structured)putBest(best,h);
        ArrayList<SemanticHit> out=new ArrayList<>(best.values());
        out.sort((a,b)->Double.compare(b.score,a.score));
        if(out.size()>limit)return new ArrayList<>(out.subList(0,limit));
        return out;
    }

    private static void putBest(Map<Long,SemanticHit> map,SemanticHit h){
        if(h==null||h.item==null)return;
        SemanticHit old=map.get(h.item.id);
        if(old==null||h.score>old.score)map.put(h.item.id,h);
    }

    private static long projectionItemId(SQLiteDatabase s,long evidenceId){
        Cursor c=s.rawQuery("SELECT id FROM knowledge_items WHERE source='knowledge_v2' AND metadata_json LIKE ? ORDER BY updated_at DESC LIMIT 1",
                new String[]{"%\"evidence_id\":"+evidenceId+"%"});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static double score(Set<String> q,String original,String title,String summary,String facts,String entities,String category,String raw,long observedAt){
        String qNorm=LocalSemanticEmbedder.norm(original);
        String strong=LocalSemanticEmbedder.norm(title+" "+entities+" "+facts);
        String medium=LocalSemanticEmbedder.norm(summary+" "+category);
        String weak=LocalSemanticEmbedder.norm(raw);
        int strongHits=overlap(q,strong),mediumHits=overlap(q,medium),weakHits=overlap(q,weak);
        int total=Math.max(1,q.size());
        double coverage=Math.min(1.0,(strongHits*1.25+mediumHits*.85+weakHits*.35)/total);
        double exact=(!qNorm.isEmpty()&&(strong.contains(qNorm)||medium.contains(qNorm)))?.22:0;
        double entityBoost=strongHits>0&&!entities.isEmpty()?.10:0;
        long age=Math.max(0,System.currentTimeMillis()-observedAt);
        double recency=age<7L*86400000L?.05:(age<30L*86400000L?.025:0);
        return Math.min(.97,.10+coverage*.62+exact+entityBoost+recency);
    }

    private static int overlap(Set<String> q,String text){
        if(text==null||text.isEmpty())return 0;Set<String> w=words(text);int n=0;for(String x:q)if(w.contains(x))n++;return n;
    }

    private static String bestSnippet(String question,String title,String summary,String facts,String entities,String raw){
        String q=LocalSemanticEmbedder.norm(question);
        String[] choices={summary,facts,entities,title,raw};
        String best="";int bestHits=-1;
        Set<String> qw=words(q);
        for(String x:choices){String clean=clip(n(x),420);if(clean.isEmpty())continue;int hits=overlap(qw,LocalSemanticEmbedder.norm(clean));if(hits>bestHits){bestHits=hits;best=clean;}}
        return best.isEmpty()?"Structured Cortex evidence":best;
    }

    private static Set<String> words(String raw){
        LinkedHashSet<String> out=new LinkedHashSet<>();String x=LocalSemanticEmbedder.norm(raw);
        for(String w:x.split("[^\\p{L}\\p{Nd}]+"))if(w.length()>=2&&!STOP.contains(w))out.add(w);
        return out;
    }

    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ");return x.length()<=max?x:x.substring(0,max)+"…";}
    private static String n(String s){return s==null?"":s.trim();}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
            "the","and","for","with","from","this","that","what","when","where","who","how","about","have","has","was","were","are","is","to","of","in","on","my","me","i",
            "ايه","إيه","اللي","ده","دا","دي","فى","في","من","على","عن","انا","أنا","هو","هي","كان","كانت","مع"
    ));

    private static final class Candidate{
        final long evidenceId;final double score;final String snippet;
        Candidate(long evidenceId,double score,String snippet){this.evidenceId=evidenceId;this.score=score;this.snippet=snippet;}
    }
}
