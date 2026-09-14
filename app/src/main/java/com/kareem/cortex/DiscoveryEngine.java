package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.*;

/**
 * Cortex Discovery Engine v1.
 * Converts analyzed evidence into situations, readable histories and critiqued discovery candidates.
 * No UI assumptions and no cloud/model dependency.
 */
public final class DiscoveryEngine {
    public static final String VERSION="discovery_engine_001";
    private DiscoveryEngine(){}

    public static long processAnalyzedItem(VaultDb vault,long itemId){
        if(vault==null||itemId<=0)return 0;
        KnowledgeItem item=vault.getById(itemId);
        if(item==null||!"analyzed".equals(item.status))return 0;
        SQLiteDatabase db=vault.getWritableDatabase();
        DiscoverySchema.ensure(db);

        ArrayList<String> entities=loadEntities(db,itemId);
        String space=DiscoveryPolicy.inferSpace(item);
        String topicKey=DiscoveryPolicy.topicKey(item,entities);
        String topicLabel=DiscoveryPolicy.topicLabel(item);
        String claimState=DiscoveryPolicy.claimState(item);
        double confidence=annotationConfidence(db,itemId);

        long now=System.currentTimeMillis();
        ContentValues a=new ContentValues();
        a.put("item_id",itemId);a.put("space",space);a.put("topic_key",topicKey);a.put("topic_label",topicLabel);
        a.put("claim_state",claimState);a.put("source_type",n(item.type));a.put("source_key",n(item.source));
        a.put("confidence",confidence);a.put("created_at",item.createdAt>0?item.createdAt:now);a.put("updated_at",now);
        db.insertWithOnConflict("discovery_annotations",null,a,SQLiteDatabase.CONFLICT_REPLACE);

        String situationKey="discovery|"+space.toLowerCase(Locale.ROOT)+"|"+topicKey;
        JSONObject meta=new JSONObject();
        try{meta.put("discovery_engine",VERSION).put("space",space).put("topic_key",topicKey);}catch(Exception ignored){}
        long situationId=UniversalEventStore.upsertSituation(db,situationKey,"discovery",topicLabel,
                preferredBody(item),"open",40,confidence,item.createdAt>0?item.createdAt:now,meta);

        ContentValues se=new ContentValues();
        se.put("situation_id",situationId);se.put("item_id",itemId);se.put("relation","supports");
        se.put("confidence",confidence);se.put("created_at",now);
        db.insertWithOnConflict("discovery_situation_evidence",null,se,SQLiteDatabase.CONFLICT_REPLACE);

        DiscoveryHistoryWriter.rebuild(db,situationId);
        generateForSituation(db,situationId);
        return situationId;
    }

    /** Bounded, resumable adoption of existing analyzed evidence. */
    public static int backfill(VaultDb vault,int max){
        if(vault==null||max<=0)return 0;
        SQLiteDatabase db=vault.getReadableDatabase();DiscoverySchema.ensure(db);
        Cursor c=db.rawQuery("SELECT k.id FROM knowledge_items k LEFT JOIN discovery_annotations d ON d.item_id=k.id "+
                "WHERE k.status='analyzed' AND d.item_id IS NULL ORDER BY k.updated_at DESC,k.id DESC LIMIT ?",
                new String[]{String.valueOf(Math.min(64,max))});
        ArrayList<Long> ids=new ArrayList<>();while(c.moveToNext())ids.add(c.getLong(0));c.close();
        int done=0;for(long id:ids){try{if(processAnalyzedItem(vault,id)>0)done++;}catch(Throwable ignored){}}
        return done;
    }

