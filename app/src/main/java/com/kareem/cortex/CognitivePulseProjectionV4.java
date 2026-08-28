package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;

/** Read-only Stage-E Pulse projection: canonical Situations + time-aware Deep Brain influence. */
public final class CognitivePulseProjectionV4 {
    private CognitivePulseProjectionV4(){}

    public static Snapshot current(VaultDb db,int limit){
        if(db==null)return new Snapshot(Collections.<Item>emptyList(),0,0,0,0,0);
        CognitiveDeepBrainStoreV4.ensure(db);CognitiveStoreV4.ensure(db);CognitiveReasoningRunStoreV4.ensure(db);
        CognitiveReasoningFreshnessV4.Snapshot freshness=CognitiveReasoningFreshnessV4.current(db);
        int lim=Math.max(1,Math.min(12,limit));SQLiteDatabase sql=db.getReadableDatabase();ArrayList<Item>candidates=new ArrayList<>();long now=System.currentTimeMillis();String latestGeminiRequestId=latestAppliedGeminiRequestId(sql);
        String q="SELECT s.id,s.kind,s.state,s.headline,COALESCE(s.explanation,''),s.attention_score,s.interruption_score,s.confidence,s.relevant_from,s.relevant_until,"+
                "COALESCE((SELECT MIN(p.rank_order) FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id),0) AS brain_rank,"+
                "COALESCE((SELECT p.attention_score FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),0) AS brain_attention,"+
                "COALESCE((SELECT p.reason FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),'') AS brain_reason,"+
                "COALESCE((SELECT p.created_at FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),0) AS brain_created_at,"+
                "COALESCE((SELECT p.request_id FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),'') AS brain_request_id,"+
                "s.updated_at,"+
                "COALESCE((SELECT GROUP_CONCAT(a.label,' | ') FROM v4_action_proposals a WHERE a.situation_id=s.id AND a.state='PROPOSED'),'') AS actions,"+
                "CASE WHEN EXISTS (SELECT 1 FROM v4_provenance sp JOIN v4_memory_evidence me ON me.memory_id=sp.source_id JOIN v4_evidence_analysis ea ON ea.evidence_id=me.evidence_id AND ea.analysis_kind='CONNECTOR_ENRICHMENT' WHERE sp.object_type='SITUATION' AND sp.object_id=s.id AND sp.source_type='MEMORY') THEN 1 ELSE 0 END AS connector_enriched "+
                "FROM v4_situations s WHERE s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED')";
        Cursor c=sql.rawQuery(q,null);
        try{
            while(c.moveToNext()){
                String actions=c.getString(16)==null?"":c.getString(16);long changedAt=c.getLong(15),brainCreatedAt=c.getLong(13);String brainRequestId=c.getString(14)==null?"":c.getString(14);boolean connectorEnriched=c.getInt(17)!=0;
                // Fresh canonical change immediately invalidates the older model judgement on this
                // Situation. Age alone is not enough: a five-minute-old ranking is stale if new
                // evidence changed the Situation one minute ago.
                long effectiveBrainAt=brainCreatedAt>=changedAt?brainCreatedAt:0;
                CognitiveNowPolicyV4.Evaluation e=CognitiveNowPolicyV4.evaluate(c.getString(1),c.getString(2),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getLong(8),c.getLong(9),c.getInt(10),effectiveBrainAt,!actions.trim().isEmpty(),now);
                if(!e.eligible)continue;
                boolean newSinceBrain=CognitiveReasoningFreshnessV4.isNew(sql,c.getString(0),changedAt);boolean geminiCurrent=e.currentDeepBrain&&!latestGeminiRequestId.isEmpty()&&latestGeminiRequestId.equals(brainRequestId);double visibleAttention=e.currentDeepBrain?Math.max(0,Math.min(1,c.getDouble(11))):e.nowScore;
                candidates.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),visibleAttention,c.getDouble(6),c.getDouble(7),c.getLong(8),c.getLong(9),c.getInt(10),e.currentDeepBrain?c.getString(12):"",actions,e.currentDeepBrain,geminiCurrent,e.brainFreshness,changedAt,newSinceBrain,connectorEnriched));
            }
        }finally{c.close();}
        Collections.sort(candidates,new Comparator<Item>(){@Override public int compare(Item a,Item b){return compareForPulse(a,b);}});

        ArrayList<Item>out=new ArrayList<>();int brain=0,local=0,actions=0,newSinceBrain=0;
        for(Item x:candidates){
            if(out.size()>=lim)break;
            // Only a current Deep Brain cluster suppresses a sibling local card. Old model output is
            // durable audit/context, but it must not hide fresh local evidence forever.
            if(!x.deepBrainRanked()&&coveredByBrainCluster(sql,x.situationId,x.kind,now))continue;
            out.add(x);if(x.deepBrainRanked())brain++;else local++;if(!x.actions.isEmpty())actions++;if(x.newSinceDeepBrain)newSinceBrain++;
        }
        return new Snapshot(out,brain,local,actions,newSinceBrain,freshness.latestAppliedAt);
    }

    /**
     * Fresh unseen canonical changes come first until Gemini gets a chance to reconsider them.
     * Once context is covered, the CURRENT Deep Brain rank is authoritative: rank 1 must not be
     * reordered behind rank 2 merely because a local heuristic score is numerically higher.
     */
    static int compareForPulse(Item a,Item b){
        if(a.newSinceDeepBrain!=b.newSinceDeepBrain)return a.newSinceDeepBrain?-1:1;
        if(a.deepBrainRanked()!=b.deepBrainRanked())return a.deepBrainRanked()?-1:1;
        if(a.deepBrainRanked()&&a.deepBrainRank!=b.deepBrainRank)return Integer.compare(a.deepBrainRank,b.deepBrainRank);
        int byNow=Double.compare(b.attentionScore,a.attentionScore);if(byNow!=0)return byNow;
        long at=a.relevantUntil>0?a.relevantUntil:Long.MAX_VALUE,bt=b.relevantUntil>0?b.relevantUntil:Long.MAX_VALUE;if(at!=bt)return Long.compare(at,bt);
        return Double.compare(b.canonicalAttentionScore,a.canonicalAttentionScore);
    }

    private static String latestAppliedGeminiRequestId(SQLiteDatabase sql){Cursor c=sql.rawQuery("SELECT request_id FROM v4_reasoning_runs WHERE provider='gemini' AND state='APPLIED' ORDER BY completed_at DESC,started_at DESC LIMIT 1",null);try{return c.moveToFirst()?(c.getString(0)==null?"":c.getString(0)):"";}finally{c.close();}}

    private static boolean coveredByBrainCluster(SQLiteDatabase sql,String situationId,String kind,long now){
        Set<String> memories=new HashSet<>();
        Cursor s=sql.rawQuery("SELECT source_id FROM v4_provenance WHERE object_type='SITUATION' AND object_id=? AND source_type='MEMORY'",new String[]{situationId});
        try{
            while(s.moveToNext()){
                String id=s.getString(0);
                if(id!=null&&!id.trim().isEmpty())memories.add(id.trim());
            }
        }finally{s.close();}
        if(memories.isEmpty())return false;

        long cutoff=now-3L*CognitiveNowPolicyV4.DAY_MS;
        Cursor p=sql.rawQuery("SELECT p.memory_ids_json FROM v4_deep_brain_priority_items p JOIN v4_situations linked ON linked.id=p.situation_id WHERE p.state='ACTIVE' AND p.created_at>=? AND p.created_at>=linked.updated_at AND p.situation_id<>'' AND p.situation_id<>? AND linked.kind=?",new String[]{String.valueOf(cutoff),situationId,kind});
        try{
            while(p.moveToNext()){
                try{
                    JSONArray a=new JSONArray(p.getString(0)==null?"[]":p.getString(0));
                    for(int i=0;i<a.length();i++){
                        String id=a.optString(i,"").trim();
                        if(memories.contains(id))return true;
                    }
                }catch(Throwable ignored){}
            }
        }finally{p.close();}
        return false;
    }

    public static final class Item{
        public final String situationId,kind,state,headline,explanation,deepBrainReason,actions;
        /** Model attention when a current Deep Brain ranking exists; otherwise live local attention. */ public final double attentionScore;
        /** Durable score stored on the canonical Situation. */ public final double canonicalAttentionScore;
        public final double interruptionScore,confidence,brainFreshness;public final long relevantFrom,relevantUntil,changedAt;public final int deepBrainRank;public final boolean newSinceDeepBrain,connectorEnriched,geminiRanked;private final boolean currentDeepBrain;
        Item(String id,String kind,String state,String headline,String explanation,double canonicalAttention,double nowScore,double interruption,double confidence,long from,long until,int rank,String reason,String actions,boolean currentDeepBrain,boolean geminiRanked,double brainFreshness,long changedAt,boolean newSinceDeepBrain,boolean connectorEnriched){this.situationId=id;this.kind=kind;this.state=state;this.headline=headline==null?"":headline;this.explanation=explanation==null?"":explanation;this.canonicalAttentionScore=canonicalAttention;this.attentionScore=nowScore;this.interruptionScore=interruption;this.confidence=confidence;this.relevantFrom=from;this.relevantUntil=until;this.deepBrainRank=rank;this.deepBrainReason=reason==null?"":reason;this.actions=actions==null?"":actions;this.currentDeepBrain=currentDeepBrain;this.geminiRanked=geminiRanked;this.brainFreshness=brainFreshness;this.changedAt=changedAt;this.newSinceDeepBrain=newSinceDeepBrain;this.connectorEnriched=connectorEnriched;}
        public boolean deepBrainRanked(){return currentDeepBrain&&deepBrainRank>0;}
    }
    public static final class Snapshot{
        public final List<Item>items;public final int deepBrainRanked,localOnly,withActions,newSinceDeepBrain;public final long lastDeepBrainAt;
        Snapshot(List<Item>items,int brain,int local,int actions,int newSinceDeepBrain,long lastDeepBrainAt){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.deepBrainRanked=brain;this.localOnly=local;this.withActions=actions;this.newSinceDeepBrain=newSinceDeepBrain;this.lastDeepBrainAt=lastDeepBrainAt;}
        public boolean empty(){return items.isEmpty();}
    }
}
