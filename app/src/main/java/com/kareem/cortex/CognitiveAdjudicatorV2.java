package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cortex-owned semantic authority for meaningful notification signals.
 *
 * Pipeline:
 * Relay/native sensor -> Tier-0 hard noise gate -> micro-batched bounded context -> local Qwen ->
 * strict validation -> confidence routing -> optional Deep Qwen -> deterministic priority ->
 * canonical Memory/Situation/Pulse. Every admitted signal receives an explicit cognitive_state.
 */
public final class CognitiveAdjudicatorV2 {
    public static final String POLICY="cognitive_adjudicator_v2_002";
    private static final int MAX_ITEMS=5;
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
        VaultDb db=null;long jobId=0,modelRunId=0,cognitiveRunId=0;BrainCompletion completion=null;
        try{
            db=new VaultDb(context);CognitiveStore.ensure(db);CognitiveRunStoreV2.ensure(db);
            SignalSnapshot snapshot=load(db,slot.signalId);if(snapshot==null||terminal(snapshot.cognitiveState))return;
            if(!stillCurrent(db,slot)){markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.SUPERSEDED,0,"newer signal superseded this micro-batch",0,0,"CONTEXT");return;}
            if(MasterRelevanceFilter.sensitiveSignal(snapshot.signal)){
                markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.SENSITIVE_BLOCKED,0,"sensitive credential blocked before model adjudication",0,0,"CONTEXT");
                DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","sensitive_blocked","SENSITIVE_BLOCKED",0,slot.threadId,slot.signalId,0,0,0,null);return;
            }

            CognitiveSignalV2.SignalFamily family=CognitiveSignalV2.classify(snapshot.signal);setFamily(db,slot.signalId,family);
            if(!LocalModelManager.installed(context)){
                markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,0,"local Qwen3-1.7B model/runtime unavailable; raw signal preserved for retry",0,0,"CONTEXT");
                DiagnosticsLog.warn(db,"CognitiveAdjudicatorV2","model_unavailable","MODEL_FAILED","LOCAL_MODEL_MISSING",0,slot.threadId,slot.signalId,0,0,null);return;
            }
            if(!LocalBrainRuntimePolicy.thermalAllowsInference(context)){
                markPendingReason(db,slot.signalId,"thermal pause; capture remains active and inference will retry");
                DiagnosticsLog.warn(db,"CognitiveAdjudicatorV2","thermal_pause","PENDING_ADJUDICATION","THERMAL_PAUSED",0,slot.threadId,slot.signalId,0,0,new JSONObject().put("thermal_status",LocalBrainRuntimePolicy.thermalStatus(context)));
                scheduleThermalRetry(context,slot);return;
            }

            String recent=slot.threadId>0?SignalThreadStore.recentContext(db,slot.threadId,LocalBrainConfig.MAX_BATCH_SIGNALS):"";
            String latest=bestLatestText(db,snapshot);
            recent=clip(recent,Math.max(0,LocalBrainConfig.MAX_INPUT_CHARS-latest.length()-1000));
            JSONObject input=new JSONObject();input.put("thread_id",slot.threadId);input.put("latest_signal_id",slot.signalId);input.put("generation",slot.generation);input.put("signal_family",family.name());input.put("source_package",snapshot.signal.source);input.put("occurred_at",snapshot.signal.occurredAt);input.put("context_hash",Fingerprint.text(recent));
            jobId=AiJobStore.create(db,"cognitive_adjudication_v2","your_data",input.toString(),40);AiJobStore.start(db,jobId,"Understanding signal","Local Qwen micro-batch");
            BrainRequest request=new BrainRequest(systemPrompt(),buildPrompt(snapshot,family,latest,recent),LocalBrainConfig.MAX_OUTPUT_TOKENS);
            CortexBrainRouter router=new CortexBrainRouter(context);
            cognitiveRunId=CognitiveRunStoreV2.queued(db,slot.signalId,"LOCAL",LocalModelManager.MODEL_NAME);CognitiveRunStoreV2.running(db,cognitiveRunId);markRunning(db,slot.signalId,cognitiveRunId,family,"LOCAL",LocalModelManager.MODEL_NAME);

            completion=runLocalWithOneRetry(router,request);
            ParseResult localParsed=parse(completion.text,latest,recent,snapshot.signal.occurredAt);
            modelRunId=recordModelRun(db,jobId,1,completion,request,localParsed,"local");
            if(!localParsed.valid()){
                CognitiveRunStoreV2.rejected(db,cognitiveRunId,localParsed.error,completion.text,completion.latencyMs);
                BrainCompletion deep=tryDeepAfterLocalFailure(router,request,family);
                if(deep==null){markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,cognitiveRunId,"local model returned invalid JSON twice; no safe deep fallback",0,0,"CONTEXT");AiJobStore.fail(db,jobId,localParsed.error,"Signal preserved as MODEL_FAILED");return;}
                cognitiveRunId=beginDeepRun(db,slot.signalId,cognitiveRunId,deep);completion=deep;ParseResult deepParsed=parse(deep.text,latest,recent,snapshot.signal.occurredAt);modelRunId=recordModelRun(db,jobId,2,deep,request,deepParsed,"deep");
                if(!deepParsed.valid()){CognitiveRunStoreV2.rejected(db,cognitiveRunId,deepParsed.error,deep.text,deep.latencyMs);forceReview(db,snapshot,cognitiveRunId,"Deep Brain output invalid after local failure");AiJobStore.complete(db,jobId,"{\"outcome\":\"REVIEW_REQUIRED\"}","Deep fallback rejected","Human review required");return;}
                applyRouted(db,context,snapshot,family,deepParsed,cognitiveRunId,jobId,modelRunId,deep,slot);return;
            }

            double localConfidence=localParsed.result.confidence;
            boolean allowRemote=remoteAllowed(family,snapshot.signal);
            BrainCompletion routed=router.routeAfterLocal(request,completion,localConfidence,allowRemote);
            ParseResult routedParsed=localParsed;
            if(routed!=completion){
                CognitiveRunStoreV2.escalated(db,cognitiveRunId,"local confidence "+fmt(localConfidence)+" routed to Deep Qwen");
                cognitiveRunId=beginDeepRun(db,slot.signalId,cognitiveRunId,routed);completion=routed;routedParsed=parse(routed.text,latest,recent,snapshot.signal.occurredAt);modelRunId=recordModelRun(db,jobId,2,routed,request,routedParsed,"deep");
                if(!routedParsed.valid()){CognitiveRunStoreV2.rejected(db,cognitiveRunId,routedParsed.error,routed.text,routed.latencyMs);forceReview(db,snapshot,cognitiveRunId,"Deep Brain returned invalid structured result");AiJobStore.complete(db,jobId,"{\"outcome\":\"REVIEW_REQUIRED\"}","Deep result rejected","Human review required");return;}
            }

            if(completion.provider.equals("LOCAL")&&routedParsed.result.confidence<LocalBrainConfig.ACCEPT_LOCAL_CONFIDENCE){
                CognitiveRunStoreV2.succeeded(db,cognitiveRunId,"REVIEW",routedParsed.result.confidence,routedParsed.result.toJson(),completion.latencyMs);
                forceReview(db,snapshot,cognitiveRunId,routedParsed.result.confidence<LocalBrainConfig.TRY_DEEP_CONFIDENCE?"local confidence below 0.55":"local confidence below 0.78 and Deep Qwen unavailable/blocked");
                AiJobStore.complete(db,jobId,"{\"outcome\":\"REVIEW_REQUIRED\"}","Confidence routed to review","No silent fall-through");return;
            }
            if(completion.provider.equals("DEEP")&&routedParsed.result.confidence<LocalBrainConfig.TRY_DEEP_CONFIDENCE){
                CognitiveRunStoreV2.succeeded(db,cognitiveRunId,"REVIEW",routedParsed.result.confidence,routedParsed.result.toJson(),completion.latencyMs);forceReview(db,snapshot,cognitiveRunId,"Deep Brain confidence below 0.55");AiJobStore.complete(db,jobId,"{\"outcome\":\"REVIEW_REQUIRED\"}","Deep confidence low","Human review required");return;
            }

            applyRouted(db,context,snapshot,family,routedParsed,cognitiveRunId,jobId,modelRunId,completion,slot);
        }catch(BrainException e){
            if(db!=null){try{if(cognitiveRunId>0)CognitiveRunStoreV2.failed(db,cognitiveRunId,e.code+": "+e.getMessage(),completion==null?0:completion.latencyMs);if("THERMAL_PAUSED".equals(e.code)){markPendingReason(db,slot.signalId,"thermal pause; retry scheduled");scheduleThermalRetry(context,slot);}else{markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,cognitiveRunId,e.code+": "+e.getMessage(),0,0,"CONTEXT");if(jobId>0)AiJobStore.fail(db,jobId,e.getMessage(),"Local/Deep brain execution failed safely");}}catch(Throwable ignored){}}
        }catch(Throwable e){
            if(db!=null){try{String error=e.getClass().getSimpleName()+": "+n(e.getMessage());if(cognitiveRunId>0)CognitiveRunStoreV2.failed(db,cognitiveRunId,error,completion==null?0:completion.latencyMs);markOutcome(db,slot.signalId,CognitiveSignalV2.CognitiveState.MODEL_FAILED,cognitiveRunId,error,0,0,"CONTEXT");if(jobId>0)AiJobStore.fail(db,jobId,error,"Cognitive adjudication failed safely");DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","adjudicate",e,"COGNITIVE_ADJUDICATION_V2",0,slot.threadId,slot.signalId,jobId,modelRunId,null);}catch(Throwable ignored){}}
        }finally{SLOTS.remove(slot.key,slot);if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static BrainCompletion runLocalWithOneRetry(CortexBrainRouter router,BrainRequest request)throws BrainException{
        try{return router.classifyLocal(request);}catch(BrainException first){if("THERMAL_PAUSED".equals(first.code)||"LOCAL_MODEL_MISSING".equals(first.code))throw first;return router.classifyLocal(request);}
    }

    private static BrainCompletion tryDeepAfterLocalFailure(CortexBrainRouter router,BrainRequest request,CognitiveSignalV2.SignalFamily family){
        if(!router.deepAvailable()||family==CognitiveSignalV2.SignalFamily.SECURITY||family==CognitiveSignalV2.SignalFamily.TRANSACTION)return null;
        try{return router.routeAfterLocal(request,new BrainCompletion("","LOCAL",LocalModelManager.MODEL_NAME,0,0,0,false),LocalBrainConfig.TRY_DEEP_CONFIDENCE,true);}catch(Throwable ignored){return null;}
    }

    private static long beginDeepRun(VaultDb db,long signalId,long oldRun,BrainCompletion deep){
        if(oldRun>0)CognitiveRunStoreV2.escalated(db,oldRun,"escalated to optional Deep Qwen");long id=CognitiveRunStoreV2.queued(db,signalId,deep.provider,deep.model);CognitiveRunStoreV2.running(db,id);return id;
    }

    private static void applyRouted(VaultDb db,Context context,SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,ParseResult parsed,long cognitiveRunId,long jobId,long modelRunId,BrainCompletion completion,Slot slot)throws Exception{
        if(!stillCurrent(db,slot)){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.SUPERSEDED,cognitiveRunId,"newer signal arrived before apply",0,parsed.result.confidence,"CONTEXT");CognitiveRunStoreV2.rejected(db,cognitiveRunId,"superseded",parsed.result.toJson(),completion.latencyMs);return;}
        ApplyResult applied=apply(db,snapshot,family,parsed.result,cognitiveRunId,completion.provider,completion.model,completion.latencyMs);
        if(!applied.success){CognitiveRunStoreV2.rejected(db,cognitiveRunId,applied.detail,parsed.result.toJson(),completion.latencyMs);markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.MODEL_FAILED,cognitiveRunId,applied.detail,0,parsed.result.confidence,"CONTEXT");AiJobStore.fail(db,jobId,applied.detail,"Validated result could not be persisted");return;}
        CognitiveRunStoreV2.succeeded(db,cognitiveRunId,parsed.result.disposition.name(),parsed.result.confidence,parsed.result.toJson(),completion.latencyMs);
        JSONObject done=new JSONObject().put("outcome",applied.state.name()).put("derived_count",applied.derivedCount).put("primary_memory_id",applied.primaryMemoryId).put("max_priority",applied.maxPriority).put("brain_provider",completion.provider).put("brain_model",completion.model);
        AiJobStore.complete(db,jobId,done.toString(),"Cognitive adjudication complete",applied.detail);
        DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","outcome_applied",applied.state.name(),applied.primaryMemoryId,snapshot.threadId,snapshot.id,jobId,modelRunId,completion.latencyMs,new JSONObject().put("family",family.name()).put("provider",completion.provider).put("model",completion.model).put("confidence",parsed.result.confidence).put("priority",applied.maxPriority));
        if(applied.state==CognitiveSignalV2.CognitiveState.DERIVED&&applied.primaryMemoryId>0){
            try{CognitiveMemoryBackfillV4.runBatch(db,24);CognitiveSituationEngineV4.Result refresh=CognitiveSituationEngineV4.refresh(db);CognitiveDeepBrainReconcilerV4.reconcile(db);if(CognitiveRealtimeProjectionV4.shouldScheduleReasoning(refresh))CognitiveReasoningOrchestratorV4.schedule(context,"cognitive_adjudicator_v2");}catch(Throwable e){DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","v4_projection",e,"V4_PROJECTION",applied.primaryMemoryId,snapshot.threadId,snapshot.id,jobId,modelRunId,null);}
        }
    }

    private static ApplyResult apply(VaultDb db,SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,CognitiveResult result,long cognitiveRunId,String provider,String model,long latency){
        if(result==null)return ApplyResult.failed("missing validated cognitive result");String reason=clip(result.reason,500);
        if(result.disposition==Disposition.IGNORE){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.IGNORED_NOISE,cognitiveRunId,reason.isEmpty()?"semantic ignore":reason,0,result.confidence,"IGNORE");return ApplyResult.simple(CognitiveSignalV2.CognitiveState.IGNORED_NOISE,reason);}
        if(result.disposition==Disposition.CONTEXT){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.CONTEXT_ONLY,cognitiveRunId,reason.isEmpty()?"useful context without durable intelligence":reason,0,result.confidence,"CONTEXT");return ApplyResult.simple(CognitiveSignalV2.CognitiveState.CONTEXT_ONLY,reason);}
        if(result.disposition==Disposition.REVIEW){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.REVIEW_REQUIRED,cognitiveRunId,reason.isEmpty()?"model requested review":reason,0,result.confidence,"REVIEW");return ApplyResult.simple(CognitiveSignalV2.CognitiveState.REVIEW_REQUIRED,reason);}
        if(result.items.isEmpty())return ApplyResult.failed("DERIVE result contained no validated items");
        long primaryMemoryId=0;int count=0,maxPriority=0;String firstKind="MEMORY";
        for(CognitiveItem item:result.items){Persisted p=persistItem(db,snapshot,family,item,result,cognitiveRunId,provider,model,latency);if(!p.success)return ApplyResult.failed(p.detail);if(primaryMemoryId<=0)primaryMemoryId=p.memoryId;if(count==0)firstKind=item.kind.name();count++;maxPriority=Math.max(maxPriority,p.priorityScore);}
        if(primaryMemoryId<=0||count==0)return ApplyResult.failed("no grounded durable item materialized");
        ContentValues raw=new ContentValues();raw.put("state","promoted");raw.put("promoted_item_id",primaryMemoryId);raw.put("retention_until",0);raw.put("disposition",legacyDisposition(firstKind));raw.put("importance",maxPriority);raw.put("confidence",result.confidence);raw.put("policy_version",POLICY);raw.put("filter_engine","cognitive_adjudicator_v2");raw.put("reason",reason);raw.put("cognitive_state",CognitiveSignalV2.CognitiveState.DERIVED.name());raw.put("cognitive_run_id",cognitiveRunId);raw.put("final_reason",reason);raw.put("updated_at",System.currentTimeMillis());if(db.getWritableDatabase().update("raw_signals",raw,"id=?",new String[]{String.valueOf(snapshot.id)})<=0)return ApplyResult.failed("raw signal final cognitive transition failed");
        return new ApplyResult(true,CognitiveSignalV2.CognitiveState.DERIVED,count,primaryMemoryId,maxPriority,reason.isEmpty()?"validated derived intelligence persisted":reason);
    }

    private static Persisted persistItem(VaultDb db,SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,CognitiveItem item,CognitiveResult result,long runId,String provider,String model,long latency){
        try{long now=System.currentTimeMillis();int priority=CognitiveSignalV2.priorityScore(item.importance,item.urgency,item.kind,item.requiresUserAction,item.requiresFollowUp,item.requiresContentExtraction,item.dueAt,snapshot.signal.occurredAt,50,family==CognitiveSignalV2.SignalFamily.SECURITY&&item.importance>=80,now);
            JSONObject meta=new JSONObject().put("policy_version",POLICY).put("raw_signal_id",snapshot.id).put("source",snapshot.signal.source).put("signal_family",family.name()).put("cognitive_run_id",runId).put("brain_provider",provider).put("brain_model",model).put("brain_latency_ms",latency).put("brain_confidence",result.confidence).put("kind",item.kind.name()).put("importance",priority).put("model_importance",item.importance).put("urgency",item.urgency).put("priority_score",priority).put("person",item.person.isEmpty()?JSONObject.NULL:item.person).put("due_at",item.dueAt>0?item.dueAt:JSONObject.NULL).put("requires_user_action",item.requiresUserAction).put("requires_follow_up",item.requiresFollowUp).put("requires_content_extraction",item.requiresContentExtraction).put("reason",result.reason);if(snapshot.threadId>0)meta.put("thread_id",snapshot.threadId);
            String semantic=Fingerprint.text("cognitive-v2|"+snapshot.id+"|"+item.kind.name()+"|"+item.summary);long derivedId=CognitiveStore.addDerived(db,item.kind.name(),item.summary,item.summary,"open",result.confidence,item.importance,semantic,meta.toString());if(derivedId<=0)return Persisted.failed("derived item persistence failed");
            ContentValues d=new ContentValues();d.put("urgency",item.urgency);d.put("person_key",item.person);d.put("due_at",item.dueAt);d.put("requires_user_action",item.requiresUserAction?1:0);d.put("requires_follow_up",item.requiresFollowUp?1:0);d.put("requires_content_extraction",item.requiresContentExtraction?1:0);d.put("cognitive_run_id",runId);d.put("priority_score",priority);d.put("updated_at",now);if(db.getWritableDatabase().update("derived_items",d,"id=?",new String[]{String.valueOf(derivedId)})<=0)return Persisted.failed("typed derived intelligence update failed");
            if(!CognitiveStore.setDerivedRoutingChecked(db,derivedId,snapshot.signal.source,snapshot.threadId,snapshot.id,item.kind.name(),semantic))return Persisted.failed("derived routing persistence failed");if(!CognitiveStore.linkChecked(db,"raw_signal",snapshot.id,"derived",derivedId,"supports",1.0,"{\"cognitive_run_id\":"+runId+"}"))return Persisted.failed("derived provenance link failed");
            String tags="signal,notification,cognitive_v2,"+item.kind.name().toLowerCase(Locale.ROOT)+",priority_"+priority;long inserted=db.insert("NOTIFICATION",snapshot.signal.source,item.summary,item.summary,category(item.kind),tags,"",Fingerprint.text("cognitive-v2-memory|"+semantic),meta.toString());long memoryId=inserted<0?-inserted:inserted;if(memoryId<=0)return Persisted.failed("knowledge item persistence failed");CognitiveStore.link(db,"raw_signal",snapshot.id,"memory",memoryId,"promoted_to",1.0,"{\"policy\":\""+POLICY+"\"}");CognitiveStore.link(db,"derived",derivedId,"memory",memoryId,"grounded_by",1.0,"");if(snapshot.threadId>0){CognitiveStore.link(db,"memory",memoryId,"thread",snapshot.threadId,"from_thread",1.0,"");CognitiveStore.link(db,"derived",derivedId,"thread",snapshot.threadId,"derived_from_thread",1.0,"");}return new Persisted(true,memoryId,priority,"");
        }catch(Throwable e){return Persisted.failed(e.getClass().getSimpleName()+": "+n(e.getMessage()));}
    }

    private static ParseResult parse(String raw,String latest,String recent,long occurredAt){
        String json=extractJson(raw);if(json==null)return ParseResult.invalid("INVALID_JSON","no complete JSON object");
        try{JSONObject root=new JSONObject(json);Disposition disposition;try{disposition=Disposition.valueOf(n(root.optString("disposition","")).toUpperCase(Locale.ROOT));}catch(Throwable e){return ParseResult.invalid("INVALID_DISPOSITION","unsupported disposition");}
            double confidence=boundedConfidence(root.opt("confidence"));if(confidence<0)return ParseResult.invalid("INVALID_CONFIDENCE","root confidence must be 0..1 or 0..100");
            JSONArray rawItems=root.optJSONArray("items");if(rawItems==null)rawItems=new JSONArray();if(rawItems.length()>MAX_ITEMS)return ParseResult.invalid("INVALID_ITEMS","more than 5 items");ArrayList<CognitiveItem> items=new ArrayList<>();
            for(int i=0;i<rawItems.length();i++){JSONObject x=rawItems.optJSONObject(i);if(x==null)return ParseResult.invalid("INVALID_ITEM","item is not an object");CognitiveSignalV2.Kind kind;try{kind=CognitiveSignalV2.Kind.valueOf(n(x.optString("kind","")).toUpperCase(Locale.ROOT));}catch(Throwable e){return ParseResult.invalid("INVALID_KIND","unsupported item kind");}
                String summary=n(x.optString("summary",""));if(summary.isEmpty()||summary.length()>240)return ParseResult.invalid("INVALID_SUMMARY","summary must be 1..240 chars");int importance=boundedInt(x,"importance",0,100,40),urgency=boundedInt(x,"urgency",0,100,30);String person=x.isNull("person")?"":clip(x.optString("person",""),120);long dueAt=parseDueAt(x.opt("due_at"));if(dueAt>0&&!plausibleDueAt(dueAt,occurredAt))dueAt=0;if(dueAt>0&&!hasTimeCue(latest+"\n"+recent))dueAt=0;
                boolean userAction=x.optBoolean("requires_user_action",false),follow=x.optBoolean("requires_follow_up",false),extract=x.optBoolean("requires_content_extraction",false);if(kind==CognitiveSignalV2.Kind.ACTION)userAction=true;if(kind==CognitiveSignalV2.Kind.WAITING)follow=true;items.add(new CognitiveItem(kind,summary,importance,urgency,person,dueAt,userAction,follow,extract));}
            if(disposition==Disposition.DERIVE&&items.isEmpty())return ParseResult.invalid("INVALID_ITEMS","DERIVE requires at least one item");if(disposition!=Disposition.DERIVE&&!items.isEmpty())return ParseResult.invalid("INVALID_ITEMS","only DERIVE may contain items");CognitiveResult result=new CognitiveResult(disposition,confidence,items,clip(root.optString("reason",""),500));return new ParseResult("VALID",result,"");
        }catch(Throwable e){return ParseResult.invalid("INVALID_JSON",e.getClass().getSimpleName());}
    }

    private static String buildPrompt(SignalSnapshot snapshot,CognitiveSignalV2.SignalFamily family,String latest,String recent){
        try{JSONObject o=new JSONObject().put("current_time_ms",System.currentTimeMillis()).put("timezone",TimeZone.getDefault().getID()).put("source_package",snapshot.signal.source).put("signal_family",family.name()).put("sender",sender(snapshot.signal)).put("recent_context",clip(recent,4200)).put("latest_signal",clip(latest,1200)).put("occurred_at",snapshot.signal.occurredAt);
            return "/no_think\nCurrent grounded signal:\n"+o.toString()+"\n\nReturn exactly one JSON object:\n{\"disposition\":\"IGNORE|CONTEXT|DERIVE|REVIEW\",\"confidence\":0.0,\"reason\":\"short reason\",\"items\":[{\"kind\":\"ACTION|WAITING|DECISION|EVENT|CONTENT|MESSAGE|REMINDER|INSIGHT|MEMORY\",\"summary\":\"short useful summary\",\"importance\":0,\"urgency\":0,\"person\":null,\"due_at\":null,\"requires_user_action\":false,\"requires_follow_up\":false,\"requires_content_extraction\":false}]}";
        }catch(Throwable e){return"/no_think\nReturn {\"disposition\":\"CONTEXT\",\"confidence\":0.5,\"reason\":\"fallback\",\"items\":[]}";}
    }

    private static String systemPrompt(){return"You are Cortex Cognitive Adjudicator. You are not a chatbot. Convert grounded phone signals into useful personal intelligence only. Tier 0 already removes obvious battery/charging/media/background-service noise. ACTION means the user needs to do something. WAITING means another person/entity is expected to do something. EVENT means a scheduled or time-bound event exists. CONTENT means content was sent/shared and may need extraction such as a voice note, reel, document, image or link. MESSAGE alone should normally remain CONTEXT unless important/actionable/follow-up. Never invent dates, people, tasks, commitments or unseen media contents. If uncertain use REVIEW or CONTEXT. JSON only. /no_think";}

    private static int recordModelRun(VaultDb db,long jobId,int pass,BrainCompletion c,BrainRequest request,ParseResult parsed,String route){return(int)AiJobStore.modelRun(db,jobId,pass,"cognitive_adjudicator",c.provider.toLowerCase(Locale.ROOT),c.model,"signal_cognition_v2:"+route,parsed.valid()?"validated":"invalid",Fingerprint.text(request.userPrompt),c.latencyMs,0,c.tokensGenerated,parsed.valid()?parsed.result.confidence:0,new JSONObjectSafe().put("provider",c.provider).put("model",c.model).put("cache_hit",c.cacheHit).put("tokens_per_second",c.tokensPerSecond).put("raw_hash",Fingerprint.text(c.text)).put("raw_chars",c.text.length()).json(),parsed.error);}

    private static boolean remoteAllowed(CognitiveSignalV2.SignalFamily family,MasterRelevanceFilter.Signal signal){return !MasterRelevanceFilter.sensitiveSignal(signal)&&family!=CognitiveSignalV2.SignalFamily.SECURITY&&family!=CognitiveSignalV2.SignalFamily.TRANSACTION;}
    private static void forceReview(VaultDb db,SignalSnapshot snapshot,long runId,String reason){markOutcome(db,snapshot.id,CognitiveSignalV2.CognitiveState.REVIEW_REQUIRED,runId,reason,45,0.5,"REVIEW");}
    private static void scheduleThermalRetry(Context context,Slot slot){Context app=context.getApplicationContext();SCHEDULER.schedule(()->enqueue(app,slot.threadId,slot.signalId),60,TimeUnit.SECONDS);}

    private static SignalSnapshot load(VaultDb db,long signalId){Cursor c=db.getReadableDatabase().rawQuery("SELECT id,COALESCE(source,''),COALESCE(title,''),COALESCE(body,''),COALESCE(metadata_json,''),occurred_at,thread_id,COALESCE(cognitive_state,'') FROM raw_signals WHERE id=? LIMIT 1",new String[]{String.valueOf(signalId)});try{if(!c.moveToFirst())return null;long id=c.getLong(0),occurred=c.getLong(5),thread=c.getLong(6);String source=n(c.getString(1)),title=n(c.getString(2)),body=n(c.getString(3)),meta=n(c.getString(4));boolean ongoing=false;try{ongoing=new JSONObject(meta).optBoolean("ongoing",false);}catch(Throwable ignored){}return new SignalSnapshot(id,thread,new MasterRelevanceFilter.Signal("notification",source,title,body,meta,occurred,ongoing),n(c.getString(7)));}finally{c.close();}}
    private static String bestLatestText(VaultDb db,SignalSnapshot s){String base=n(s.signal.body).isEmpty()?n(s.signal.title):n(s.signal.body);try{Cursor c=db.getReadableDatabase().rawQuery("SELECT COALESCE(ea.output_text,'') FROM v4_legacy_map m JOIN v4_evidence_analysis ea ON ea.evidence_id=m.object_id WHERE m.legacy_table='raw_signals' AND m.legacy_id=? AND m.object_type='EVIDENCE' AND ea.analysis_kind='CONNECTOR_ENRICHMENT' ORDER BY ea.created_at DESC,ea.id DESC LIMIT 1",new String[]{String.valueOf(s.id)});String e;try{e=c.moveToFirst()?n(c.getString(0)):"";}finally{c.close();}if(!e.isEmpty()&&(base.isEmpty()||e.length()>=base.length()))return e;}catch(Throwable ignored){}return base;}
    private static String sender(MasterRelevanceFilter.Signal s){try{JSONObject o=new JSONObject(n(s.metadataJson));String x=o.optString("person_hint","");if(x.isEmpty())x=o.optString("conversation_title","");if(!x.isEmpty())return clip(x,120);}catch(Throwable ignored){}return clip(s.title,120);}

    private static void markRunning(VaultDb db,long signalId,long runId,CognitiveSignalV2.SignalFamily family,String provider,String model){ContentValues v=new ContentValues();v.put("signal_family",family.name());v.put("cognitive_state",CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION.name());v.put("cognitive_run_id",runId);v.put("final_reason",provider+" "+model+" analyzing");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}
    private static void markPendingReason(VaultDb db,long signalId,String reason){ContentValues v=new ContentValues();v.put("cognitive_state",CognitiveSignalV2.CognitiveState.PENDING_ADJUDICATION.name());v.put("final_reason",reason);v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}
    private static void setFamily(VaultDb db,long signalId,CognitiveSignalV2.SignalFamily family){ContentValues v=new ContentValues();v.put("signal_family",family.name());v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}
    private static void markOutcome(VaultDb db,long signalId,CognitiveSignalV2.CognitiveState state,long runId,String reason,int importance,double confidence,String disposition){ContentValues v=new ContentValues();v.put("cognitive_state",state.name());v.put("cognitive_run_id",Math.max(0,runId));v.put("final_reason",clip(reason,700));v.put("reason",clip(reason,700));v.put("importance",Math.max(0,Math.min(100,importance)));v.put("confidence",Math.max(0,Math.min(1,confidence)));v.put("disposition",disposition);v.put("policy_version",POLICY);v.put("filter_engine","cognitive_adjudicator_v2");v.put("state",state==CognitiveSignalV2.CognitiveState.DERIVED?"promoted":"context_model_checked");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("raw_signals",v,"id=?",new String[]{String.valueOf(signalId)});}

    private static boolean stillCurrent(VaultDb db,Slot slot){if(!isCurrent(slot))return false;if(slot.threadId<=0)return true;Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM raw_signals WHERE thread_id=? ORDER BY occurred_at DESC,id DESC LIMIT 1",new String[]{String.valueOf(slot.threadId)});try{return c.moveToFirst()&&c.getLong(0)==slot.signalId;}finally{c.close();}}
    private static boolean isCurrent(Slot slot){Slot x=slot==null?null:SLOTS.get(slot.key);return x==slot&&x.generation==slot.generation;}
    private static boolean terminal(String s){String x=n(s).toUpperCase(Locale.ROOT);return x.equals("IGNORED_NOISE")||x.equals("CONTEXT_ONLY")||x.equals("DERIVED")||x.equals("REVIEW_REQUIRED")||x.equals("SENSITIVE_BLOCKED")||x.equals("SUPERSEDED");}

    private static long parseDueAt(Object raw){if(raw==null||raw==JSONObject.NULL)return 0;try{if(raw instanceof Number)return Math.max(0,((Number)raw).longValue());String s=n(String.valueOf(raw));if(s.isEmpty())return 0;if(s.matches("\\d{10,13}")){long x=Long.parseLong(s);return s.length()==10?x*1000L:x;}String[] patterns={"yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mmXXX","yyyy-MM-dd HH:mm"};for(String p:patterns){try{SimpleDateFormat f=new SimpleDateFormat(p,Locale.US);f.setLenient(false);Date d=f.parse(s);if(d!=null)return d.getTime();}catch(Throwable ignored){}}}catch(Throwable ignored){}return 0;}
    private static boolean plausibleDueAt(long due,long occurred){long anchor=occurred>0?occurred:System.currentTimeMillis();return due>=anchor-7L*24*60*60*1000&&due<=anchor+730L*24*60*60*1000;}
    private static boolean hasTimeCue(String text){String x=MasterRelevanceFilter.ruleNorm(text);return x.matches(".*\\b\\d{1,2}[:/]\\d{1,2}.*")||x.matches(".*\\b\\d{1,2}\\s*(am|pm).*" )||has(x,"today","tomorrow","tonight","monday","tuesday","wednesday","thursday","friday","saturday","sunday","بكره","بكرة","غدا","غداً","النهارده","اليوم","الليله","الليلة","الساعة","الساعه","موعد");}
    private static double boundedConfidence(Object raw){try{double x=Double.parseDouble(String.valueOf(raw));if(Double.isNaN(x)||Double.isInfinite(x)||x<0||x>100)return-1;if(x>1)x/=100.0;return x;}catch(Throwable e){return-1;}}
    private static int boundedInt(JSONObject o,String key,int min,int max,int fallback){try{if(!o.has(key))return fallback;return Math.max(min,Math.min(max,(int)Math.round(Double.parseDouble(String.valueOf(o.opt(key))))));}catch(Throwable e){return fallback;}}
    private static String extractJson(String s){String x=n(s).replace("```json","").replace("```","").trim();int start=x.indexOf('{');if(start<0)return null;boolean in=false,esc=false;int depth=0;for(int i=start;i<x.length();i++){char c=x.charAt(i);if(in){if(esc){esc=false;continue;}if(c=='\\'){esc=true;continue;}if(c=='\"')in=false;continue;}if(c=='\"'){in=true;continue;}if(c=='{')depth++;else if(c=='}'){depth--;if(depth==0)return x.substring(start,i+1);if(depth<0)return null;}}return null;}
    private static String category(CognitiveSignalV2.Kind k){if(k==CognitiveSignalV2.Kind.ACTION)return"Actions";if(k==CognitiveSignalV2.Kind.WAITING)return"Waiting";if(k==CognitiveSignalV2.Kind.DECISION)return"Decisions";if(k==CognitiveSignalV2.Kind.EVENT||k==CognitiveSignalV2.Kind.REMINDER)return"Events";if(k==CognitiveSignalV2.Kind.CONTENT)return"Content";return"Memory";}
    private static String legacyDisposition(String kind){String x=n(kind).toUpperCase(Locale.ROOT);return x.equals("ACTION")||x.equals("WAITING")||x.equals("DECISION")?x:"MEMORY";}
    private static String clip(String s,int max){String x=n(s).replaceAll("\\s+"," ");return x.length()<=max?x:x.substring(0,max);}
    private static String fmt(double x){return String.format(Locale.US,"%.2f",x);}
    private static boolean has(String s,String...xs){for(String x:xs)if(s.contains(MasterRelevanceFilter.ruleNorm(x)))return true;return false;}
    private static String n(String s){return s==null?"":s.trim();}

    private enum Disposition{IGNORE,CONTEXT,DERIVE,REVIEW}
    static final class ParseResult{final String status,error;final CognitiveResult result;ParseResult(String s,CognitiveResult r,String e){status=s;result=r;error=n(e);}boolean valid(){return result!=null&&"VALID".equals(status);}static ParseResult invalid(String s,String e){return new ParseResult(s,null,e);}}
    static final class CognitiveResult{final Disposition disposition;final double confidence;final List<CognitiveItem> items;final String reason;CognitiveResult(Disposition d,double c,List<CognitiveItem> i,String r){disposition=d;confidence=c;items=i;reason=n(r);}String toJson(){try{JSONObject o=new JSONObject().put("disposition",disposition.name()).put("confidence",confidence).put("reason",reason);JSONArray a=new JSONArray();for(CognitiveItem i:items)a.put(i.toJson());return o.put("items",a).toString();}catch(Throwable e){return"{}";}}}
    static final class CognitiveItem{final CognitiveSignalV2.Kind kind;final String summary,person;final int importance,urgency;final long dueAt;final boolean requiresUserAction,requiresFollowUp,requiresContentExtraction;CognitiveItem(CognitiveSignalV2.Kind k,String s,int imp,int urg,String p,long due,boolean ua,boolean fu,boolean ex){kind=k;summary=s;importance=imp;urgency=urg;person=p;dueAt=due;requiresUserAction=ua;requiresFollowUp=fu;requiresContentExtraction=ex;}JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("kind",kind.name()).put("summary",summary).put("importance",importance).put("urgency",urgency).put("person",person.isEmpty()?JSONObject.NULL:person).put("due_at",dueAt>0?dueAt:JSONObject.NULL).put("requires_user_action",requiresUserAction).put("requires_follow_up",requiresFollowUp).put("requires_content_extraction",requiresContentExtraction);}catch(Throwable ignored){}return o;}}
    private static final class SignalSnapshot{final long id,threadId;final MasterRelevanceFilter.Signal signal;final String cognitiveState;SignalSnapshot(long i,long t,MasterRelevanceFilter.Signal s,String c){id=i;threadId=t;signal=s;cognitiveState=c;}}
    private static final class Slot{final long key,threadId,signalId,generation;volatile ScheduledFuture<?> future;Slot(long k,long t,long s,long g){key=k;threadId=t;signalId=s;generation=g;}}
    private static final class Persisted{final boolean success;final long memoryId;final int priorityScore;final String detail;Persisted(boolean s,long m,int p,String d){success=s;memoryId=m;priorityScore=p;detail=n(d);}static Persisted failed(String d){return new Persisted(false,0,0,d);}}
    private static final class ApplyResult{final boolean success;final CognitiveSignalV2.CognitiveState state;final int derivedCount,maxPriority;final long primaryMemoryId;final String detail;ApplyResult(boolean s,CognitiveSignalV2.CognitiveState st,int c,long m,int p,String d){success=s;state=st;derivedCount=c;primaryMemoryId=m;maxPriority=p;detail=n(d);}static ApplyResult simple(CognitiveSignalV2.CognitiveState st,String d){return new ApplyResult(true,st,0,0,0,d);}static ApplyResult failed(String d){return new ApplyResult(false,CognitiveSignalV2.CognitiveState.MODEL_FAILED,0,0,0,d);}}
    private static final class JSONObjectSafe{final JSONObject o=new JSONObject();JSONObjectSafe put(String k,Object v){try{o.put(k,v);}catch(Throwable ignored){}return this;}String json(){return o.toString();}}
}
