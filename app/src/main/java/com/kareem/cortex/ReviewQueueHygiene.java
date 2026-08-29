package com.kareem.cortex;

import android.content.ContentValues;

import java.util.ArrayList;
import java.util.HashMap;

/** Keeps human review for genuine ambiguity, not technical residue from old model passes. */
public final class ReviewQueueHygiene {
    private static final long NORMAL_TTL=72L*60L*60L*1000L;
    private static final long HIGH_TTL=7L*24L*60L*60L*1000L;
    private ReviewQueueHygiene(){}

    public static ArrayList<ReviewQueueStore.Item> pending(VaultDb db,int limit){
        ArrayList<ReviewQueueStore.Item> raw=ReviewQueueStore.pending(db,80);
        ArrayList<BrainSituationStore.Item> current=BrainSituationStore.current(db,200);
        HashMap<Long,BrainSituationStore.Item> byThread=new HashMap<>();
        for(BrainSituationStore.Item s:current)if(s.threadId>0)byThread.put(s.threadId,s);
        long now=System.currentTimeMillis();ArrayList<ReviewQueueStore.Item> out=new ArrayList<>();
        for(ReviewQueueStore.Item r:raw){
            long ttl=r.importance>=70?HIGH_TTL:NORMAL_TTL;
            boolean stale=r.createdAt>0&&now-r.createdAt>ttl;
            boolean noise=mechanical(r.title+" "+r.body);
            BrainSituationStore.Item s=r.threadId>0?byThread.get(r.threadId):null;
            boolean superseded=s!=null&&s.lastChangedAt>=r.createdAt&&(r.signalId<=0||s.signalId>=r.signalId);
            if(stale||noise||superseded){retire(db,r.id,stale?"expired":(noise?"dismissed":"superseded"));continue;}
            out.add(r);if(out.size()>=Math.max(1,limit))break;
        }
        return out;
    }

    private static void retire(VaultDb db,long id,String state){
        ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("state",state);v.put("resolved_at",now);v.put("updated_at",now);
        db.getWritableDatabase().update("derived_items",v,"id=? AND kind='REVIEW' AND state='pending'",new String[]{String.valueOf(id)});
    }

    private static boolean mechanical(String value){
        String t=MasterRelevanceFilter.ruleNorm(value==null?"":value);
        boolean operation=t.contains("deleting")||t.contains("uploading")||t.contains("downloading")||t.contains("syncing")||t.contains("processing");
        boolean progress=t.matches(".*\\b\\d+\\s+of\\s+\\d+\\b.*")||t.contains("%")||t.contains("progress");
        return operation&&progress;
    }
}