    public static ArrayList<Insight> topInsights(VaultDb vault,int limit){
        ArrayList<Insight> out=new ArrayList<>();if(vault==null||limit<=0)return out;
        SQLiteDatabase db=vault.getReadableDatabase();DiscoverySchema.ensure(db);
        Cursor c=db.rawQuery("SELECT id,situation_id,family,title,body,why_matters,why_now,confidence,score,evidence_count,updated_at "+
                "FROM discovery_candidates WHERE state='publishable' ORDER BY score DESC,updated_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        while(c.moveToNext())out.add(new Insight(c.getLong(0),c.getLong(1),s(c,2),s(c,3),s(c,4),s(c,5),s(c,6),c.getDouble(7),c.getDouble(8),c.getInt(9),c.getLong(10)));
        c.close();return out;
    }

    static void generateForSituation(SQLiteDatabase db,long situationId){
        EvidenceSet set=loadEvidence(db,situationId);
        if(set.items.isEmpty())return;
        detectOpenLoops(db,situationId,set);
        detectContradictions(db,situationId,set);
        detectMeaningfulChange(db,situationId,set);
        detectUnexpectedConnection(db,situationId,set);
        detectMissingLinks(db,situationId,set);
    }

    private static void detectOpenLoops(SQLiteDatabase db,long situationId,EvidenceSet set){
        Cursor c=db.rawQuery("SELECT a.item_id,a.action_text,a.due_text,a.created_at FROM actions a "+
                "JOIN discovery_situation_evidence se ON se.item_id=a.item_id WHERE se.situation_id=? AND a.status='open' ORDER BY a.created_at DESC LIMIT 12",
                new String[]{String.valueOf(situationId)});
        while(c.moveToNext()){
            long itemId=c.getLong(0);String action=s(c,1),due=s(c,2);
            if(action.isEmpty())continue;
            String body="Cortex found an open commitment or action: "+action+(due.isEmpty()?".":" · Due: "+due+".");
            Candidate x=new Candidate("OPEN_LOOP","Open loop worth checking",body,
                    "An unresolved commitment can become easy to miss once the original message or document disappears from view.",
                    "It is still marked open in the evidence currently indexed by Cortex.",0.78,0.70,0.72,0.72,0.18);
            storeCandidate(db,situationId,x,Collections.singletonList(itemId));
        }
        c.close();
    }

    private static void detectContradictions(SQLiteDatabase db,long situationId,EvidenceSet set){
        ArrayList<StateRow> states=new ArrayList<>();
        Cursor c=db.rawQuery("SELECT d.item_id,d.claim_state,k.created_at,k.title FROM discovery_annotations d "+
                "JOIN discovery_situation_evidence se ON se.item_id=d.item_id JOIN knowledge_items k ON k.id=d.item_id "+
                "WHERE se.situation_id=? AND COALESCE(d.claim_state,'')<>'' ORDER BY k.created_at DESC LIMIT 16",
                new String[]{String.valueOf(situationId)});
        while(c.moveToNext())states.add(new StateRow(c.getLong(0),s(c,1),c.getLong(2),s(c,3)));c.close();
        for(int i=0;i<states.size();i++)for(int j=i+1;j<states.size();j++){
            StateRow a=states.get(i),b=states.get(j);
            if(!DiscoveryPolicy.contradictory(a.state,b.state))continue;
            String body="Two pieces of evidence disagree about the same situation: '"+a.state+"' versus '"+b.state+"'. Cortex is not treating either as the final truth yet.";
            Candidate x=new Candidate("CONTRADICTION","Conflicting evidence needs resolution",body,
                    "A wrong status can change what you do next.","The conflict appeared inside one linked situation and remains unresolved.",0.86,0.86,0.82,0.78,0.10);
            storeCandidate(db,situationId,x,Arrays.asList(a.itemId,b.itemId));
            return;
        }
    }

    private static void detectMeaningfulChange(SQLiteDatabase db,long situationId,EvidenceSet set){
        if(set.items.size()<2)return;
        EvidenceRow latest=set.items.get(0),previous=set.items.get(1);
        String a=preferred(latest.summary,latest.extracted,latest.title),b=preferred(previous.summary,previous.extracted,previous.title);
        if(a.isEmpty()||b.isEmpty()||!StatefulMeaningPolicy.materialChange(b,a))return;
        long delta=Math.max(0,latest.when-previous.when);
        if(delta<60_000L&&latest.source.equals(previous.source))return;
        String body="New evidence materially changed the wording or state of this situation. Cortex kept both versions instead of silently replacing the older one.";
        Candidate x=new Candidate("MEANINGFUL_CHANGE","This situation changed",body,
                "A changed situation may invalidate an earlier assumption or decision.","The newest linked evidence differs materially from the previous evidence.",0.74,0.72,0.62,0.76,0.20);
        storeCandidate(db,situationId,x,Arrays.asList(latest.id,previous.id));
    }

    private static void detectUnexpectedConnection(SQLiteDatabase db,long situationId,EvidenceSet set){
        if(set.items.size()<2||set.sources.size()<2)return;
        String topic=set.topicKey;
        boolean strong=topic.startsWith("ref|")||topic.startsWith("entity|");
        if(!strong)return;
        ArrayList<Long> ids=new ArrayList<>();for(int i=0;i<Math.min(4,set.items.size());i++)ids.add(set.items.get(i).id);
        String body="The same strong identifier or entity appears across "+set.sources.size()+" different source(s). Cortex linked them into one situation rather than leaving them as isolated records.";
        Candidate x=new Candidate("UNEXPECTED_CONNECTION","Related evidence was hiding in different sources",body,
                "Cross-source links can reveal context that is invisible when each app or file is viewed separately.",
                "A new source joined an existing strongly identified situation.",0.80,0.78,0.64,0.70,0.12);
        storeCandidate(db,situationId,x,ids);
    }

    private static void detectMissingLinks(SQLiteDatabase db,long situationId,EvidenceSet set){
        Cursor c=db.rawQuery("SELECT a.item_id,a.action_text,a.due_text,a.created_at FROM actions a "+
                "JOIN discovery_situation_evidence se ON se.item_id=a.item_id WHERE se.situation_id=? AND a.status='open' AND COALESCE(a.due_text,'')<>'' ORDER BY a.created_at DESC LIMIT 8",
                new String[]{String.valueOf(situationId)});
        while(c.moveToNext()){
            long itemId=c.getLong(0),when=c.getLong(3);String action=s(c,1),due=s(c,2);
            Cursor done=db.rawQuery("SELECT 1 FROM discovery_situation_evidence se JOIN knowledge_items k ON k.id=se.item_id "+
                    "WHERE se.situation_id=? AND k.created_at>? AND (lower(COALESCE(k.summary,'')) LIKE '%complete%' OR lower(COALESCE(k.summary,'')) LIKE '%done%' OR COALESCE(k.summary,'') LIKE '%تم التنفيذ%' OR COALESCE(k.summary,'') LIKE '%اكتمل%') LIMIT 1",
                    new String[]{String.valueOf(situationId),String.valueOf(when)});
            boolean completed=done.moveToFirst();done.close();if(completed)continue;
            String body="Cortex found a dated/open action ('"+clip(action,180)+"', "+clip(due,80)+") but no later completion evidence inside the currently indexed sources.";
            Candidate x=new Candidate("MISSING_LINK","Expected completion evidence is missing",body,
                    "This may be an unfinished step, or simply a coverage gap that Cortex should not mistake for completion.",
                    "The action has a due/reference value and the linked history contains no later completion evidence.",0.72,0.80,0.78,0.76,0.28);
            storeCandidate(db,situationId,x,Collections.singletonList(itemId));
        }
        c.close();
    }

    private static long storeCandidate(SQLiteDatabase db,long situationId,Candidate x,List<Long> evidenceIds){
        int evidenceCount=evidenceIds==null?0:new LinkedHashSet<>(evidenceIds).size();
        double evidence=Math.min(1,0.52+0.16*Math.max(0,evidenceCount-1));
        double score=DiscoveryPolicy.score(x.novelty,0.86,x.consequence,evidence,x.timeliness,x.uncertainty);
        DiscoveryCritic.Verdict verdict=DiscoveryCritic.judge(x.family,x.title,x.body,evidenceCount,x.confidence,score);
        String fp=Fingerprint.text(VERSION+"|"+situationId+"|"+x.family+"|"+DiscoveryPolicy.norm(x.title)+"|"+DiscoveryPolicy.norm(x.body));
        long now=System.currentTimeMillis(),id=0;
        Cursor old=db.rawQuery("SELECT id FROM discovery_candidates WHERE fingerprint=? LIMIT 1",new String[]{fp});
        if(old.moveToFirst())id=old.getLong(0);old.close();

        ContentValues v=new ContentValues();
        v.put("situation_id",situationId);v.put("family",x.family);v.put("title",x.title);v.put("body",x.body);
        v.put("why_matters",x.whyMatters);v.put("why_now",x.whyNow);v.put("confidence",x.confidence);v.put("novelty",x.novelty);
        v.put("consequence",x.consequence);v.put("timeliness",x.timeliness);v.put("evidence_count",evidenceCount);v.put("score",score);
        v.put("state",verdict.keep?"publishable":"suppressed");v.put("critic_reason",verdict.reason);v.put("fingerprint",fp);v.put("updated_at",now);
        if(id>0)db.update("discovery_candidates",v,"id=?",new String[]{String.valueOf(id)});
        else{v.put("created_at",now);id=db.insertOrThrow("discovery_candidates",null,v);}
        if(id>0&&evidenceIds!=null)for(long itemId:new LinkedHashSet<>(evidenceIds)){
            ContentValues e=new ContentValues();e.put("candidate_id",id);e.put("item_id",itemId);e.put("relation","supports");e.put("created_at",now);
            db.insertWithOnConflict("discovery_candidate_evidence",null,e,SQLiteDatabase.CONFLICT_IGNORE);
        }
        return id;
    }

    private static EvidenceSet loadEvidence(SQLiteDatabase db,long situationId){
        EvidenceSet set=new EvidenceSet();
        Cursor c=db.rawQuery("SELECT k.id,k.created_at,k.title,k.summary,k.extracted_text,k.source,d.topic_key "+
                "FROM discovery_situation_evidence se JOIN knowledge_items k ON k.id=se.item_id "+
                "JOIN discovery_annotations d ON d.item_id=k.id WHERE se.situation_id=? ORDER BY k.created_at DESC,k.id DESC LIMIT 64",
                new String[]{String.valueOf(situationId)});
        while(c.moveToNext()){
            EvidenceRow r=new EvidenceRow(c.getLong(0),c.getLong(1),s(c,2),s(c,3),s(c,4),s(c,5));
            set.items.add(r);if(!r.source.isEmpty())set.sources.add(r.source);if(set.topicKey.isEmpty())set.topicKey=s(c,6);
        }c.close();return set;
    }

    private static ArrayList<String> loadEntities(SQLiteDatabase db,long itemId){
        ArrayList<String> out=new ArrayList<>();Cursor c=db.rawQuery("SELECT kind,value FROM entities WHERE item_id=? ORDER BY confidence DESC,id ASC LIMIT 24",new String[]{String.valueOf(itemId)});
        while(c.moveToNext())out.add(s(c,0)+": "+s(c,1));c.close();return out;
    }

    private static double annotationConfidence(SQLiteDatabase db,long itemId){
        Cursor c=db.rawQuery("SELECT MAX(confidence) FROM entities WHERE item_id=?",new String[]{String.valueOf(itemId)});
        double v=c.moveToFirst()&&!c.isNull(0)?c.getDouble(0):0;c.close();
        return Math.max(0.62,Math.min(0.95,v>0?v:0.72));
    }

    private static String preferredBody(KnowledgeItem k){return preferred(k.summary,k.extractedText,k.rawText);}
    private static String preferred(String a,String b,String c){if(!n(a).isEmpty())return n(a);if(!n(b).isEmpty())return n(b);return n(c);}
    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static String n(String s){return s==null?"":s.trim();}

    private static final class Candidate{
        final String family,title,body,whyMatters,whyNow;final double confidence,novelty,consequence,timeliness,uncertainty;
        Candidate(String f,String t,String b,String wm,String wn,double c,double n,double co,double ti,double u){family=f;title=t;body=b;whyMatters=wm;whyNow=wn;confidence=c;novelty=n;consequence=co;timeliness=ti;uncertainty=u;}
    }
    private static final class StateRow{final long itemId,when;final String state,title;StateRow(long i,String s,long w,String t){itemId=i;state=s;when=w;title=t;}}
    private static final class EvidenceRow{final long id,when;final String title,summary,extracted,source;EvidenceRow(long i,long w,String t,String s,String e,String so){id=i;when=w;title=t;summary=s;extracted=e;source=so;}}
    private static final class EvidenceSet{final ArrayList<EvidenceRow> items=new ArrayList<>();final LinkedHashSet<String> sources=new LinkedHashSet<>();String topicKey="";}

    public static final class Insight{
        public final long id,situationId,updatedAt;public final String family,title,body,whyMatters,whyNow;public final double confidence,score;public final int evidenceCount;
        Insight(long i,long s,String f,String t,String b,String wm,String wn,double c,double sc,int ec,long u){id=i;situationId=s;family=f;title=t;body=b;whyMatters=wm;whyNow=wn;confidence=c;score=sc;evidenceCount=ec;updatedAt=u;}
    }
}
