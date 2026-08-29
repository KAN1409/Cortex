package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small write API over the unified cognitive schema. */
public final class CognitiveStore {
    private CognitiveStore(){}

    public static void ensure(VaultDb db){CognitiveSchema.ensure(db.getWritableDatabase());}

    public static void link(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){linkChecked(db,fromType,fromId,toType,toId,relation,confidence,metadataJson);}
    public static boolean linkChecked(VaultDb db,String fromType,long fromId,String toType,long toId,String relation,double confidence,String metadataJson){
        if(fromId<=0||toId<=0||empty(fromType)||empty(toType)||empty(relation))return false;
        ensure(db);ContentValues v=new ContentValues();v.put("from_type",fromType);v.put("from_id",fromId);v.put("to_type",toType);v.put("to_id",toId);v.put("relation",relation);v.put("confidence",confidence);v.put("metadata_json",n(metadataJson));v.put("created_at",System.currentTimeMillis());
        long id=db.getWritableDatabase().insertWithOnConflict("source_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return true;
        Cursor c=db.getReadableDatabase().query("source_links",new String[]{"id"},"from_type=? AND from_id=? AND to_type=? AND to_id=? AND relation=?",new String[]{fromType,String.valueOf(fromId),toType,String.valueOf(toId),relation},null,null,null,"1");boolean exists=c.moveToFirst();c.close();return exists;
    }

    public static long addDerived(VaultDb db,String kind,String title,String body,String state,double confidence,int importance,String fingerprint,String metadataJson){
        ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("kind",n(kind).toUpperCase());v.put("title",empty(title)?friendly(kind):title.trim());v.put("body",n(body));v.put("state",empty(state)?"open":state);v.put("confidence",confidence);v.put("importance",importance);v.put("fingerprint",n(fingerprint));v.put("metadata_json",n(metadataJson));v.put("created_at",now);v.put("updated_at",now);
        SQLiteDatabase sql=db.getWritableDatabase();long id=sql.insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        if(empty(fingerprint))return id;

        Cursor c=sql.query("derived_items",new String[]{"id","state"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long existing=0;String existingState="";if(c.moveToFirst()){existing=c.getLong(0);existingState=n(c.getString(1));}c.close();if(existing<=0)return 0;
        if(active(existingState))return existing;

        // The same semantic obligation may legitimately recur after completion/expiry.
        // Archive the historical fingerprint, then let the canonical fingerprint identify the new active occurrence.
        ContentValues history=new ContentValues();history.put("fingerprint",Fingerprint.text("historical-derived|"+fingerprint+"|"+existing));history.put("updated_at",now);if(sql.update("derived_items",history,"id=? AND fingerprint=?",new String[]{String.valueOf(existing),fingerprint})<=0)return 0;
        long retry=sql.insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(retry>0)return retry;
        Cursor current=sql.query("derived_items",new String[]{"id","state"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long currentId=0;if(current.moveToFirst()&&active(n(current.getString(1))))currentId=current.getLong(0);current.close();return currentId;
    }

    /** Hot routing fields stay typed/indexed; metadata_json remains flexible provenance. */
    public static void setDerivedRouting(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind){setDerivedRoutingChecked(db,derivedId,sourceKey,threadId,anchorSignalId,candidateKind,"");}
    public static boolean setDerivedRoutingChecked(VaultDb db,long derivedId,String sourceKey,long threadId,long anchorSignalId,String candidateKind,String semanticKey){
        if(db==null||derivedId<=0)return false;ensure(db);ContentValues v=new ContentValues();v.put("source_key",n(sourceKey));v.put("thread_id",Math.max(0,threadId));v.put("anchor_signal_id",Math.max(0,anchorSignalId));v.put("candidate_kind",n(candidateKind).toUpperCase());if(!empty(semanticKey))v.put("semantic_key",semanticKey);v.put("updated_at",System.currentTimeMillis());return db.getWritableDatabase().update("derived_items",v,"id=?",new String[]{String.valueOf(derivedId)})>0;
    }

    /**
     * Persist one authoritative Cognitive V2 item. The caller may already hold the DB transaction.
     * Qwen never receives a database handle and cannot write persistence directly.
     */
    public static long addCognitiveItem(VaultDb db,CognitiveItem item,long signalId,long threadId,long modelRunId,String policy){
        if(db==null||item==null||item.kind==null||signalId<=0||modelRunId<=0)return 0;
        ensure(db);SQLiteDatabase sql=db.getWritableDatabase();long now=System.currentTimeMillis();String kind=item.kind.name();String source=sourceForSignal(sql,signalId);double confidence=modelConfidence(sql,modelRunId);
        String semantic=Fingerprint.text(kind+"|"+n(item.summary)+"|"+n(item.person)+"|"+(item.dueAt==null?0:item.dueAt));
        String fingerprint=Fingerprint.text("cognitive-v2-canary|"+signalId+"|"+semantic);
        JSONObject meta=new JSONObject();try{meta.put("engine","cognitive_v2");meta.put("policy",n(policy));meta.put("authority","CANARY");meta.put("raw_signal_id",signalId);meta.put("thread_id",Math.max(0,threadId));meta.put("model_run_id",modelRunId);}catch(Throwable ignored){}
        ContentValues v=new ContentValues();v.put("kind",kind);v.put("title",empty(item.summary)?friendly(kind):item.summary);v.put("body",n(item.summary));v.put("state","open");v.put("confidence",confidence);v.put("importance",clamp100(item.importance));v.put("urgency",clamp100(item.urgency));v.put("person_key",n(item.person));v.put("due_at",item.dueAt==null?0:Math.max(0,item.dueAt));v.put("requires_user_action",item.requiresUserAction?1:0);v.put("requires_follow_up",item.requiresFollowUp?1:0);v.put("requires_content_extraction",item.requiresContentExtraction?1:0);v.put("model_run_id",modelRunId);v.put("priority_score",clamp100(item.importance));v.put("source_key",source);v.put("thread_id",Math.max(0,threadId));v.put("anchor_signal_id",signalId);v.put("candidate_kind",kind);v.put("semantic_key",semantic);v.put("fingerprint",fingerprint);v.put("metadata_json",meta.toString());v.put("created_at",now);v.put("updated_at",now);
        long id=sql.insertWithOnConflict("derived_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id>0)return id;
        Cursor c=sql.query("derived_items",new String[]{"id","model_run_id"},"fingerprint=?",new String[]{fingerprint},null,null,null,"1");long existing=0,owner=0;if(c.moveToFirst()){existing=c.getLong(0);owner=c.getLong(1);}c.close();return existing>0&&owner==modelRunId?existing:0;
    }

    /** Backward-compatible helper for tests/diagnostics that do not carry an explicit router decision. */
    public static CanaryApply applyCanaryAuthority(VaultDb db,long signalId,long threadId,CognitiveResult result,LocalBrainRun run,long latencyMs,String inputHash,String policy){
        return applyCanaryAuthority(
                db,signalId,threadId,result,run,latencyMs,inputHash,policy,
                CognitiveAuthorityRouter.RoutingReason.HASH_CANARY.name(),-1
        );
    }

    /** One short atomic authority transition: model provenance -> derived intelligence -> links -> raw truth. */
    public static CanaryApply applyCanaryAuthority(
            VaultDb db,
            long signalId,
            long threadId,
            CognitiveResult result,
            LocalBrainRun run,
            long latencyMs,
            String inputHash,
            String policy,
            String routingReason,
            int routingBucket
    ){
        if(db==null||signalId<=0||result==null||result.disposition==null||run==null)throw new IllegalArgumentException("invalid canary apply input");
        String routeReason=normalizedRoutingReason(routingReason);
        ensure(db);SQLiteDatabase sql=db.getWritableDatabase();long modelRunId=0;ArrayList<Long> derivedIds=new ArrayList<>();sql.beginTransaction();
        try{
            JSONObject output=canaryOutput(signalId,result,run,policy,"ACCEPTED",routeReason,routingBucket);
            modelRunId=AiJobStore.modelRun(db,0,1,"cognitive_authority","local",LocalModelManager.MODEL_NAME,"cognitive_v2_canary","complete",n(inputHash),Math.max(0,latencyMs),0,run.tokensGenerated,result.confidence,output.toString(),"");
            if(modelRunId<=0)throw new IllegalStateException("canary model_run persistence failed");

            String routeMeta=routingMetadata(policy,routeReason,routingBucket).toString();
            if(!linkChecked(db,"model_run",modelRunId,"raw_signal",signalId,"authoritative_evaluated",result.confidence,routeMeta))throw new IllegalStateException("canary model-to-signal provenance failed");

            CognitiveItem strongest=null;
            if(result.disposition==CognitiveDisposition.DERIVE){
                if(result.items==null||result.items.isEmpty())throw new IllegalStateException("accepted DERIVE has no items");
                for(CognitiveItem item:result.items){
                    if(item==null||item.kind==null)throw new IllegalStateException("accepted DERIVE contains invalid item");
                    long derivedId=addCognitiveItem(db,item,signalId,threadId,modelRunId,policy);if(derivedId<=0)throw new IllegalStateException("canary derived item persistence failed");derivedIds.add(derivedId);
                    if(!linkChecked(db,"raw_signal",signalId,"derived",derivedId,"supports",1.0,"{\"authority\":\"CANARY\"}"))throw new IllegalStateException("canary signal provenance failed");
                    if(!linkChecked(db,"model_run",modelRunId,"derived",derivedId,"generated",result.confidence,routeMeta))throw new IllegalStateException("canary model provenance failed");
                    if(threadId>0&&!linkChecked(db,"derived",derivedId,"thread",threadId,"derived_from_thread",1.0,"{\"authority\":\"CANARY\"}"))throw new IllegalStateException("canary thread provenance failed");
                    if(strongest==null||item.importance>strongest.importance||(item.importance==strongest.importance&&item.urgency>strongest.urgency))strongest=item;
                }
            }

            long now=System.currentTimeMillis();ContentValues raw=new ContentValues();raw.put("cognitive_version",n(policy));raw.put("cognitive_updated_at",now);raw.put("updated_at",now);raw.put("filter_engine","cognitive_v2");raw.put("policy_version",n(policy));raw.put("confidence",result.confidence);raw.put("reason",n(result.reason));raw.put("final_reason","V2 canary accepted local result at confidence "+String.format(Locale.US,"%.2f",result.confidence)+" via "+routeReason);
            if(result.disposition==CognitiveDisposition.DERIVE){raw.put("cognitive_state","DERIVED");raw.put("state","derived");raw.put("disposition",strongest==null?"CONTEXT":strongest.kind.name());raw.put("importance",strongest==null?0:clamp100(strongest.importance));}
            else if(result.disposition==CognitiveDisposition.CONTEXT){raw.put("cognitive_state","CONTEXT_ONLY");raw.put("state","context");raw.put("disposition","CONTEXT");raw.put("importance",0);}
            else throw new IllegalStateException("unsupported authoritative disposition "+result.disposition);

            int rawUpdated;
            if(threadId>0){
                rawUpdated=sql.update(
                        "raw_signals",
                        raw,
                        "id=? AND id=(SELECT id FROM raw_signals WHERE thread_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1)",
                        new String[]{String.valueOf(signalId),String.valueOf(threadId)});
                if(rawUpdated!=1)throw new IllegalStateException("CANARY_SUPERSEDED");
            }else{
                rawUpdated=sql.update("raw_signals",raw,"id=?",new String[]{String.valueOf(signalId)});
                if(rawUpdated!=1)throw new IllegalStateException("canary raw state transition failed");
            }
            sql.setTransactionSuccessful();return new CanaryApply(modelRunId,derivedIds,result.disposition);
        }finally{sql.endTransaction();}
    }

    public static boolean updateRawCognitiveState(VaultDb db,long signalId,String cognitiveState,String version,String finalReason){
        if(db==null||signalId<=0)return false;ensure(db);long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("cognitive_state",n(cognitiveState));v.put("cognitive_version",n(version));v.put("final_reason",n(finalReason));v.put("cognitive_updated_at",now);v.put("updated_at",now);return db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)})==1;
    }

    public static void feedback(VaultDb db,String targetType,long targetId,String eventType,String valueJson,String policyVersion){
        if(targetId<=0||empty(targetType)||empty(eventType))return;ensure(db);String source="",candidate="";
        if("derived".equalsIgnoreCase(targetType)){
            Cursor c=db.getReadableDatabase().query("derived_items",new String[]{"source_key","candidate_kind","kind"},"id=?",new String[]{String.valueOf(targetId)},null,null,null,"1");
            if(c.moveToFirst()){source=n(c.getString(0));candidate=n(c.getString(1));if(candidate.isEmpty()&&"REVIEW".equalsIgnoreCase(n(c.getString(2))))candidate="UNKNOWN";}c.close();
        }
        ContentValues v=new ContentValues();v.put("target_type",targetType);v.put("target_id",targetId);v.put("event_type",eventType);v.put("value_json",n(valueJson));v.put("policy_version",n(policyVersion));v.put("source_key",source);v.put("candidate_kind",candidate.toUpperCase());v.put("scope_key",source+"|"+candidate.toUpperCase());v.put("created_at",System.currentTimeMillis());db.getWritableDatabase().insert("feedback_events",null,v);
    }

    public static String schemaRevision(VaultDb db){ensure(db);Cursor c=db.getReadableDatabase().query("schema_meta",new String[]{"value"},"key='cognitive_schema'",null,null,null,null,"1");String x=c.moveToFirst()?c.getString(0):"";c.close();return x==null?"":x;}

    private static JSONObject canaryOutput(long signalId,CognitiveResult result,LocalBrainRun run,String policy,String outcome,String routingReason,int routingBucket){
        JSONObject root=new JSONObject();try{root.put("schema","cognitive_canary_001");root.put("signal_id",signalId);root.put("policy",n(policy));root.put("routing_reason",normalizedRoutingReason(routingReason));root.put("routing_bucket",routingBucket);root.put("outcome",outcome);root.put("disposition",result.disposition.name());root.put("confidence",result.confidence);root.put("reason",clip(result.reason,300));JSONArray items=new JSONArray();for(CognitiveItem item:result.items){if(item==null||item.kind==null)continue;JSONObject x=new JSONObject();x.put("kind",item.kind.name());x.put("summary",clip(item.summary,240));x.put("importance",item.importance);x.put("urgency",item.urgency);x.put("person",clip(item.person,120));x.put("due_at",item.dueAt==null?0:item.dueAt);x.put("requires_user_action",item.requiresUserAction);x.put("requires_follow_up",item.requiresFollowUp);x.put("requires_content_extraction",item.requiresContentExtraction);items.put(x);}root.put("items",items);root.put("tokens_per_second",run.tokensPerSecond);root.put("tokens_generated",run.tokensGenerated);root.put("generation_ms",run.generationMs);root.put("model_load_ms",run.modelLoadMs);root.put("duration_ms",run.durationMs);root.put("cache_hit",run.cacheHit);}catch(Throwable ignored){}return root;
    }

    private static JSONObject routingMetadata(String policy,String routingReason,int routingBucket){
        JSONObject meta=new JSONObject();
        try{
            meta.put("policy",n(policy));
            meta.put("routing_reason",normalizedRoutingReason(routingReason));
            meta.put("routing_bucket",routingBucket);
        }catch(Throwable ignored){}
        return meta;
    }

    private static String normalizedRoutingReason(String routingReason){
        String value=n(routingReason).trim();
        return value.isEmpty()?CognitiveAuthorityRouter.RoutingReason.HASH_CANARY.name():value;
    }

    private static String sourceForSignal(SQLiteDatabase sql,long signalId){Cursor c=sql.query("raw_signals",new String[]{"source"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");String x=c.moveToFirst()?n(c.getString(0)):"";c.close();return x;}
    private static double modelConfidence(SQLiteDatabase sql,long modelRunId){Cursor c=sql.query("model_runs",new String[]{"confidence"},"id=?",new String[]{String.valueOf(modelRunId)},null,null,null,"1");double x=c.moveToFirst()?c.getDouble(0):0;c.close();return x;}
    private static boolean active(String state){return"open".equalsIgnoreCase(n(state))||"pending".equalsIgnoreCase(n(state));}
    private static String friendly(String kind){String x=n(kind).toLowerCase(Locale.ROOT).replace('_',' ');return x.isEmpty()?"Derived intelligence":Character.toUpperCase(x.charAt(0))+x.substring(1);}
    private static int clamp100(int x){return Math.max(0,Math.min(100,x));}
    private static String clip(String s,int max){String x=n(s).trim();return x.length()<=max?x:x.substring(0,max);}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String n(String s){return s==null?"":s;}

    public static final class CanaryApply{
        public final long modelRunId;public final List<Long> derivedIds;public final CognitiveDisposition disposition;
        CanaryApply(long modelRunId,List<Long> derivedIds,CognitiveDisposition disposition){this.modelRunId=modelRunId;this.derivedIds=java.util.Collections.unmodifiableList(new ArrayList<>(derivedIds));this.disposition=disposition;}
    }
}
