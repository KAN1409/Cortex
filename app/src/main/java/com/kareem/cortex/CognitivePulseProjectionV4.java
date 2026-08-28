package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;

/** Read-only Stage-E Pulse projection: canonical Situations + optional Deep Brain ranking/actions. */
public final class CognitivePulseProjectionV4 {
    private CognitivePulseProjectionV4(){}

    public static Snapshot current(VaultDb db,int limit){
        if(db==null)return new Snapshot(Collections.<Item>emptyList(),0,0,0);
        CognitiveDeepBrainStoreV4.ensure(db);CognitiveStoreV4.ensure(db);
        int lim=Math.max(1,Math.min(12,limit));SQLiteDatabase sql=db.getReadableDatabase();ArrayList<Item>out=new ArrayList<>();
        String q="SELECT s.id,s.kind,s.state,s.headline,COALESCE(s.explanation,''),s.attention_score,s.interruption_score,s.confidence,s.relevant_from,s.relevant_until,"+
                "COALESCE((SELECT MIN(p.rank_order) FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id),0) AS brain_rank,"+
                "COALESCE((SELECT p.reason FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=s.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),'') AS brain_reason,"+
                "COALESCE((SELECT GROUP_CONCAT(a.label,' | ') FROM v4_action_proposals a WHERE a.situation_id=s.id AND a.state='PROPOSED'),'') AS actions "+
                "FROM v4_situations s WHERE s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED') "+
                "ORDER BY CASE WHEN brain_rank>0 THEN 0 ELSE 1 END ASC,CASE WHEN brain_rank>0 THEN brain_rank ELSE 999 END ASC,s.attention_score DESC,CASE WHEN s.relevant_until>0 THEN s.relevant_until ELSE 9223372036854775807 END ASC,s.updated_at DESC LIMIT ?";
        Cursor c=sql.rawQuery(q,new String[]{String.valueOf(lim*4)});int brain=0,local=0,actions=0;
        try{
            while(c.moveToNext()&&out.size()<lim){
                Item x=new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getLong(8),c.getLong(9),c.getInt(10),c.getString(11),c.getString(12));
                // If ChatGPT grouped several same-kind Memory-grounded situations into one priority,
                // keep the linked canonical Situation as the visible Pulse card and suppress sibling
                // local cards. Nothing is merged, deleted or resolved; this is projection-only dedupe.
                if(!x.deepBrainRanked()&&coveredByBrainCluster(sql,x.situationId,x.kind))continue;
                out.add(x);if(x.deepBrainRank>0)brain++;else local++;if(!x.actions.isEmpty())actions++;
            }
        }finally{c.close();}
        return new Snapshot(out,brain,local,actions);
    }

    private static boolean coveredByBrainCluster(SQLiteDatabase sql,String situationId,String kind){
        Set<String> memories=new HashSet<>();Cursor s=sql.rawQuery("SELECT source_id FROM v4_provenance WHERE object_type='SITUATION' AND object_id=? AND source_type='MEMORY'",new String[]{situationId});try{while(s.moveToNext()){String id=s.getString(0);if(id!=null&&!id.trim().isEmpty())memories.add(id.trim());}}finally{s.close();}if(memories.isEmpty())return false;
        Cursor p=sql.rawQuery("SELECT p.memory_ids_json FROM v4_deep_brain_priority_items p JOIN v4_situations linked ON linked.id=p.situation_id WHERE p.state='ACTIVE' AND p.situation_id<>'' AND p.situation_id<>? AND linked.kind=?",new String[]{situationId,kind});
        try{while(p.moveToNext()){try{JSONArray a=new JSONArray(p.getString(0)==null?"[]":p.getString(0));for(int i=0;i<a.length();i++){String id=a.optString(i,"").trim();if(memories.contains(id))return true;}}catch(Throwable ignored){}}}finally{p.close();}return false;
    }

    public static final class Item{
        public final String situationId,kind,state,headline,explanation,deepBrainReason,actions;public final double attentionScore,interruptionScore,confidence;public final long relevantFrom,relevantUntil;public final int deepBrainRank;
        Item(String id,String kind,String state,String headline,String explanation,double attention,double interruption,double confidence,long from,long until,int rank,String reason,String actions){this.situationId=id;this.kind=kind;this.state=state;this.headline=headline==null?"":headline;this.explanation=explanation==null?"":explanation;this.attentionScore=attention;this.interruptionScore=interruption;this.confidence=confidence;this.relevantFrom=from;this.relevantUntil=until;this.deepBrainRank=rank;this.deepBrainReason=reason==null?"":reason;this.actions=actions==null?"":actions;}
        public boolean deepBrainRanked(){return deepBrainRank>0;}
    }
    public static final class Snapshot{public final List<Item>items;public final int deepBrainRanked,localOnly,withActions;Snapshot(List<Item>items,int brain,int local,int actions){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.deepBrainRanked=brain;this.localOnly=local;this.withActions=actions;}public boolean empty(){return items.isEmpty();}}
}
