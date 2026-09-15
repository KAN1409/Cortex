package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Shared persistence boundary for rule and council findings. Originals are never modified. */
final class CortexInsightPublisher {
    private CortexInsightPublisher(){}
    static long submit(SQLiteDatabase db,long sid,String issue,String family,String domain,
            String title,String found,String why,String whyNow,String action,double confidence,
            double score,List<Long> evidenceIds,long now){
        DiscoveryV3Schema.ensure(db);
        db.beginTransaction();
        try{
            LinkedHashSet<Long> valid=new LinkedHashSet<>();boolean missing=false;long last=0;
            for(Long item:new LinkedHashSet<>(evidenceIds)){
                try(Cursor c=db.rawQuery("SELECT created_at FROM knowledge_items WHERE id=?",new String[]{String.valueOf(item)})){
                    if(c.moveToFirst()){valid.add(item);last=Math.max(last,c.getLong(0));}else missing=true;
                }
            }
            boolean publish=!missing&&!valid.isEmpty()&&Double.isFinite(confidence)&&Double.isFinite(score)
                    &&DiscoveryV3Policy.publishable(family,found,action,valid.size(),confidence,score)
                    &&DiscoveryV3Feed.userWorthy(family,title,found,action,valid.size(),confidence,score);
            long id=0;String previous="";
            try(Cursor c=db.rawQuery("SELECT id,state FROM discovery_v3_insights WHERE issue_key=?",new String[]{issue})){
                if(c.moveToFirst()){id=c.getLong(0);previous=c.getString(1);}
            }
            String state=publish?"published":(missing||valid.isEmpty()?"quarantined":"suppressed");
            if(Arrays.asList("dismissed","resolved","wrong","superseded").contains(previous))state=previous;
            ContentValues v=new ContentValues();v.put("situation_id",sid);v.put("issue_key",issue);
            v.put("family",family);v.put("domain",domain);v.put("title",title);v.put("what_found",found);
            v.put("why_matters",why);v.put("why_now",whyNow);v.put("suggested_action",action);
            v.put("confidence",Double.isFinite(confidence)?confidence:0);v.put("score",Double.isFinite(score)?score:0);
            v.put("state",state);v.put("quality_reason",publish?"truth_pipeline_001: evidence and publication gates passed":"truth_pipeline_001: insufficient evidence or value");
            v.put("evidence_count",valid.size());v.put("last_evidence_at",last);v.put("updated_at",now);
            if(id==0){v.put("created_at",now);id=db.insertOrThrow("discovery_v3_insights",null,v);}
            else db.update("discovery_v3_insights",v,"id=?",new String[]{String.valueOf(id)});
            for(long item:valid){ContentValues e=new ContentValues();e.put("insight_id",id);e.put("item_id",item);e.put("role","supports");db.insertWithOnConflict("discovery_v3_insight_evidence",null,e,SQLiteDatabase.CONFLICT_IGNORE);}
            try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT e.item_id) FROM discovery_v3_insight_evidence e JOIN knowledge_items k ON k.id=e.item_id WHERE e.insight_id=?",new String[]{String.valueOf(id)})){
                c.moveToFirst();ContentValues count=new ContentValues();count.put("evidence_count",c.getInt(0));db.update("discovery_v3_insights",count,"id=?",new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
}
