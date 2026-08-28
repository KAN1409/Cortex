package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
        // A user-triggered Deep Brain request must not depend on background-maintenance timing.
        CognitiveSituationEngineV4.refresh(db); CognitiveDeepBrainReconcilerV4.reconcile(db);
        CognitiveReasoningFreshnessV4.Snapshot freshness=CognitiveReasoningFreshnessV4.current(db);
        String requestId = CognitiveDeepBrainStoreV4.newRequestId(); SQLiteDatabase sql = db.getReadableDatabase();
        try {
            JSONObject root = new JSONObject(); root.put("protocol", "CORTEX_CONTEXT_V2"); root.put("request_id", requestId); root.put("generated_at", System.currentTimeMillis()); root.put("question", q);
            root.put("grounding_policy", "Evidence/history is immutable. Deep Brain may prioritize existing situations and suggest actions, but may not rewrite historical Evidence/Facts.");
            root.put("privacy_policy", "Only context whose Cortex source policy permits cloud use is included. RESTRICTED Evidence is never included.");
            JSONObject rf=new JSONObject();rf.put("latest_applied_at",freshness.latestAppliedAt);rf.put("new_open_situations",freshness.newOpenSituations);rf.put("newest_situation_change_at",freshness.newestSituationAt);root.put("reasoning_freshness",rf);
            LinkedHashSet<String> situationIds=new LinkedHashSet<>(),memoryIds=new LinkedHashSet<>(),worldIds=new LinkedHashSet<>(),factIds=new LinkedHashSet<>();
            root.put("current_phone_context", phoneContext(context,db)); root.put("situations", situations(context,sql,situationIds,freshness.latestAppliedAt)); root.put("recent_memories", memories(context,sql,situationIds,memoryIds)); root.put("worlds", worlds(context,sql,worldIds)); root.put("facts", facts(context,sql,factIds));
            root.put("allowed_situation_ids",array(situationIds));root.put("allowed_memory_ids",array(memoryIds));root.put("allowed_world_ids",array(worldIds));root.put("allowed_fact_ids",array(factIds));
            String contextJson=root.toString(); JSONObject compact=compact(root); String compactContextJson=compact.toString();
            String shareText=CognitiveDeepBrainProtocolV4.buildShareText(requestId,q,root); String compactText=CognitiveDeepBrainProtocolV4.buildCompactPasteText(requestId,q,compact); String exportJson=CognitiveDeepBrainProtocolV4.buildJsonFile(requestId,q,compact);
            Packet packet=new Packet(requestId,q,contextJson,compactContextJson,shareText,compactText,exportJson,new ArrayList<>(situationIds),new ArrayList<>(memoryIds),new ArrayList<>(worldIds),new ArrayList<>(factIds));
            CognitiveDeepBrainStoreV4.saveRequest(db,packet);return packet;
        } catch (RuntimeException e) { throw e; } catch (Throwable e) { throw new IllegalStateException("Could not build Deep Brain packet", e); }
    }

    /** Smaller transport projection: same grounded IDs, less duplicated prose and no redundant allow-lists. */
    private static JSONObject compact(JSONObject full)throws Exception{
        JSONObject out=new JSONObject(); out.put("request_id",full.optString("request_id",""));out.put("generated_at",full.optLong("generated_at",0));out.put("question",full.optString("question",""));
        JSONObject freshness=full.optJSONObject("reasoning_freshness");if(freshness!=null&&freshness.length()>0)out.put("reasoning_freshness",freshness);
        JSONObject phone=full.optJSONObject("current_phone_context");if(phone!=null&&phone.length()>0)out.put("phone",phone);
        JSONArray sOut=new JSONArray(),s=full.optJSONArray("situations");if(s!=null)for(int i=0;i<s.length();i++){JSONObject x=s.optJSONObject(i);if(x==null)continue;JSONObject y=new JSONObject();copy(y,x,"id","kind","state","headline","relevant_from","relevant_until","attention_score","canonical_attention_score","interruption_score","confidence","deep_brain_rank","new_since_deep_brain","connector_enriched","changed_at");String e=clip(x.optString("explanation",""),260);if(!e.isEmpty())y.put("explanation",e);String br=clip(x.optString("deep_brain_reason",""),300);if(!br.isEmpty())y.put("deep_brain_reason",br);sOut.put(y);}out.put("situations",sOut);
        JSONArray mOut=new JSONArray(),m=full.optJSONArray("recent_memories");if(m!=null)for(int i=0;i<m.length();i++){JSONObject x=m.optJSONObject(i);if(x==null)continue;JSONObject y=new JSONObject();y.put("id",x.optString("id",""));y.put("kind",x.optString("kind",""));y.put("started_at",x.optLong("started_at",0));y.put("importance",x.optDouble("importance",0));if(x.optBoolean("pinned",false))y.put("pinned",true);String src=clip(x.optString("source_package",""),80);if(!src.isEmpty())y.put("source",src);y.put("text",memoryText(x.optString("title",""),x.optString("body","")));mOut.put(y);}out.put("memories",mOut);
        JSONArray wOut=new JSONArray(),w=full.optJSONArray("worlds");if(w!=null)for(int i=0;i<w.length();i++){JSONObject x=w.optJSONObject(i);if(x==null)continue;JSONObject y=new JSONObject();copy(y,x,"id","name","type","maturity","last_active_at");String summary=clip(x.optString("summary",""),240);if(!summary.isEmpty())y.put("summary",summary);wOut.put(y);}out.put("worlds",wOut);
        JSONArray fOut=new JSONArray(),f=full.optJSONArray("facts");if(f!=null)for(int i=0;i<f.length();i++){JSONObject x=f.optJSONObject(i);if(x==null)continue;JSONObject y=new JSONObject();copy(y,x,"id","subject_world_id","predicate","grounding","confidence","valid_from","valid_until");y.put("value",clip(x.optString("value",""),280));fOut.put(y);}out.put("facts",fOut);return out;
    }
    private static void copy(JSONObject to,JSONObject from,String...keys)throws Exception{for(String key:keys)if(from.has(key)&&!from.isNull(key))to.put(key,from.get(key));}
    private static String memoryText(String title,String body){String t=clean(title),b=clean(body);if(b.isEmpty())return clip(t,420);if(!t.isEmpty()&&b.toLowerCase(java.util.Locale.ROOT).startsWith(t.toLowerCase(java.util.Locale.ROOT)))return clip(b,420);if(t.isEmpty())return clip(b,420);return clip(t+" — "+b,420);}
    private static String clean(String s){return s==null?"":s.replace('\u0000',' ').replaceAll("\\s+"," ").trim();}

    private static JSONObject phoneContext(Context context,VaultDb db) {JSONObject o=new JSONObject();try{if(context!=null&&!PrivacyPolicy.canUseCloud(context,"phone_context")){o.put("omitted","phone_context is Local only in Cortex Privacy controls");return o;}PhoneContextStore.Event latest=PhoneContextStore.latest(db);if(latest!=null){o.put("latest_at",latest.occurredAt);o.put("latest",clip(latest.human(),400));}o.put("recent_30m",clip(PhoneContextStore.recentSummary(db,30L*60L*1000L,12),3000));o.put("active_processes",clip(PhoneContextStore.activeProcessSummary(db,8),1800));}catch(Throwable ignored){}return o;}

    /**
     * Select Situations using the same live temporal policy Pulse uses, not stored attention alone.
     * This prevents the model packet itself from being biased toward yesterday's ranking and ensures
     * fresh deadlines/risks from Second Brain can enter the next ChatGPT pass immediately.
     */
    private static JSONArray situations(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids,long latestAppliedAt)throws Exception{
        ArrayList<SituationPacketRow> rows=new ArrayList<>();long now=System.currentTimeMillis();
        String query="SELECT id,kind,state,headline,COALESCE(explanation,''),COALESCE(primary_world_id,''),semantic_anchor,relevant_from,relevant_until,attention_score,interruption_score,confidence,last_evaluated_at,"+
                "COALESCE((SELECT MIN(p.rank_order) FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=v4_situations.id),0),"+
                "COALESCE((SELECT p.reason FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=v4_situations.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),''),"+
                "COALESCE((SELECT p.created_at FROM v4_deep_brain_priority_items p WHERE p.state='ACTIVE' AND p.situation_id=v4_situations.id ORDER BY p.rank_order ASC,p.created_at DESC LIMIT 1),0),updated_at,"+
                "CASE WHEN EXISTS(SELECT 1 FROM v4_action_proposals a WHERE a.situation_id=v4_situations.id AND a.state='PROPOSED') THEN 1 ELSE 0 END,"+
                "CASE WHEN EXISTS (SELECT 1 FROM v4_provenance sp JOIN v4_memory_evidence me ON me.memory_id=sp.source_id JOIN v4_evidence_analysis ea ON ea.evidence_id=me.evidence_id AND ea.analysis_kind='CONNECTOR_ENRICHMENT' WHERE sp.object_type='SITUATION' AND sp.object_id=v4_situations.id AND sp.source_type='MEMORY') THEN 1 ELSE 0 END "+
                "FROM v4_situations WHERE state NOT IN ('RESOLVED','CANCELLED','DISMISSED') ORDER BY updated_at DESC LIMIT ?";
        Cursor c=sql.rawQuery(query,new String[]{String.valueOf(MAX_SITUATIONS*6)});
        try{while(c.moveToNext()){
            String id=c.getString(0);if(!objectCloudAllowed(context,sql,"SITUATION",id,0))continue;long changedAt=c.getLong(16),brainCreatedAt=c.getLong(15);boolean hasAction=c.getInt(17)!=0,connectorEnriched=c.getInt(18)!=0;long effectiveBrainAt=brainCreatedAt>=changedAt?brainCreatedAt:0;
            CognitiveNowPolicyV4.Evaluation e=CognitiveNowPolicyV4.evaluate(c.getString(1),c.getString(2),c.getDouble(9),c.getDouble(10),c.getDouble(11),c.getLong(7),c.getLong(8),c.getInt(13),effectiveBrainAt,hasAction,now);if(!e.eligible)continue;
            rows.add(new SituationPacketRow(id,c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),c.getLong(8),c.getDouble(9),e.nowScore,c.getDouble(10),c.getDouble(11),c.getLong(12),c.getInt(13),e.currentDeepBrain?c.getString(14):"",e.currentDeepBrain,changedAt,CognitiveReasoningFreshnessV4.isNew(changedAt,latestAppliedAt),connectorEnriched));
        }}finally{c.close();}
        Collections.sort(rows,new Comparator<SituationPacketRow>(){@Override public int compare(SituationPacketRow a,SituationPacketRow b){int n=Double.compare(b.nowAttention,a.nowAttention);if(n!=0)return n;if(a.newSinceDeepBrain!=b.newSinceDeepBrain)return a.newSinceDeepBrain?-1:1;if(a.currentDeepBrain!=b.currentDeepBrain)return a.currentDeepBrain?-1:1;if(a.currentDeepBrain&&a.brainRank!=b.brainRank)return Integer.compare(a.brainRank,b.brainRank);long au=a.relevantUntil>0?a.relevantUntil:Long.MAX_VALUE,bu=b.relevantUntil>0?b.relevantUntil:Long.MAX_VALUE;return Long.compare(au,bu);}});
        JSONArray out=new JSONArray();for(SituationPacketRow r:rows){if(out.length()>=MAX_SITUATIONS)break;ids.add(r.id);JSONObject x=new JSONObject();x.put("id",r.id);x.put("kind",r.kind);x.put("state",r.state);x.put("headline",clip(r.headline,300));x.put("explanation",clip(r.explanation,500));x.put("primary_world_id",r.primaryWorldId);x.put("semantic_anchor",clip(r.semanticAnchor,240));x.put("relevant_from",r.relevantFrom);x.put("relevant_until",r.relevantUntil);x.put("attention_score",r.nowAttention);x.put("canonical_attention_score",r.canonicalAttention);x.put("interruption_score",r.interruption);x.put("confidence",r.confidence);x.put("last_evaluated_at",r.lastEvaluatedAt);x.put("changed_at",r.changedAt);if(r.newSinceDeepBrain)x.put("new_since_deep_brain",true);if(r.connectorEnriched)x.put("connector_enriched",true);if(r.currentDeepBrain&&r.brainRank>0)x.put("deep_brain_rank",r.brainRank);if(r.currentDeepBrain&&!r.brainReason.isEmpty())x.put("deep_brain_reason",clip(r.brainReason,500));out.put(x);}return out;
    }

    /** Supporting Memories for selected Situations are guaranteed space before generic recency. */
    private static JSONArray memories(Context context,SQLiteDatabase sql,LinkedHashSet<String>situationIds,LinkedHashSet<String>ids)throws Exception{
        JSONArray out=new JSONArray();LinkedHashSet<String>preferred=new LinkedHashSet<>();
        for(String situationId:situationIds){Cursor p=sql.rawQuery("SELECT source_id FROM v4_provenance WHERE object_type='SITUATION' AND object_id=? AND source_type='MEMORY' ORDER BY created_at DESC",new String[]{situationId});try{while(p.moveToNext()){String id=clean(p.getString(0));if(!id.isEmpty())preferred.add(id);}}finally{p.close();}}
        for(String id:preferred){if(out.length()>=MAX_MEMORIES)break;appendMemory(context,sql,id,ids,out);}
        Cursor c=sql.rawQuery("SELECT id FROM v4_memories WHERE state='ACTIVE' ORDER BY started_at DESC,id DESC LIMIT ?",new String[]{String.valueOf(MAX_MEMORIES*6)});try{while(c.moveToNext()&&out.length()<MAX_MEMORIES){String id=c.getString(0);if(ids.contains(id))continue;appendMemory(context,sql,id,ids,out);}}finally{c.close();}return out;
    }
    private static boolean appendMemory(Context context,SQLiteDatabase sql,String id,LinkedHashSet<String>ids,JSONArray out)throws Exception{
        if(id==null||id.trim().isEmpty()||ids.contains(id)||out.length()>=MAX_MEMORIES)return false;if(!memoryCloudAllowed(context,sql,id))return false;
        Cursor c=sql.rawQuery("SELECT kind,COALESCE(title,''),body,COALESCE(source_package,''),started_at,importance,pinned FROM v4_memories WHERE id=? AND state='ACTIVE' LIMIT 1",new String[]{id});try{if(!c.moveToFirst())return false;JSONObject x=new JSONObject();x.put("id",id);x.put("kind",c.getString(0));x.put("title",clip(c.getString(1),220));x.put("body",clip(c.getString(2),700));x.put("source_package",c.getString(3));x.put("started_at",c.getLong(4));x.put("importance",c.getDouble(5));x.put("pinned",c.getInt(6)!=0);JSONArray evidence=new JSONArray();Cursor e=sql.rawQuery("SELECT evidence_id FROM v4_memory_evidence WHERE memory_id=? ORDER BY ordinal ASC,evidence_id ASC LIMIT 6",new String[]{id});try{while(e.moveToNext())evidence.put(e.getString(0));}finally{e.close();}x.put("evidence_ids",evidence);ids.add(id);out.put(x);return true;}finally{c.close();}
    }

    private static JSONArray worlds(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT id,canonical_name,type_hint,maturity,COALESCE(summary,''),last_active_at FROM v4_worlds WHERE status='ACTIVE' ORDER BY last_active_at DESC,id ASC LIMIT ?",new String[]{String.valueOf(MAX_WORLDS*4)});try{while(c.moveToNext()&&out.length()<MAX_WORLDS){String id=c.getString(0);if(!objectCloudAllowed(context,sql,"WORLD",id,0))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("name",clip(c.getString(1),180));x.put("type",c.getString(2));x.put("maturity",c.getString(3));x.put("summary",clip(c.getString(4),500));x.put("last_active_at",c.getLong(5));out.put(x);}}finally{c.close();}return out;}
    private static JSONArray facts(Context context,SQLiteDatabase sql,LinkedHashSet<String>ids)throws Exception{JSONArray out=new JSONArray();Cursor c=sql.rawQuery("SELECT id,COALESCE(subject_world_id,''),predicate,value,grounding,confidence,valid_from,valid_until FROM v4_facts WHERE status='ACTIVE' ORDER BY updated_at DESC,id ASC LIMIT ?",new String[]{String.valueOf(MAX_FACTS*4)});try{while(c.moveToNext()&&out.length()<MAX_FACTS){String id=c.getString(0);if(!objectCloudAllowed(context,sql,"FACT",id,0))continue;JSONObject x=new JSONObject();ids.add(id);x.put("id",id);x.put("subject_world_id",c.getString(1));x.put("predicate",clip(c.getString(2),160));x.put("value",clip(c.getString(3),500));x.put("grounding",c.getString(4));x.put("confidence",c.getDouble(5));x.put("valid_from",c.getLong(6));x.put("valid_until",c.getLong(7));out.put(x);}}finally{c.close();}return out;}
    private static boolean memoryCloudAllowed(Context context,SQLiteDatabase sql,String memoryId){Cursor c=sql.rawQuery("SELECT e.id,e.source_type,e.sensitivity FROM v4_memory_evidence me JOIN v4_evidence e ON e.id=me.evidence_id WHERE me.memory_id=?",new String[]{memoryId});try{while(c.moveToNext()){if(!evidenceCloudAllowed(context,c.getString(0),c.getString(1),c.getString(2)))return false;}}finally{c.close();}return true;}
    private static boolean evidenceCloudAllowed(Context context,String id,String sourceType,String sensitivity){if("RESTRICTED".equalsIgnoreCase(sensitivity))return false;if(context==null)return true;String key=privacyKey(sourceType);return key.isEmpty()||PrivacyPolicy.canUseCloud(context,key);}
    private static String privacyKey(String sourceType){String t=sourceType==null?"":sourceType.toUpperCase(java.util.Locale.ROOT);if("NOTIFICATION".equals(t))return"notifications";if("VOICE".equals(t))return"audio";if("IMAGE".equals(t))return"images";if("FILE".equals(t))return"files";if("CONTACT".equals(t))return"contacts";if("CALENDAR".equals(t))return"calendar";if("APP_ACTIVITY".equals(t))return"app_usage";if("SCREEN".equals(t))return"screen_context";if("LOCATION".equals(t)||"SYSTEM".equals(t))return"phone_context";return"";}
    private static boolean objectCloudAllowed(Context context,SQLiteDatabase sql,String objectType,String objectId,int depth){if(context==null||depth>3)return true;Cursor c=sql.rawQuery("SELECT source_type,source_id FROM v4_provenance WHERE object_type=? AND object_id=?",new String[]{objectType,objectId});try{while(c.moveToNext()){String type=c.getString(0),id=c.getString(1);if("EVIDENCE".equals(type)){Cursor e=sql.rawQuery("SELECT source_type,sensitivity FROM v4_evidence WHERE id=? LIMIT 1",new String[]{id});try{if(e.moveToFirst()&&!evidenceCloudAllowed(context,id,e.getString(0),e.getString(1)))return false;}finally{e.close();}}else if("MEMORY".equals(type)){if(!memoryCloudAllowed(context,sql,id))return false;}else if("FACT".equals(type)){if(!objectCloudAllowed(context,sql,"FACT",id,depth+1))return false;}}}finally{c.close();}return true;}
    private static JSONArray array(Iterable<String>ids){JSONArray a=new JSONArray();for(String id:ids)a.put(id);return a;}private static String clip(String s,int n){String x=s==null?"":s.replace('\u0000',' ').trim();return x.length()<=n?x:x.substring(0,n)+"…";}

    private static final class SituationPacketRow{
        final String id,kind,state,headline,explanation,primaryWorldId,semanticAnchor,brainReason;final long relevantFrom,relevantUntil,lastEvaluatedAt,changedAt;final double canonicalAttention,nowAttention,interruption,confidence;final int brainRank;final boolean currentDeepBrain,newSinceDeepBrain,connectorEnriched;
        SituationPacketRow(String id,String kind,String state,String headline,String explanation,String primaryWorldId,String semanticAnchor,long relevantFrom,long relevantUntil,double canonicalAttention,double nowAttention,double interruption,double confidence,long lastEvaluatedAt,int brainRank,String brainReason,boolean currentDeepBrain,long changedAt,boolean newSinceDeepBrain,boolean connectorEnriched){this.id=id;this.kind=kind;this.state=state;this.headline=headline;this.explanation=explanation;this.primaryWorldId=primaryWorldId;this.semanticAnchor=semanticAnchor;this.relevantFrom=relevantFrom;this.relevantUntil=relevantUntil;this.canonicalAttention=canonicalAttention;this.nowAttention=nowAttention;this.interruption=interruption;this.confidence=confidence;this.lastEvaluatedAt=lastEvaluatedAt;this.brainRank=brainRank;this.brainReason=brainReason==null?"":brainReason;this.currentDeepBrain=currentDeepBrain;this.changedAt=changedAt;this.newSinceDeepBrain=newSinceDeepBrain;this.connectorEnriched=connectorEnriched;}
    }

    public static final class Packet{
        public final String requestId,question,contextJson,compactContextJson,shareText,compactText,exportJson;
        public final List<String>situationIds,memoryIds,worldIds,factIds;
        Packet(String requestId,String question,String contextJson,String compactContextJson,String shareText,String compactText,String exportJson,List<String>situationIds,List<String>memoryIds,List<String>worldIds,List<String>factIds){this.requestId=requestId;this.question=question;this.contextJson=contextJson;this.compactContextJson=compactContextJson;this.shareText=shareText;this.compactText=compactText;this.exportJson=exportJson;this.situationIds=Collections.unmodifiableList(situationIds);this.memoryIds=Collections.unmodifiableList(memoryIds);this.worldIds=Collections.unmodifiableList(worldIds);this.factIds=Collections.unmodifiableList(factIds);}
        /** Compatibility constructor used by regression fixtures created before compact transport existed. */
        Packet(String requestId,String question,String contextJson,String shareText,List<String>situationIds,List<String>memoryIds,List<String>worldIds,List<String>factIds){this(requestId,question,contextJson,contextJson,shareText,shareText,contextJson,situationIds,memoryIds,worldIds,factIds);}
    }
}
