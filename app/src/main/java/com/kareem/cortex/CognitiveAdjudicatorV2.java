package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

/**
 * Cortex-owned semantic authority for meaningful notification signals.
 *
 * Relay/native sensor -> Tier-0 hard noise gate -> bounded context -> typed CortexBrainRouter ->
 * strict parser/validator inside providers -> confidence routing -> CognitiveStore -> deterministic
 * priority -> canonical V4 Memory/Situation/Pulse. No admitted signal is allowed to disappear.
 */
public final class CognitiveAdjudicatorV2 {
    public static final String POLICY="cognitive_adjudicator_v2_003";
    private static final ScheduledExecutorService SCHEDULER=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static final ThreadPoolExecutor MODEL_EXECUTOR=new ThreadPoolExecutor(
            1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(LocalBrainConfig.MAX_QUEUE),new ThreadPoolExecutor.CallerRunsPolicy());
    private static final ConcurrentHashMap<Long,Slot> SLOTS=new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION=new AtomicLong();

    private CognitiveAdjudicatorV2(){}

    public static void enqueue(Context context,long threadId,long signalId){
        if(context==null||signalId<=0)return;Context app=context.getApplicationContext();long key=threadId>0?threadId:-signalId;
        SLOTS.compute(key,(ignored,old)->{
            if(old!=null&&old.future!=null)old.future.cancel(false);
            Slot next=new Slot(key,threadId,signalId,GENERATION.incrementAndGet());
            next.future=SCHEDULER.schedule(()->fire(app,next),LocalBrainConfig.MICRO_BATCH_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    private static void fire(Context app,Slot slot){if(!isCurrent(slot))return;MODEL_EXECUTOR.execute(()->adjudicate(app,slot));}

    private static void adjudicate(Context context,Slot slot){
        VaultDb db=null;long jobId=0,localRunId=0,selectedRunId=0,modelRunId=0;long selectedLatency=0;String selectedProvider="LOCAL",selectedModel=LocalModelManager.MODEL_NAME;
        try{
            db=new VaultDb(context);CognitiveStore.ensure(db);CognitiveRunStoreV2.ensure(db);
            SignalSnapshot snapshot=load(db,slot.signalId);if(snapshot==null||CognitiveSignalV2.terminal(snapshot.cognitiveState))return;
            if(!stillCurrent(db,slot)){markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.SUPERSEDED,0,"newer signal superseded this micro-batch",0,0,"CONTEXT");return;}
            if(MasterRelevanceFilter.sensitiveSignal(snapshot.signal)){
                markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.SENSITIVE_BLOCKED,0,"sensitive credential blocked before model adjudication",0,0,"CONTEXT");
                DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","sensitive_blocked","SENSITIVE_BLOCKED",0,slot.threadId,slot.signalId,0,0,0,null);return;
            }

            CognitiveSignalV2.SignalFamily family=SignalFamilyClassifier.classify(snapshot.signal);setFamily(db,slot.signalId,family);
            if(!LocalModelManager.installed(context)){
                markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,0,"local Qwen3-1.7B model/runtime unavailable; raw signal preserved for retry",0,0,"CONTEXT");
                DiagnosticsLog.warn(db,"CognitiveAdjudicatorV2","model_unavailable","MODEL_FAILED","LOCAL_MODEL_MISSING",0,slot.threadId,slot.signalId,0,0,null);return;
            }
            if(!LocalBrainRuntimePolicy.thermalAllowsInference(context)){
                markQueued(db,slot.signalId,family,"thermal pause; capture remains active and inference will retry");
                DiagnosticsLog.warn(db,"CognitiveAdjudicatorV2","thermal_pause","LOCAL_QUEUED","THERMAL_PAUSED",0,slot.threadId,slot.signalId,0,0,new JSONObject().put("thermal_status",LocalBrainRuntimePolicy.thermalStatus(context)));
                scheduleThermalRetry(context,slot);return;
            }

            String latest=bestLatestText(db,snapshot);List<String> recent=recentContext(db,slot.threadId,slot.signalId,LocalBrainConfig.MAX_THREAD_HISTORY);
            String baseline=baselineDecision(snapshot,family,latest,recent);
            CognitiveInput cognitiveInput=new CognitiveInput(slot.signalId,family,snapshot.signal.source,sourceApp(snapshot.signal),sender(snapshot.signal),latest,recent,snapshot.signal.occurredAt,TimeZone.getDefault().getID(),baseline);
            JSONObject jobInput=new JSONObject().put("thread_id",slot.threadId).put("latest_signal_id",slot.signalId).put("generation",slot.generation).put("signal_family",family.name()).put("source_package",snapshot.signal.source).put("occurred_at",snapshot.signal.occurredAt).put("baseline",new JSONObject(baseline)).put("context_count",recent.size());
            jobId=AiJobStore.create(db,"cognitive_adjudication_v2","your_data",jobInput.toString(),40);AiJobStore.start(db,jobId,"Understanding signal","Typed local-first Cognitive Brain V2");

            CortexBrainRouter router=new CortexBrainRouter(context);boolean allowRemote=remoteAllowed(family,snapshot.signal);
            localRunId=CognitiveRunStoreV2.queued(db,slot.signalId,"LOCAL",LocalModelManager.MODEL_NAME);CognitiveRunStoreV2.running(db,localRunId);selectedRunId=localRunId;

            RouteAttempt attempt=routeWithRecovery(router,cognitiveInput,allowRemote);
            CortexBrainRouter.RoutedCognitiveResult routed=attempt.routed;CognitiveResult result=routed.result;
            if(result==null)throw new BrainException("COGNITIVE_RESULT_MISSING","Typed brain route returned no cognitive result");

            if(routed.escalated){
                if(!attempt.localFailed)CognitiveRunStoreV2.escalated(db,localRunId,"local confidence "+fmt(routed.localConfidence)+" required optional Deep Qwen");
                long deepRun=CognitiveRunStoreV2.queued(db,slot.signalId,"DEEP",router.deepModel());CognitiveRunStoreV2.running(db,deepRun);
                if(!routed.deepError.isEmpty()){
                    CognitiveRunStoreV2.failed(db,deepRun,routed.deepError,routed.deepLatencyMs);
                    selectedRunId=localRunId;selectedProvider="LOCAL";selectedModel=LocalModelManager.MODEL_NAME;selectedLatency=routed.localLatencyMs;
                }else{
                    selectedRunId=deepRun;selectedProvider=routed.provider;selectedModel=routed.model;selectedLatency=routed.deepLatencyMs;
                }
            }else{
                selectedProvider=routed.provider;selectedModel=routed.model;selectedLatency=routed.localLatencyMs;
            }

            modelRunId=recordModelRun(db,jobId,routed,cognitiveInput,result);
            if(!stillCurrent(db,slot)){
                markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.SUPERSEDED,selectedRunId,"newer signal arrived before apply",0,result.confidence,"CONTEXT");
                CognitiveRunStoreV2.rejected(db,selectedRunId,"superseded",result.toJson().toString(),selectedLatency);return;
            }

            ApplyResult applied=apply(db,snapshot,family,result,selectedRunId,selectedProvider,selectedModel,selectedLatency,baseline);
            if(!applied.success){
                CognitiveRunStoreV2.rejected(db,selectedRunId,applied.detail,result.toJson().toString(),selectedLatency);markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,selectedRunId,applied.detail,0,result.confidence,"CONTEXT");AiJobStore.fail(db,jobId,applied.detail,"Validated result could not be persisted");return;
            }
            if(routed.deepError.isEmpty())CognitiveRunStoreV2.succeeded(db,selectedRunId,result.disposition.name(),result.confidence,result.toJson().toString(),selectedLatency);
            else if(selectedRunId==localRunId)CognitiveRunStoreV2.succeeded(db,localRunId,"REVIEW",result.confidence,result.toJson().toString(),selectedLatency);

            JSONObject done=new JSONObject().put("outcome",applied.state.name()).put("derived_count",applied.derivedCount).put("primary_memory_id",applied.primaryMemoryId).put("max_priority",applied.maxPriority).put("brain_provider",selectedProvider).put("brain_model",selectedModel).put("deep_error",routed.deepError);
            AiJobStore.complete(db,jobId,done.toString(),"Cognitive adjudication complete",applied.detail);
            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","outcome_applied",applied.state.name(),applied.primaryMemoryId,snapshot.threadId,snapshot.id,jobId,modelRunId,selectedLatency,new JSONObject().put("family",family.name()).put("provider",selectedProvider).put("model",selectedModel).put("confidence",result.confidence).put("priority",applied.maxPriority).put("deep_error",routed.deepError));

            if(applied.state==CognitiveSignalV2.CognitiveState.DERIVED&&applied.primaryMemoryId>0){
                try{CognitiveMemoryBackfillV4.runBatch(db,24);CognitiveSituationEngineV4.Result refresh=CognitiveSituationEngineV4.refresh(db);CognitiveDeepBrainReconcilerV4.reconcile(db);if(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(refresh))CognitiveReasoningOrchestratorV4.schedule(context,"cognitive_adjudicator_v2");}
                catch(Throwable e){DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","v4_projection",e,"V4_PROJECTION",applied.primaryMemoryId,snapshot.threadId,snapshot.id,jobId,modelRunId,null);}
            }
        }catch(BrainException e){
            if(db!=null){try{if(localRunId>0)CognitiveRunStoreV2.failed(db,localRunId,e.code+": "+n(e.getMessage()),0);if("THERMAL_PAUSED".equals(e.code)){SignalSnapshot s=load(db,slot.signalId);setFamily(db,slot.signalId,s==null?CognitiveSignalV2.SignalFamily.UNKNOWN:SignalFamilyClassifier.classify(s.signal));markQueued(db,slot.signalId,s==null?CognitiveSignalV2.SignalFamily.UNKNOWN:SignalFamilyClassifier.classify(s.signal),"thermal pause; retry scheduled");scheduleThermalRetry(context,slot);}else{markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,localRunId,e.code+": "+n(e.getMessage()),0,0,"CONTEXT");if(jobId>0)AiJobStore.fail(db,jobId,n(e.getMessage()),"Local/Deep brain execution failed safely");}}catch(Throwable ignored){}}
        }catch(Throwable e){
            if(db!=null){try{String error=e.getClass().getSimpleName()+": "+n(e.getMessage());if(selectedRunId>0)CognitiveRunStoreV2.failed(db,selectedRunId,error,selectedLatency);markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,selectedRunId,error,0,0,"CONTEXT");if(jobId>0)AiJobStore.fail(db,jobId,error,"Cognitive adjudication failed safely");DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","adjudicate",e,"COGNITIVE_ADJUDICATION_V2",0,slot.threadId,slot.signalId,jobId,modelRunId,null);}catch(Throwable ignored){}}
        }finally{SLOTS.remove(slot.key,slot);if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static RouteAttempt routeWithRecovery(CortexBrainRouter router,CognitiveInput input,boolean allowRemote)throws BrainException{
        try{return new RouteAttempt(router.classify(input,allowRemote),false);}catch(BrainException first){
            if("THERMAL_PAUSED".equals(first.code)||"LOCAL_MODEL_MISSING".equals(first.code))throw first;
            try{return new RouteAttempt(router.classify(input,allowRemote),false);}catch(BrainException second){
                if(allowRemote&&router.deepAvailable())return new RouteAttempt(router.deepFallback(input),true);
                throw second;
            }
        }
    }

    private static ApplyResult apply(VaultDb db,SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,CognitiveResult result,long runId,String provider,String model,long latency,String baseline){
        if(result==null||result.disposition==null)return ApplyResult.failed("missing validated cognitive result");String reason=clip(result.reason,500);
        if(result.disposition==CognitiveDisposition.IGNORE){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.IGNORED_NOISE,runId,reason.isEmpty()?"semantic ignore":reason,0,result.confidence,"IGNORE");return ApplyResult.simple(CognitiveSignalV2.CognitiveState.IGNORED_NOISE,reason);}
        if(result.disposition==CognitiveDisposition.CONTEXT){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.CONTEXT_ONLY,runId,reason.isEmpty()?"useful context without durable intelligence":reason,0,result.confidence,"CONTEXT");return ApplyResult.simple(CognitiveSignalV2.CognitiveState.CONTEXT_ONLY,reason);}
        if(result.disposition==CognitiveDisposition.REVIEW){
            markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.REVIEW_REQUIRED,runId,reason.isEmpty()?"cognitive result requires review":reason,45,result.confidence,"REVIEW");enqueueLegacyReviewIfGrounded(db,snapshot,baseline,result);return ApplyResult.simple(CognitiveSignalV2.CognitiveState.REVIEW_REQUIRED,reason);
        }
        if(result.items.isEmpty())return ApplyResult.failed("DERIVE result contained no validated items");

