package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds a bounded, grounded, user-triggered context packet for ChatGPT Deep Brain. */
public final class CognitiveDeepBrainPacketBuilderV4 {
    private static final int MAX_SITUATIONS=12, MAX_MEMORIES=24, MAX_WORLDS=16, MAX_FACTS=24;
    private CognitiveDeepBrainPacketBuilderV4() {}

    public static Packet build(VaultDb db,String question){return build(null,db,question);}
    public static Packet build(Context context,VaultDb db,String question) {
        if (db == null) throw new IllegalArgumentException("db required");
        String q = question == null ? "" : question.trim(); if (q.isEmpty()) throw new IllegalArgumentException("question required");
        CognitiveStoreV4.ensure(db); CognitiveDeepBrainStoreV4.ensure(db); PhoneContextStore.ensure(db);
        String requestId = CognitiveDeepBrainStoreV4.newRequestId(); SQLiteDatabase sql = db.getReadableDatabase();
        try {
            JSONObject root = new JSONObject(); root.put("protocol", "CORTEX_CONTEXT_V2"); root.put("request_id", requestId); root.put("generated_at", System.currentTimeMillis()); root.put("question", q);
            root.put("grounding_policy", "Evidence/history is immutable. Deep Brain may prioritize existing situations and suggest actions, but may not rewrite historical Evidence/Facts.");
            root.put("privacy_policy", "Only context whose Cortex source policy permits cloud use is included. RESTRICTED Evidence is never included.");
            LinkedHashSet<String> situationIds=new LinkedHashSet<>(),memoryIds=new LinkedHashSet<>(),worldIds=new LinkedHashSet<>(),factIds=new LinkedHashSet<>();
            root.put("current_phone_context", phoneContext(context,db)); root.put("situations", situations(context,sql,situationIds)); root.put("recent_memories", memories(context,sql,memoryIds)); root.put("worlds", worlds(context,sql,worldIds)); root.put("facts", facts(context,sql,factIds));
            root.put("allowed_situation_ids",array(situationIds));root.put("allowed_memory_ids",array(memoryIds));root.put("allowed_world_ids",array(worldIds));root.put("allowed_fact_ids",array(factIds));
            String contextJson=root.toString();String shareText=CognitiveDeepBrainProtocolV4.buildShareText(requestId,q,root);
            Packet packet=new Packet(requestId,q,contextJson,shareText,new ArrayList<>(situationIds),new ArrayList<>(memoryIds),new ArrayList<>(worldIds),new ArrayList<>(factIds));CognitiveDeepBrainStoreV4.saveRequest(db,packet);return packet;
        } catch (RuntimeException e) { throw e; } catch (Throwable e) { throw new IllegalStateException("Could not build Deep Brain packet", e); }
    }

    private static JSONObject phoneContext(Context context,VaultDb db) {
        JSONObject o=new JSONObject();try{if(context!=null&&!PrivacyPolicy.canUseCloud(context,"phone_context")){o.put("omitted","phone_context is Local only in Cortex Privacy controls");return o;}PhoneContextStore.Event latest=PhoneContextStore.latest(db);if(latest!=null){o.put("latest_at",latest.occurredAt);o.put("latest",clip(latest.human(),400));}o.put("recent_30m",clip(PhoneContextStore.recentSummary(db,30L*60L*1000L,12),3000));o.put("active_processes",clip(PhoneContextStore.activeProcessSummary(db,8),1800));}catch(Throwable ignored){}return o;
    }

