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
        CognitiveDeepBrainStoreV4.ensure(db);CognitiveStoreV4.ensure(db);
        int lim=Math.max(1,Math.min(12,limit));SQLiteDatabase sql=db.getReadableDatabase();ArrayList<Item>candidates=new ArrayList<>();long now=System.currentTimeMillis();
        String q="SELECT s.id,s.kind,s.state,s.headline,COALESCE(s.explanation,''),s.attention_score,s.interruption_score,s.confidence,s.relevant_from,s.relevant_until,"+
                "COALESCE((SELECT MIN(p.rank_order) FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id),0) AS brain_rank,"+
                "COALESCE((SELECT p.reason FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),'') AS brain_reason,"+
                "COALESCE((SELECT p.created_at FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),0) AS brain_created_at,"+
                "COALESCE((SELECT GROUP_CONCAT(a.label,' | ') FROM v4_action_proposals a WHERE a.situation_id=s.id AND a.state='PROPOSED'),'') AS actions "+
                "FROM v4_situations s WHERE s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED')";
        Cursor c=sql.rawQuery(q,null);
        try{
            while(c.moveToNext()){
                String actions=c.getString(13)==null?"":c.getString(13);
                CognitiveNowPolicyV4.Evaluation e=CognitiveNowPolicyV4.evaluate(c.getString(1),c.getString(2),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getLong(8),c.getLong(9),c.getInt(10),c.getLong(12),!actions.trim().isEmpty(),now);
                if(!e.eligible)continue;
                candidates.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),e.nowScore,c.getDouble(6),c.getDouble(7),c.getLong(8),c.getLong(9),c.getInt(10),e.currentDeepBrain?c.getString(11):"",actions,e.currentDeepBrain,e.brainFreshness));
            }
        }finally{c.close();}
        Collections.sort(candidates,new Comparator<Item>(){@Override public int compare(Item a,Item b){
            int byNow=Double.compare(b.attentionScore,a.attentionScore);if(byNow!=0)return byNow;
            if(a.deepBrainRanked()!=b.deepBrainRanked())return a.deepBrainRanked()?-1:1;
            if(a.deepBrainRanked()&&a.deepBrainRank!=b.deepBrainRank)return Integer.compare(a.deepBrainRank,b.deepBrainRank);
            long at=a.relevantUntil>0?a.relevantUntil:Long.MAX_VALUE,bt=b.relevantUntil>0?b.relevantUntil:Long.MAX_VALUE;if(at!=bt)return Long.compare(at,bt);
            return Double.compare(b.canonicalAttentionScore,a.canonicalAttentionScore);
        }});

        long lastDeepBrainAt=latestDeepBrainAt(sql);ArrayList<Item>out=new ArrayList<>();int brain=0,local=0,actions=0,newSinceBrain=0;
        for(Item x:candidates){
            if(out.size()>=lim)break;
            // Only a current Deep Brain cluster suppresses a sibling local card. Old model output is
            // durable audit/context, but it must not hide fresh local evidence forever.
            if(!x.deepBrainRanked()&&coveredByBrainCluster(sql,x.situationId,x.kind,now))continue;
            out.add(x);if(x.deepBrainRanked())brain++;else local++;if(!x.actions.isEmpty())actions++;
            if(!x.deepBrainRanked()&&lastDeepBrainAt>0&&x.relevantFrom>lastDeepBrainAt)newSinceBrain++;
        }
        return new Snapshot(out,brain,local,actions,newSinceBrain,lastDeepBrainAt);
    }

    private static long latestDeepBrainAt(SQLiteDatabase sql){
        Cursor c=sql.rawQuery("SELECT COALESCE(MAX(created_at),0) FROM v4_deep_brain_priority_items WHERE state='ACTIVE'",null);
        try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}
    }

    private static boolean coveredByBrainCluster(SQLiteDatabase sql,String situationId,String kind,long now){
        Set<String> memories=new HashSet<>();Cursor s=sql.rawQuery("SELECT source_id FROM v4_provenance WHERE object_type='SITUATION' AND object_id=? AND source_type='MEMORY'",new String[]{situationId});try{while(s.moveToNext()){String id=s.getString(0);if(id!=null&&!id.trim().isEmpty())memories.add(id.trim());}}finally{s.close();}if(memories.isEmpty())return false;
        long cutoff=now-3L*CognitiveNowPolicyV4.DAY_MS;
        Cursor p=sql.rawQuery("SELECT p.memory_ids_json FROM v4_deep_brain_priority_items p JOIN v4_situations linked ON linked.id=p.situation_id WHERE p.state='ACTIVE' AND p.created_at>=? AND p.situation_id<>'' AND p.situation_id<>? AND linked.kind=?",new String[]{String.valueOf(cutoff),situationId,kind});
        try{while(p.moveToNext()){try{JSONArray a=new JSONArray(p.getString(0)==null?"[]":p.getString(0));for(int i=0;i<a.length();i++){String id=a.optString(i,"").trim();if(memories.contains(id))return true;}}catch(Throwable ignored){}}}finally{p.close();}return false;
    }

    public static final class Item{
        public final String situationId,kind,state,headline,explanation,deepBrainReason,actions;
        /** Dynamic current score used by Pulse ordering/UI. */ public final double attentionScore;
        /** Durable score stored on the canonical Situation. */ public final double canonicalAttentionScore;
        public final double interruptionScore,confidence,brainFreshness;public final long relevantFrom,relevantUntil;public final int deepBrainRank;private final boolean currentDeepBrain;
        Item(String id,String kind,String state,String headline,String explanation,double canonicalAttention,double nowScore,double interruption,double confidence,long from,long until,int rank,String reason,String actions,boolean currentDeepBrain,double brainFreshness){this.situationId=id;this.kind=kind;this.state=state;this.headline=headline==null?"":headline;this.explanation=explanation==null?"":explanation;this.canonicalAttentionScore=canonicalAttention;this.attentionScore=nowScore;this.interruptionScore=interruption;this.confidence=confidence;this.relevantFrom=from;this.relevantUntil=until;this.deepBrainRank=rank;this.deepBrainReason=reason==null?"":reason;this.actions=actions==null?"":actions;this.currentDeepBrain=currentDeepBrain;this.brainFreshness=brainFreshness;}
        public boolean deepBrainRanked(){return currentDeepBrain&&deepBrainRank>0;}
    }
    public static final class Snapshot{
        public final List<Item>items;public final int deepBrainRanked,localOnly,withActions,newSinceDeepBrain;public final long lastDeepBrainAt;
        Snapshot(List<Item>items,int brain,int local,int actions,int newSinceDeepBrain,long lastDeepBrainAt){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.deepBrainRanked=brain;this.localOnly=local;this.withActions=actions;this.newSinceDeepBrain=newSinceDeepBrain;this.lastDeepBrainAt=lastDeepBrainAt;}
        public boolean empty(){return items.isEmpty();}
    }
}