        long primaryMemoryId=0;int count=0,maxPriority=0;String firstKind="MEMORY";
        for(CognitiveItem item:result.items){Persisted p=persistItem(db,snapshot,family,item,result,runId,provider,model,latency);if(!p.success)return ApplyResult.failed(p.detail);if(primaryMemoryId<=0)primaryMemoryId=p.memoryId;if(count==0)firstKind=item.kind.name();count++;maxPriority=Math.max(maxPriority,p.priorityScore);}
        if(primaryMemoryId<=0||count==0)return ApplyResult.failed("no grounded durable item materialized");
        ContentValues raw=new ContentValues();raw.put("state","promoted");raw.put("promoted_item_id",primaryMemoryId);raw.put("retention_until",0);raw.put("disposition",legacyDisposition(firstKind));raw.put("importance",maxPriority);raw.put("confidence",result.confidence);raw.put("policy_version",POLICY);raw.put("filter_engine","cognitive_adjudicator_v2");raw.put("reason",reason);raw.put("cognitive_state",CognitiveSignalV2.CognitiveState.DERIVED.name());raw.put("cognitive_run_id",runId);raw.put("final_reason",reason);raw.put("updated_at",System.currentTimeMillis());if(db.getWritableDatabase().update("raw_signals",raw,"id=?",new String[]{String.valueOf(snapshot.id)})<=0)return ApplyResult.failed("raw signal final cognitive transition failed");
        return new ApplyResult(true,CognitiveSignalV2.CognitiveState.DERIVED,count,primaryMemoryId,maxPriority,reason.isEmpty()?"validated derived intelligence persisted":reason);
    }

    private static Persisted persistItem(VaultDb db,SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,CognitiveItem item,CognitiveResult result,long runId,String provider,String model,long latency){
        try{
            long now=System.currentTimeMillis();int priority=PriorityEngine.score(item.importance,item.urgency,item.kind,item.requiresUserAction,item.requiresFollowUp,item.requiresContentExtraction,item.dueAt,snapshot.signal.occurredAt,50,family==CognitiveSignalV2.SignalFamily.SECURITY&&item.importance>=80,now);
            JSONObject meta=new JSONObject().put("policy_version",POLICY).put("raw_signal_id",snapshot.id).put("source",snapshot.signal.source).put("signal_family",family.name()).put("cognitive_run_id",runId).put("brain_provider",provider).put("brain_model",model).put("brain_latency_ms",latency).put("brain_confidence",result.confidence).put("kind",item.kind.name()).put("importance",priority).put("model_importance",item.importance).put("urgency",item.urgency).put("priority_score",priority).put("person",item.person.isEmpty()?JSONObject.NULL:item.person).put("due_at",item.dueAt>0?item.dueAt:JSONObject.NULL).put("requires_user_action",item.requiresUserAction).put("requires_follow_up",item.requiresFollowUp).put("requires_content_extraction",item.requiresContentExtraction).put("reason",result.reason);if(snapshot.threadId>0)meta.put("thread_id",snapshot.threadId);
            CognitiveStore.CognitiveItemWrite write=CognitiveStore.addCognitiveItem(db,item,snapshot.id,snapshot.threadId,runId,snapshot.signal.source,result.confidence,priority,meta.toString());if(!write.success)return Persisted.failed(write.detail);
            String tags="signal,notification,cognitive_v2,"+item.kind.name().toLowerCase(Locale.ROOT)+",priority_"+priority;String fp=Fingerprint.text("cognitive-v2-memory|"+snapshot.id+"|"+item.kind.name()+"|"+write.semanticKey);long inserted=db.insert("NOTIFICATION",snapshot.signal.source,item.summary,item.summary,category(item.kind),tags,"",fp,meta.toString());long memoryId=inserted<0?-inserted:inserted;if(memoryId<=0)return Persisted.failed("knowledge item persistence failed");
            CognitiveStore.link(db,"raw_signal",snapshot.id,"memory",memoryId,"promoted_to",1.0,"{\"policy\":\""+POLICY+"\"}");CognitiveStore.link(db,"derived",write.derivedId,"memory",memoryId,"grounded_by",1.0,"");if(snapshot.threadId>0){CognitiveStore.link(db,"memory",memoryId,"thread",snapshot.threadId,"from_thread",1.0,"");CognitiveStore.link(db,"derived",write.derivedId,"thread",snapshot.threadId,"derived_from_thread",1.0,"");}
            return new Persisted(true,memoryId,priority,"");
        }catch(Throwable e){return Persisted.failed(e.getClass().getSimpleName()+": "+n(e.getMessage()));}
    }

    private static long recordModelRun(VaultDb db,long jobId,CortexBrainRouter.RoutedCognitiveResult routed,CognitiveInput input,CognitiveResult result){
        try{JSONObject meta=new JSONObject().put("provider",routed.provider).put("model",routed.model).put("escalated",routed.escalated).put("local_confidence",routed.localConfidence).put("local_latency_ms",routed.localLatencyMs).put("deep_latency_ms",routed.deepLatencyMs).put("deep_error",routed.deepError).put("result",result.toJson());return AiJobStore.modelRun(db,jobId,routed.escalated?2:1,"cognitive_adjudicator",routed.provider.toLowerCase(Locale.ROOT),routed.model,"signal_cognition_v2:typed","validated",Fingerprint.text(input.signalId+"|"+input.latestText+"|"+input.recentContext.toString()),routed.selectedLatencyMs(),0,0,result.confidence,meta.toString(),routed.deepError);}catch(Throwable ignored){return 0;}
    }

    private static String baselineDecision(SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,String latest,List<String> recent){
        try{String context=join(recent);MasterRelevanceFilter.Decision d=(family==CognitiveSignalV2.SignalFamily.COMMUNICATION&&snapshot.threadId>0)?MasterRelevanceFilter.evaluateThread(latest,context):MasterRelevanceFilter.evaluateTier0(snapshot.signal);return new JSONObject().put("disposition",d.disposition.name()).put("importance",d.importance).put("confidence",d.confidence).put("reason",d.reason).toString();}catch(Throwable e){return"{\"disposition\":\"CONTEXT\",\"importance\":30,\"confidence\":0.5,\"reason\":\"baseline unavailable\"}";}
    }

    private static void enqueueLegacyReviewIfGrounded(VaultDb db,SignalSnapshot snapshot,String baseline,CognitiveResult result){
        try{JSONObject b=new JSONObject(baseline);String candidate=b.optString("disposition","").toUpperCase(Locale.ROOT);if(!("ACTION".equals(candidate)||"WAITING".equals(candidate)||"DECISION".equals(candidate)))return;ReviewQueueStore.enqueue(db,candidate,snapshot.signal.title,bestBody(snapshot.signal),result.confidence,Math.max(40,b.optInt("importance",40)),snapshot.threadId,snapshot.id,result.reason,snapshot.signal.source,bestBody(snapshot.signal));}catch(Throwable ignored){}
    }

    private static SignalSnapshot load(VaultDb db,long signalId){Cursor c=db.getReadableDatabase().rawQuery("SELECT id,COALESCE(source,''),COALESCE(title,''),COALESCE(body,''),COALESCE(metadata_json,''),occurred_at,thread_id,COALESCE(cognitive_state,'') FROM raw_signals WHERE id=? LIMIT 1",new String[]{String.valueOf(signalId)});try{if(!c.moveToFirst())return null;long id=c.getLong(0),occurred=c.getLong(5),thread=c.getLong(6);String source=n(c.getString(1)),title=n(c.getString(2)),body=n(c.getString(3)),meta=n(c.getString(4));boolean ongoing=false;try{ongoing=new JSONObject(meta).optBoolean("ongoing",false);}catch(Throwable ignored){}return new SignalSnapshot(id,thread,new MasterRelevanceFilter.Signal("notification",source,title,body,meta,occurred,ongoing),n(c.getString(7)));}finally{c.close();}}

    private static List<String> recentContext(VaultDb db,long threadId,long signalId,int limit){
        if(threadId<=0)return Collections.emptyList();ArrayList<String> newest=new ArrayList<>();Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(title,''),COALESCE(body,'') FROM raw_signals WHERE thread_id=? AND id<>? AND cognitive_state NOT IN ('IGNORED_NOISE','SENSITIVE_BLOCKED') ORDER BY occurred_at DESC,id DESC LIMIT ?",new String[]{String.valueOf(threadId),String.valueOf(signalId),String.valueOf(Math.max(1,Math.min(5,limit)))});try{while(c.moveToNext()){String title=n(c.getString(0)),body=n(c.getString(1));String x=(title+(title.isEmpty()||body.isEmpty()?"":"\n")+body).trim();if(!x.isEmpty())newest.add(clip(x,1200));}}finally{c.close();}Collections.reverse(newest);return newest;}
    private static String bestLatestText(VaultDb db,SignalSnapshot s){String base=bestBody(s.signal);try{Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(ea.output_text,'') FROM v4_legacy_map m JOIN v4_evidence_analysis ea ON ea.evidence_id=m.object_id WHERE m.legacy_table='raw_signals' AND m.legacy_id=? AND m.object_type='EVIDENCE' AND ea.analysis_kind='CONNECTOR_ENRICHMENT' ORDER BY ea.created_at DESC,ea.id DESC LIMIT 1",new String[]{String.valueOf(s.id)});String e;try{e=c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}if(!e.isEmpty()&&(base.isEmpty()||e.length()>=base.length()))return clip(e,1600);}catch(Throwable ignored){}return clip(base,1600);}
    private static String bestBody(MasterRelevanceFilter.Signal s){return n(s.body).isEmpty()?n(s.title):n(s.body);}
    private static String sender(MasterRelevanceFilter.Signal s){try{JSONObject o=new JSONObject(n(s.metadataJson));String x=o.optString("person_hint","");if(x.isEmpty())x=o.optString("conversation_title","");if(!x.isEmpty())return clip(x,120);}catch(Throwable ignored){}return clip(s.title,120);}
    private static String sourceApp(MasterRelevanceFilter.Signal s){try{JSONObject o=new JSONObject(n(s.metadataJson));String x=o.optString("source_app","");if(x.isEmpty())x=o.optString("app_label","");if(!x.isEmpty())return clip(x,120);}catch(Throwable ignored){}return clip(s.source,120);}

    private static void markQueued(VaultDb db,long signalId,CognitiveSignalV2.SignalFamily family,String reason){ContentValues v=new ContentValues();v.put("signal_family",family.name());v.put("cognitive_state",CognitiveSignalV2.CognitiveState.LOCAL_QUEUED.name());v.put("final_reason",clip(reason,700));v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}
    private static void setFamily(VaultDb db,long signalId,CognitiveSignalV2.SignalFamily family){ContentValues v=new ContentValues();v.put("signal_family",family.name());v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}
    private static void markOutcome(VaultDb db,long signalId,CognitiveSignalV2.CognitiveState state,long runId,String reason,int importance,double confidence,String disposition){ContentValues v=new ContentValues();v.put("cognitive_state",state.name());v.put("cognitive_run_id",Math.max(0,runId));v.put("final_reason",clip(reason,700));v.put("reason",clip(reason,700));v.put("importance",Math.max(0,Math.min(100,importance)));v.put("confidence",Math.max(0,Math.min(1,confidence)));v.put("disposition",disposition);v.put("policy_version",POLICY);v.put("filter_engine","cognitive_adjudicator_v2");v.put("state",state==CognitiveSignalV2.CognitiveState.DERIVED?"promoted":"context_model_checked");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}

    private static boolean stillCurrent(VaultDb db,Slot slot){if(!isCurrent(slot))return false;if(slot.threadId<=0)return true;Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM raw_signals WHERE thread_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(slot.threadId)});try{return c.moveToFirst()&&c.getLong(0)==slot.signalId;}finally{c.close();}}
    private static boolean isCurrent(Slot slot){Slot x=slot==null?null:SLOTS.get(slot.key);return x==slot&&x.generation==slot.generation;}
    private static boolean remoteAllowed(CognitiveSignalV2.SignalFamily family,MasterRelevanceFilter.Signal signal){return !MasterRelevanceFilter.sensitiveSignal(signal)&&family!=CognitiveSignalV2.SignalFamily.SECURITY&&family!=CognitiveSignalV2.SignalFamily.TRANSACTION;}
    private static void scheduleThermalRetry(Context context,Slot slot){Context app=context.getApplicationContext();SCHEDULER.schedule(()->enqueue(app,slot.threadId,slot.signalId),60,TimeUnit.SECONDS);}

    private static String category(CognitiveKind k){if(k==CognitiveKind.ACTION)return"Actions";if(k==CognitiveKind.WAITING)return"Waiting";if(k==CognitiveKind.DECISION)return"Decisions";if(k==CognitiveKind.EVENT||k==CognitiveKind.REMINDER)return"Events";if(k==CognitiveKind.CONTENT)return"Content";return"Memory";}
    private static String legacyDisposition(String kind){String x=n(kind).toUpperCase(Locale.ROOT);return x.equals("ACTION")||x.equals("WAITING")||x.equals("DECISION")?x:"MEMORY";}
    private static String join(List<String> xs){StringBuilder b=new StringBuilder();if(xs!=null)for(String x:xs){if(b.length()>0)b.append('\n');b.append(x);}return b.toString();}
    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ");return x.length()<=max?x:x.substring(0,max);}
    private static String fmt(double x){return String.format(Locale.US,"%.2f",x);}
    private static String n(String s){return s==null?"":s.trim();}

    private static final class RouteAttempt{final CortexBrainRouter.RoutedCognitiveResult routed;final boolean localFailed;RouteAttempt(CortexBrainRouter.RoutedCognitiveResult r,boolean f){routed=r;localFailed=f;}}
    private static final class SignalSnapshot{final long id,threadId;final MasterRelevanceFilter.Signal signal;final String cognitiveState;SignalSnapshot(long i,long t,MasterRelevanceFilter.Signal s,String c){id=i;threadId=t;signal=s;cognitiveState=c;}}
    private static final class Slot{final long key,threadId,signalId,generation;volatile ScheduledFuture<?> future;Slot(long k,long t,long s,long g){key=k;threadId=t;signalId=s;generation=g;}}
    private static final class Persisted{final boolean success;final long memoryId;final int priorityScore;final String detail;Persisted(boolean s,long m,int p,String d){success=s;memoryId=m;priorityScore=p;detail=n(d);}static Persisted failed(String d){return new Persisted(false,0,0,d);}}
    private static final class ApplyResult{final boolean success;final CognitiveSignalV2.CognitiveState state;final int derivedCount,maxPriority;final long primaryMemoryId;final String detail;ApplyResult(boolean s,CognitiveSignalV2.CognitiveState st,int c,long m,int p,String d){success=s;state=st;derivedCount=c;primaryMemoryId=m;maxPriority=p;detail=n(d);}static ApplyResult simple(CognitiveSignalV2.CognitiveState st,String d){return new ApplyResult(true,st,0,0,0,d);}static ApplyResult failed(String d){return new ApplyResult(false,CognitiveSignalV2.CognitiveState.MODEL_FAILED,0,0,0,d);}}
}
