package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Connects already-applied ChatGPT suggestions to Situations that may have been created later.
 *
 * <p>This is what lets old Deep Brain output become useful after the Situation Engine lands: a
 * memory-grounded ranked priority can raise the attention of the matching canonical Situation and
 * an action proposal can be attached to it. It never changes Evidence/Memory/Facts and never
 * resolves/dismisses a Situation.</p>
 */
public final class CognitiveDeepBrainReconcilerV4 {
    private CognitiveDeepBrainReconcilerV4(){}

    public static Result reconcile(VaultDb db){
        if(db==null)throw new IllegalArgumentException("db required");
        CognitiveDeepBrainStoreV4.ensure(db);CognitiveStoreV4.ensure(db);
        SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();
        int prioritiesLinked=0,situationsRaised=0,actionsLinked=0;
        sql.beginTransaction();
        try{
            Cursor p=sql.rawQuery("SELECT id,attention_score,COALESCE(situation_id,''),memory_ids_json FROM v4_deep_brain_priority_items WHERE state='ACTIVE' ORDER BY rank_order ASC,created_at DESC",null);
            try{
                while(p.moveToNext()){
                    String priorityId=p.getString(0);double score=clamp01(p.getDouble(1));String linked=clean(p.getString(2));List<String> memories=jsonIds(p.getString(3));
                    String situation=validOpenSituation(sql,linked)?linked:findSituationForMemories(sql,memories);
                    if(situation.isEmpty())continue;
                    if(!situation.equals(linked)){ContentValues pv=new ContentValues();pv.put("situation_id",situation);pv.put("updated_at",now);if(sql.update("v4_deep_brain_priority_items",pv,"id=?",new String[]{priorityId})>0)prioritiesLinked++;}
                    Cursor sc=sql.rawQuery("SELECT state,attention_score FROM v4_situations WHERE id=? LIMIT 1",new String[]{situation});String state="";double old=0;try{if(sc.moveToFirst()){state=sc.getString(0);old=sc.getDouble(1);}}finally{sc.close();}
                    if(!terminal(state)){
                        ContentValues sv=new ContentValues();boolean changed=false;
                        if(score>old){sv.put("attention_score",score);changed=true;}
                        if("DETECTED".equals(state)&&score>=.70){sv.put("state","RELEVANT");changed=true;}
                        if(changed){sv.put("last_evaluated_at",now);sv.put("updated_at",now);if(sql.update("v4_situations",sv,"id=?",new String[]{situation})>0)situationsRaised++;}
                        provenance(sql,"SITUATION",situation,"DEEP_BRAIN_PRIORITY",priorityId,"ranked_by_deep_brain",score,now);
                    }
                }
            }finally{p.close();}

            Cursor a=sql.rawQuery("SELECT id,COALESCE(situation_id,''),COALESCE(payload_json,'{}') FROM v4_action_proposals WHERE state='PROPOSED' AND (situation_id IS NULL OR situation_id='') ORDER BY created_at DESC LIMIT 200",null);
            try{
                while(a.moveToNext()){
                    String actionId=a.getString(0),payload=a.getString(2);List<String> memories=new ArrayList<>();
                    try{JSONObject o=new JSONObject(payload);if(!"chatgpt_plus_share".equals(o.optString("origin","")))continue;JSONArray xs=o.optJSONArray("memory_ids");if(xs!=null)for(int i=0;i<xs.length();i++){String id=clean(xs.optString(i,""));if(!id.isEmpty())memories.add(id);}}catch(Throwable ignored){continue;}
                    String situation=findSituationForMemories(sql,memories);if(situation.isEmpty())continue;ContentValues v=new ContentValues();v.put("situation_id",situation);v.put("updated_at",now);if(sql.update("v4_action_proposals",v,"id=?",new String[]{actionId})>0){actionsLinked++;provenance(sql,"SITUATION",situation,"ACTION_PROPOSAL",actionId,"has_proposed_action",1.0,now);}
                }
            }finally{a.close();}
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
        return new Result(prioritiesLinked,situationsRaised,actionsLinked);
    }

    private static String findSituationForMemories(SQLiteDatabase sql,List<String> memories){
        if(memories==null||memories.isEmpty())return"";StringBuilder q=new StringBuilder("SELECT s.id FROM v4_provenance p JOIN v4_situations s ON s.id=p.object_id WHERE p.object_type='SITUATION' AND p.source_type='MEMORY' AND p.source_id IN (");String[] args=new String[memories.size()];for(int i=0;i<memories.size();i++){if(i>0)q.append(',');q.append('?');args[i]=memories.get(i);}q.append(") AND s.state NOT IN ('RESOLVED','CANCELLED','DISMISSED') GROUP BY s.id ORDER BY COUNT(DISTINCT p.source_id) DESC,s.confidence DESC,s.updated_at DESC LIMIT 1");Cursor c=sql.rawQuery(q.toString(),args);try{return c.moveToFirst()?clean(c.getString(0)):"";}finally{c.close();}
    }
    private static boolean validOpenSituation(SQLiteDatabase sql,String id){if(id.isEmpty())return false;Cursor c=sql.rawQuery("SELECT state FROM v4_situations WHERE id=? LIMIT 1",new String[]{id});try{return c.moveToFirst()&&!terminal(c.getString(0));}finally{c.close();}}
    private static boolean terminal(String s){return"RESOLVED".equals(s)||"CANCELLED".equals(s)||"DISMISSED".equals(s);}
    private static List<String> jsonIds(String raw){ArrayList<String>out=new ArrayList<>();try{JSONArray a=new JSONArray(raw==null?"[]":raw);for(int i=0;i<a.length();i++){String x=clean(a.optString(i,""));if(!x.isEmpty()&&!out.contains(x))out.add(x);}}catch(Throwable ignored){}return out;}
    private static void provenance(SQLiteDatabase sql,String objectType,String objectId,String sourceType,String sourceId,String role,double confidence,long now){ContentValues v=new ContentValues();v.put("object_type",objectType);v.put("object_id",objectId);v.put("source_type",sourceType);v.put("source_id",sourceId);v.put("role",role);v.put("confidence",clamp01(confidence));v.put("created_at",now);sql.insertWithOnConflict("v4_provenance",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    private static double clamp01(double x){if(Double.isNaN(x)||Double.isInfinite(x))return 0;return Math.max(0,Math.min(1,x));}
    private static String clean(String s){return s==null?"":s.trim();}
    public static final class Result{public final int prioritiesLinked,situationsRaised,actionsLinked;Result(int p,int s,int a){prioritiesLinked=p;situationsRaised=s;actionsLinked=a;}}
}