    private static JSONArray situations(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT id,kind,state,headline,COALESCE(explanation,''),COALESCE(primary_world_id,''),semantic_anchor,relevant_from,relevant_until,attention_score,interruption_score,confidence,last_evaluated_at FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED') ORDER BY attention_score DESC,CASE WHEN relevant_until>0 THEN relevant_until ELSE 9223372036854775807 END ASC,updated_at DESC LIMIT ?",new String[]{String.valueOf(MAX_SITUATIONS*3)});try{while(c.moveToNext()&&out.length()<MAX_SITUATIONS){String id=c.getString(0);if(!objectCloudAllowed(context,sql,"SITUATION",id,0))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("kind",c.getString(1));x.put("state",c.getString(2));x.put("headline",clip(c.getString(3),300));x.put("explanation",clip(c.getString(4),500));x.put("primary_world_id",c.getString(5));x.put("semantic_anchor",clip(c.getString(6),240));x.put("relevant_from",c.getLong(7));x.put("relevant_until",c.getLong(8));x.put("attention_score",c.getDouble(9));x.put("interruption_score",c.getDouble(10));x.put("confidence",c.getDouble(11));x.put("last_evaluated_at",c.getLong(12));out.put(x);}}finally{c.close();}return out;}

    private static JSONArray memories(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT m.id,m.kind,COALESCE(m.title,''),m.body,COALESCE(m.source_package,''),m.started_at,m.importance,m.pinned FROM v4_memories m WHERE m.state='ACTIVE' ORDER BY m.started_at DESC,m.id DESC LIMIT ?",new String[]{String.valueOf(MAX_MEMORIES*5)});try{while(c.moveToNext()&&out.length()<MAX_MEMORIES){String id=c.getString(0);if(!memoryCloudAllowed(context,sql,id))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("kind",c.getString(1));x.put("title",clip(c.getString(2),220));x.put("body",clip(c.getString(3),700));x.put("source_package",c.getString(4));x.put("started_at",c.getLong(5));x.put("importance",c.getDouble(6));x.put("pinned",c.getInt(7)!=0);JSONArray evidence=new JSONArray();Cursor e=sql.rawQuery("SELECT evidence_id FROM v4_memory_evidence WHERE memory_id=? ORDER BY ordinal ASC,evidence_id ASC LIMIT 6",new String[]{id});try{while(e.moveToNext())evidence.put(e.getString(0));}finally{e.close();}x.put("evidence_ids",evidence);out.put(x);}}finally{c.close();}return out;}

    private static JSONArray worlds(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT id,canonical_name,type_hint,maturity,COALESCE(summary,''),last_active_at FROM v4_worlds WHERE status='ACTIVE' ORDER BY last_active_at DESC,id ASC LIMIT ?",new String[]{String.valueOf(MAX_WORLDS*4)});try{while(c.moveToNext()&&out.length()<MAX_WORLDS){String id=c.getString(0);if(!objectCloudAllowed(context,sql,"WORLD",id,0))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("name",clip(c.getString(1),180));x.put("type",c.getString(2));x.put("maturity",c.getString(3));x.put("summary",clip(c.getString(4),500));x.put("last_active_at",c.getLong(5));out.put(x);}}finally{c.close();}return out;}

    private static JSONArray facts(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT id,COALESCE(subject_world_id,''),predicate,value,grounding,confidence,valid_from,valid_until FROM v4_facts WHERE status='ACTIVE' ORDER BY updated_at DESC,id ASC LIMIT ?",new String[]{String.valueOf(MAX_FACTS*4)});try{while(c.moveToNext()&&out.length()<MAX_FACTS){String id=c.getString(0);if(!objectCloudAllowed(context,sql,"FACT",id,0))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("subject_world_id",c.getString(1));x.put("predicate",clip(c.getString(2),160));x.put("value",clip(c.getString(3),500));x.put("grounding",c.getString(4));x.put("confidence",c.getDouble(5));x.put("valid_from",c.getLong(6));x.put("valid_until",c.getLong(7));out.put(x);}}finally{c.close();}return out;}

    private static boolean memoryCloudAllowed(Context context,SQLiteDatabase sql,String memoryId){Cursor c=sql.rawQuery("SELECT e.id,e.source_type,e.sensitivity FROM v4_memory_evidence me JOIN v4_evidence e ON e.id=me.evidence_id WHERE me.memory_id=?",new String[]{memoryId});boolean any=false;try{while(c.moveToNext()){any=true;if(!evidenceCloudAllowed(context,c.getString(0),c.getString(1),c.getString(2)))return false;}}finally{c.close();}return true;}
    private static boolean evidenceCloudAllowed(Context context,String id,String sourceType,String sensitivity){if("RESTRICTED".equalsIgnoreCase(sensitivity))return false;if(context==null)return true;String key=privacyKey(sourceType);return key.isEmpty()||PrivacyPolicy.canUseCloud(context,key);}
    private static String privacyKey(String sourceType){String t=sourceType==null?"":sourceType.toUpperCase(java.util.Locale.ROOT);if("NOTIFICATION".equals(t))return"notifications";if("VOICE".equals(t))return"audio";if("IMAGE".equals(t))return"images";if("FILE".equals(t))return"files";if("CONTACT".equals(t))return"contacts";if("CALENDAR".equals(t))return"calendar";if("APP_ACTIVITY".equals(t))return"app_usage";if("SCREEN".equals(t))return"screen_context";if("LOCATION".equals(t)||"SYSTEM".equals(t))return"phone_context";return"";}
    private static boolean objectCloudAllowed(Context context,SQLiteDatabase sql,String objectType,String objectId,int depth){if(context==null||depth>3)return true;Cursor c=sql.rawQuery("SELECT source_type,source_id FROM v4_provenance WHERE object_type=? AND object_id=?",new String[]{objectType,objectId});boolean any=false;try{while(c.moveToNext()){any=true;String type=c.getString(0),id=c.getString(1);if("EVIDENCE".equals(type)){Cursor e=sql.rawQuery("SELECT source_type,sensitivity FROM v4_evidence WHERE id=? LIMIT 1",new String[]{id});try{if(e.moveToFirst()&&!evidenceCloudAllowed(context,id,e.getString(0),e.getString(1)))return false;}finally{e.close();}}else if("MEMORY".equals(type)){if(!memoryCloudAllowed(context,sql,id))return false;}else if("FACT".equals(type)){if(!objectCloudAllowed(context,sql,"FACT",id,depth+1))return false;}}}finally{c.close();}return true;}

    private static JSONArray array(Iterable<String>ids){JSONArray a=new JSONArray();for(String id:ids)a.put(id);return a;}private static String clip(String s,int n){String x=s==null?"":s.replace('\u0000',' ').trim();return x.length()<=n?x:x.substring(0,n)+"…";}
    public static final class Packet{public final String requestId,question,contextJson,shareText;public final List<String>situationIds,memoryIds,worldIds,factIds;Packet(String requestId,String question,String contextJson,String shareText,List<String>situationIds,List<String>memoryIds,List<String>worldIds,List<String>factIds){this.requestId=requestId;this.question=question;this.contextJson=contextJson;this.shareText=shareText;this.situationIds=Collections.unmodifiableList(situationIds);this.memoryIds=Collections.unmodifiableList(memoryIds);this.worldIds=Collections.unmodifiableList(worldIds);this.factIds=Collections.unmodifiableList(factIds);}}
}
