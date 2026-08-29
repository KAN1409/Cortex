package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Cognitive V2 local runner. Shadow remains telemetry-only; canary authority is explicitly guarded. */
public final class CognitiveAdjudicatorV2 {
    public static final String POLICY="cognitive_v2_shadow_001";
    public static final String CANARY_POLICY="cognitive_v2_canary_001";

    private static final long AUTHORITY_QUIET_MS=3000L;
    private static final long SHADOW_QUIET_MS=15_000L;
    private static final long CANARY_TIMEOUT_MS=12_000L;

    private static final ScheduledExecutorService SCHEDULER=Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService AUTHORITY_EXECUTOR=Executors.newCachedThreadPool();
    private static final ExecutorService SHADOW_EXECUTOR=Executors.newCachedThreadPool();
    private static final ConcurrentHashMap<String,Slot> SHADOW_SLOTS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,AuthoritySlot> AUTHORITY_SLOTS=new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION=new AtomicLong();

    private CognitiveAdjudicatorV2(){}

    public interface AuthorityCallback {
        void accepted(CognitiveResult result,long modelRunId);
        void fallback(String reason);
    }

    public static void enqueueShadow(Context context,long signalId,long threadId){
        if(context==null||signalId<=0)return;
        Context app=context.getApplicationContext();
        if(!CognitiveFeatureFlags.shadowEnabled(app))return;
        String key=slotKey(threadId,signalId);
        SHADOW_SLOTS.compute(key,(ignored,previous)->{
            if(previous!=null&&previous.future!=null)previous.future.cancel(false);
            Slot next=new Slot(key,signalId,threadId,GENERATION.incrementAndGet());
            next.future=SCHEDULER.schedule(()->fireShadow(app,next),SHADOW_QUIET_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    /** Compatibility entry point for callers that do not yet carry detailed router provenance. */
    public static void enqueueAuthoritative(Context context,long signalId,long threadId,AuthorityCallback callback){
        enqueueAuthoritative(
                context,signalId,threadId,
                CognitiveAuthorityRouter.RoutingReason.HASH_CANARY.name(),-1,callback
        );
    }

    public static void enqueueAuthoritative(
            Context context,
            long signalId,
            long threadId,
            String routingReason,
            int routingBucket,
            AuthorityCallback callback
    ){
        if(context==null||signalId<=0){safeFallback(callback,"INVALID_INPUT");return;}
        Context app=context.getApplicationContext();
        if(!CognitiveFeatureFlags.authorityCanaryEnabled(app)){safeFallback(callback,"CANARY_DISABLED");return;}
        String key=slotKey(threadId,signalId);
        String routeReason=normalizedRoutingReason(routingReason);
        AUTHORITY_SLOTS.compute(key,(ignored,previous)->{
            if(previous!=null)supersede(previous);
            AuthoritySlot next=new AuthoritySlot(
                    key,signalId,threadId,GENERATION.incrementAndGet(),
                    routeReason,routingBucket,callback
            );
            next.future=SCHEDULER.schedule(()->fireAuthority(app,next),AUTHORITY_QUIET_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    private static void fireShadow(Context app,Slot slot){
        if(isShadowCurrent(slot))SHADOW_EXECUTOR.execute(()->analyzeShadow(app,slot));
    }

    private static void fireAuthority(Context app,AuthoritySlot slot){
        if(isAuthorityCurrent(slot)&&!slot.terminal.get())AUTHORITY_EXECUTOR.execute(()->analyzeAuthority(app,slot));
    }

    private static void analyzeShadow(Context app,Slot slot){
        VaultDb db=new VaultDb(app);long modelRunId=0;
        try{
            if(!isShadowCurrent(slot))return;
            CognitiveInput input=CognitiveInputFactory.load(db,slot.signalId);
            if(input==null){recordSkipped(db,slot,"SIGNAL_MISSING");return;}
            if(input.latestText.isEmpty()){recordSkipped(db,slot,"EMPTY_CONTENT");return;}
            if(SensitiveSignalPolicy.containsSecret(input.latestText)){recordSkipped(db,slot,"SENSITIVE_BLOCKED");return;}
            if(isHardNoise(db,slot.signalId)){recordSkipped(db,slot,"HARD_NOISE");return;}
            if(!LocalModelManager.installed(app)){recordSkipped(db,slot,"MODEL_NOT_READY");return;}

            LocalQwenBrain brain=new LocalQwenBrain(app);
            LocalBrainRun run=brain.classifyWithTelemetry(input,LocalInferenceCoordinator.Priority.SHADOW);
            long latency=run.totalMs;
            if(!isShadowCurrent(slot)){recordSuperseded(db,slot,run,latency);return;}

            LegacyCognitiveSnapshot legacy=LegacyCognitiveSnapshotStore.get(db,slot.signalId);
            JSONObject telemetry=CognitiveShadowComparator.compare(slot.signalId,legacy,run.result);
            telemetry.put("policy",POLICY);
            telemetry.put("generation",slot.generation);
            telemetry.put("signal_family",input.family.name());
            telemetry.put("source_app",clip(input.sourceApp,80));
            putRunTelemetry(telemetry,run);
            telemetry.put("outcome","COMPLETE");

            modelRunId=AiJobStore.modelRun(
                    db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","complete",inputHash(input),latency,0,
                    run.tokensGenerated,run.result.confidence,telemetry.toString(),""
            );
            DiagnosticsLog.info(
                    db,"CognitiveAdjudicatorV2","shadow_complete",
                    telemetry.optString("comparison",""),0,slot.threadId,slot.signalId,0,
                    modelRunId,latency,
                    new JSONObject().put("policy",POLICY)
                            .put("comparison",telemetry.optString("comparison",""))
                            .put("family",input.family.name())
            );
        }catch(Throwable error){
            if(LocalInferenceCoordinator.isBusy(error)){
                recordSkipped(db,slot,"SKIPPED_BUSY");
                return;
            }
            String message=error.getClass().getSimpleName()+(error.getMessage()==null?"":": "+error.getMessage());
            try{
                if(modelRunId<=0){
                    JSONObject out=new JSONObject()
                            .put("schema","cognitive_shadow_001")
                            .put("signal_id",slot.signalId)
                            .put("policy",POLICY)
                            .put("outcome","FAILED")
                            .put("failure_kind",failureKind(error))
                            .put("wire_schema",FastCognitivePromptBuilder.WIRE_SCHEMA);
                    modelRunId=AiJobStore.modelRun(
                            db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                            "cognitive_v2_shadow","failed","",0,0,0,0,
                            out.toString(),clip(message,500)
                    );
                }
                DiagnosticsLog.error(
                        db,"CognitiveAdjudicatorV2","shadow_failed",error,"COGNITIVE_V2_SHADOW",
                        0,slot.threadId,slot.signalId,0,modelRunId,
                        new JSONObject().put("policy",POLICY).put("failure_kind",failureKind(error))
                );
            }catch(Throwable ignored){}
        }finally{
            SHADOW_SLOTS.remove(slot.key,slot);
            try{db.close();}catch(Throwable ignored){}
        }
    }

    private static void analyzeAuthority(Context app,AuthoritySlot slot){
        VaultDb db=new VaultDb(app);LocalBrainRun run=null;long latency=0;
        try{
            if(!isAuthorityCurrent(slot)||slot.terminal.get())return;
            if(!CognitiveFeatureFlags.authorityCanaryEnabled(app)){authorityFallback(db,slot,"CANARY_DISABLED","fallback",null,0,null);return;}

            CognitiveInput input=CognitiveInputFactory.load(db,slot.signalId);
            if(input==null){authorityFallback(db,slot,"SIGNAL_MISSING","fallback",null,0,null);return;}
            if(input.latestText.isEmpty()){authorityFallback(db,slot,"EMPTY_CONTENT","fallback",null,0,input);return;}
            if(SensitiveSignalPolicy.containsSecret(input.latestText)){authorityFallback(db,slot,"SENSITIVE_BLOCKED","fallback",null,0,input);return;}
            if(isHardNoise(db,slot.signalId)){authorityFallback(db,slot,"HARD_NOISE","fallback",null,0,input);return;}
            if(!LocalModelManager.installed(app)){authorityFallback(db,slot,"MODEL_NOT_READY","fallback",null,0,input);return;}
            if(!CognitiveStore.updateRawCognitiveState(
                    db,slot.signalId,"LOCAL_RUNNING",CANARY_POLICY,
                    "V2 canary local inference running"
            )){
                authorityFallback(db,slot,"STATE_TRANSITION_FAILED","failed",null,0,input);
                return;
            }

            slot.started=true;
            slot.inferenceEnqueuedAt=System.currentTimeMillis();
            slot.timeoutFuture=SCHEDULER.schedule(
                    ()->timeoutAuthority(app,slot),
                    CANARY_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS
            );

            LocalQwenBrain brain=new LocalQwenBrain(app);
            run=brain.classifyWithTelemetry(
                    input,
                    LocalInferenceCoordinator.Priority.AUTHORITATIVE,
                    atMs->slot.nativeStartedAt=atMs,
                    ()->slot.terminal.get()||slot.superseded||!isAuthorityCurrent(slot)
            );
            slot.nativeFinishedAt=run.nativeFinishedAt;
            latency=run.totalMs;

            if(slot.terminal.get())return;
            if(slot.superseded||!isAuthorityCurrent(slot)){
                recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,input,null);
                return;
            }
            if(!claimAuthority(slot))return;

            if(!CognitiveFeatureFlags.authorityCanaryEnabled(app)){
                recordAuthorityRun(db,slot,"fallback","CANARY_DISABLED",run,latency,input,null);
                safeFallback(slot.callback,"CANARY_DISABLED");
                return;
            }
            if(!canAcceptAuthoritatively(run.result)){
                String reason=authorityFallbackReason(run.result);
                recordAuthorityRun(db,slot,"fallback",reason,run,latency,input,null);
                safeFallback(slot.callback,reason);
                return;
            }

            try{
                CognitiveStore.CanaryApply applied=CognitiveStore.applyCanaryAuthority(
                        db,slot.signalId,slot.threadId,run.result,run,latency,
                        inputHash(input),CANARY_POLICY,slot.routingReason,slot.routingBucket
                );
                enrichAppliedRunTelemetry(db,applied.modelRunId,run);
                DiagnosticsLog.info(
                        db,"CognitiveAdjudicatorV2","canary_applied",run.result.disposition.name(),
                        0,slot.threadId,slot.signalId,0,applied.modelRunId,latency,
                        new JSONObject().put("policy",CANARY_POLICY)
                                .put("routing_reason",slot.routingReason)
                                .put("routing_bucket",slot.routingBucket)
                                .put("disposition",run.result.disposition.name())
                                .put("derived_count",applied.derivedIds.size())
                                .put("queue_wait_ms",run.queueWaitMs)
                                .put("native_total_ms",run.nativeTotalMs)
                                .put("total_ms",run.totalMs)
                );
                safeAccepted(slot.callback,run.result,applied.modelRunId);
            }catch(Throwable applyError){
                String detail=applyError.getMessage()==null?applyError.getClass().getSimpleName():applyError.getMessage();
                if(detail.contains("CANARY_SUPERSEDED")){
                    CognitiveStore.updateRawCognitiveState(
                            db,slot.signalId,"SUPERSEDED",CANARY_POLICY,
                            "Newer signal superseded V2 canary before apply"
                    );
                    recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,input,applyError);
                    return;
                }
                recordAuthorityRun(db,slot,"failed","APPLY_FAILED",run,latency,input,applyError);
                safeFallback(slot.callback,"APPLY_FAILED");
            }
        }catch(Throwable error){
            if(slot.superseded){
                try{recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,null,error);}catch(Throwable ignored){}
                return;
            }
            if(slot.terminal.get())return;
            if(claimAuthority(slot)){
                try{recordAuthorityRun(db,slot,"failed",failureKind(error),run,latency,null,error);}catch(Throwable ignored){}
                safeFallback(slot.callback,failureKind(error));
            }
        }finally{
            cancel(slot.timeoutFuture);
            AUTHORITY_SLOTS.remove(slot.key,slot);
            try{db.close();}catch(Throwable ignored){}
        }
    }

    private static void timeoutAuthority(Context app,AuthoritySlot slot){
        if(slot==null||!isAuthorityCurrent(slot)||!slot.terminal.compareAndSet(false,true))return;
        slot.timedOut=true;
        AUTHORITY_SLOTS.remove(slot.key,slot);

        long now=System.currentTimeMillis();
        long elapsed=slot.inferenceEnqueuedAt>0
                ? Math.max(0L,now-slot.inferenceEnqueuedAt)
                : CANARY_TIMEOUT_MS;
        String reason=slot.nativeStartedAt>0?"INFERENCE_TIMEOUT":"QUEUE_TIMEOUT";
        String code=slot.nativeStartedAt>0
                ?"COGNITIVE_V2_CANARY_INFERENCE_TIMEOUT"
                :"COGNITIVE_V2_CANARY_QUEUE_TIMEOUT";

        VaultDb db=new VaultDb(app);
        try{
            recordAuthorityRun(db,slot,"timeout",reason,null,elapsed,null,null);
            DiagnosticsLog.warn(
                    db,"CognitiveAdjudicatorV2","canary_timeout","legacy_fallback",code,
                    0,slot.threadId,slot.signalId,0,0,
                    new JSONObject().put("reason",reason)
                            .put("routing_reason",slot.routingReason)
                            .put("routing_bucket",slot.routingBucket)
                            .put("queue_wait_ms",slot.nativeStartedAt>0
                                    ? Math.max(0L,slot.nativeStartedAt-slot.inferenceEnqueuedAt)
                                    : elapsed)
                            .put("total_ms",elapsed)
            );
        }catch(Throwable ignored){
        }finally{
            try{db.close();}catch(Throwable ignored){}
        }
        safeFallback(slot.callback,reason);
    }

    private static boolean canAcceptAuthoritatively(CognitiveResult result){
        if(result==null||result.disposition==null)return false;
        switch(result.disposition){
            case DERIVE:return result.confidence>=0.80&&result.items!=null&&!result.items.isEmpty();
            case CONTEXT:return result.confidence>=0.85;
            case IGNORE:
            case REVIEW:
            default:return false;
        }
    }

    private static String authorityFallbackReason(CognitiveResult result){
        if(result==null||result.disposition==null)return"INVALID_RESULT";
        switch(result.disposition){
            case IGNORE:return"AI_IGNORE_NOT_AUTHORITATIVE";
            case REVIEW:return"AI_REVIEW_NOT_AUTHORITATIVE";
            case DERIVE:return result.items==null||result.items.isEmpty()?"EMPTY_DERIVE":"LOW_CONFIDENCE_DERIVE";
            case CONTEXT:return"LOW_CONFIDENCE_CONTEXT";
            default:return"UNSUPPORTED_DISPOSITION";
        }
    }

    private static void authorityFallback(
            VaultDb db,AuthoritySlot slot,String reason,String state,
            LocalBrainRun run,long latency,CognitiveInput input
    ){
        if(!claimAuthority(slot))return;
        recordAuthorityRun(db,slot,state,reason,run,latency,input,null);
        safeFallback(slot.callback,reason);
    }

    private static boolean claimAuthority(AuthoritySlot slot){
        if(slot==null||!isAuthorityCurrent(slot)||!slot.terminal.compareAndSet(false,true))return false;
        cancel(slot.timeoutFuture);
        AUTHORITY_SLOTS.remove(slot.key,slot);
        return true;
    }

    private static void supersede(AuthoritySlot slot){
        if(slot==null)return;
        slot.superseded=true;
        cancel(slot.future);
        cancel(slot.timeoutFuture);
        if(slot.terminal.compareAndSet(false,true))safeFallback(slot.callback,"SUPERSEDED");
    }

    private static long recordAuthorityRun(
            VaultDb db,AuthoritySlot slot,String state,String reason,
            LocalBrainRun run,long latency,CognitiveInput input,Throwable error
    ){
        try{
            JSONObject output=new JSONObject()
                    .put("schema","cognitive_canary_001")
                    .put("signal_id",slot.signalId)
                    .put("policy",CANARY_POLICY)
                    .put("routing_reason",slot.routingReason)
                    .put("routing_bucket",slot.routingBucket)
                    .put("outcome",n(state).toUpperCase())
                    .put("reason",n(reason))
                    .put("generation",slot.generation)
                    .put("wire_schema",FastCognitivePromptBuilder.WIRE_SCHEMA);

            double confidence=0;
            int tokens=0;
            if(run!=null&&run.result!=null){
                confidence=run.result.confidence;
                tokens=run.tokensGenerated;
                output.put("disposition",run.result.disposition==null?"":run.result.disposition.name());
                output.put("confidence",run.result.confidence);
                JSONArray items=new JSONArray();
                for(CognitiveItem item:run.result.items){
                    if(item==null||item.kind==null)continue;
                    items.put(new JSONObject()
                            .put("kind",item.kind.name())
                            .put("summary",clip(item.summary,240))
                            .put("importance",item.importance)
                            .put("urgency",item.urgency));
                }
                output.put("items",items);
                putRunTelemetry(output,run);
            }else{
                putSlotTelemetry(output,slot,latency);
            }

            String hash=input==null?"":inputHash(input);
            String err=error==null?"":clip(
                    error.getClass().getSimpleName()
                            +(error.getMessage()==null?"":": "+error.getMessage()),
                    500
            );
            long runId=AiJobStore.modelRun(
                    db,0,1,"cognitive_authority","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_canary",state,hash,Math.max(0,latency),0,tokens,
                    confidence,output.toString(),err
            );
            if(runId>0){
                JSONObject metadata=new JSONObject()
                        .put("policy",CANARY_POLICY)
                        .put("routing_reason",slot.routingReason)
                        .put("routing_bucket",slot.routingBucket);
                CognitiveStore.link(
                        db,"model_run",runId,"raw_signal",slot.signalId,
                        "canary_evaluated",confidence,metadata.toString()
                );
            }
            DiagnosticsLog.info(
                    db,"CognitiveAdjudicatorV2","canary_terminal",reason,
                    0,slot.threadId,slot.signalId,0,runId,latency,
                    new JSONObject().put("policy",CANARY_POLICY)
                            .put("routing_reason",slot.routingReason)
                            .put("routing_bucket",slot.routingBucket)
                            .put("state",state).put("reason",reason)
            );
            return runId;
        }catch(Throwable ignored){
            return 0;
        }
    }

    private static void enrichAppliedRunTelemetry(VaultDb db,long modelRunId,LocalBrainRun run){
        if(db==null||modelRunId<=0||run==null)return;
        Cursor cursor=db.getReadableDatabase().query(
                "model_runs",new String[]{"output_json"},"id=?",
                new String[]{String.valueOf(modelRunId)},null,null,null,"1"
        );
        String raw=cursor.moveToFirst()&&!cursor.isNull(0)?cursor.getString(0):"";
        cursor.close();
        try{
            JSONObject output=raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
            putRunTelemetry(output,run);
            ContentValues values=new ContentValues();
            values.put("output_json",output.toString());
            db.getWritableDatabase().update(
                    "model_runs",values,"id=?",
                    new String[]{String.valueOf(modelRunId)}
            );
        }catch(Throwable ignored){}
    }

    private static void putRunTelemetry(JSONObject output,LocalBrainRun run){
        if(output==null||run==null)return;
        try{
            output.put("queue_wait_ms",run.queueWaitMs);
            output.put("native_total_ms",run.nativeTotalMs);
            output.put("total_ms",run.totalMs);
            output.put("prompt_chars",run.promptChars);
            output.put("tokens_generated",run.tokensGenerated);
            output.put("tokens_per_second",run.tokensPerSecond);
            output.put("cache_hit",run.cacheHit);
            output.put("wire_schema",run.wireSchema);
            output.put("enqueued_at",run.enqueuedAt);
            output.put("native_started_at",run.nativeStartedAt);
            output.put("native_finished_at",run.nativeFinishedAt);
            output.put("generation_ms",run.generationMs);
            output.put("model_load_ms",run.modelLoadMs);
            output.put("duration_ms",run.durationMs);
        }catch(Throwable ignored){}
    }

    private static void putSlotTelemetry(JSONObject output,AuthoritySlot slot,long totalMs){
        if(output==null||slot==null)return;
        try{
            long now=System.currentTimeMillis();
            long enqueued=slot.inferenceEnqueuedAt;
            long started=slot.nativeStartedAt;
            long finished=slot.nativeFinishedAt;
            long queueWait=enqueued>0
                    ? (started>0?Math.max(0L,started-enqueued):Math.max(0L,now-enqueued))
                    : 0L;
            long nativeTotal=started>0
                    ? Math.max(0L,(finished>0?finished:now)-started)
                    : 0L;
            output.put("queue_wait_ms",queueWait);
            output.put("native_total_ms",nativeTotal);
            output.put("total_ms",Math.max(0L,totalMs));
            output.put("prompt_chars",0);
            output.put("tokens_generated",0);
            output.put("tokens_per_second",0);
            output.put("cache_hit",false);
            output.put("wire_schema",FastCognitivePromptBuilder.WIRE_SCHEMA);
            output.put("enqueued_at",enqueued);
            output.put("native_started_at",started);
            output.put("native_finished_at",finished);
        }catch(Throwable ignored){}
    }

    private static boolean isHardNoise(VaultDb db,long signalId){
        Cursor c=db.getReadableDatabase().query(
                "raw_signals",new String[]{"disposition","filter_engine"},"id=?",
                new String[]{String.valueOf(signalId)},null,null,null,"1"
        );
        boolean noise=false;
        if(c.moveToFirst()){
            noise="IGNORE".equalsIgnoreCase(c.getString(0))
                    &&"deterministic_fast_gate".equalsIgnoreCase(c.getString(1));
        }
        c.close();
        return noise;
    }

    private static void recordSkipped(VaultDb db,Slot slot,String reason){
        try{
            JSONObject output=new JSONObject()
                    .put("schema","cognitive_shadow_001")
                    .put("signal_id",slot.signalId)
                    .put("policy",POLICY)
                    .put("outcome","SKIPPED")
                    .put("reason",reason)
                    .put("wire_schema",FastCognitivePromptBuilder.WIRE_SCHEMA);
            long runId=AiJobStore.modelRun(
                    db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","skipped","",0,0,0,0,output.toString(),""
            );
            DiagnosticsLog.info(
                    db,"CognitiveAdjudicatorV2","shadow_skipped",reason,
                    0,slot.threadId,slot.signalId,0,runId,0,
                    new JSONObject().put("policy",POLICY).put("reason",reason)
            );
        }catch(Throwable ignored){}
    }

    private static void recordSuperseded(VaultDb db,Slot slot,LocalBrainRun run,long latency){
        try{
            JSONObject output=new JSONObject()
                    .put("schema","cognitive_shadow_001")
                    .put("signal_id",slot.signalId)
                    .put("policy",POLICY)
                    .put("outcome","SUPERSEDED")
                    .put("generation",slot.generation);
            putRunTelemetry(output,run);
            long runId=AiJobStore.modelRun(
                    db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","superseded","",latency,0,
                    run.tokensGenerated,run.result.confidence,output.toString(),""
            );
            DiagnosticsLog.info(
                    db,"CognitiveAdjudicatorV2","shadow_superseded","safe",
                    0,slot.threadId,slot.signalId,0,runId,latency,
                    new JSONObject().put("policy",POLICY).put("generation",slot.generation)
            );
        }catch(Throwable ignored){}
    }

    private static String inputHash(CognitiveInput input){
        StringBuilder s=new StringBuilder()
                .append(input.signalId).append('|')
                .append(input.occurredAt).append('|')
                .append(input.family.name()).append('|')
                .append(input.sourcePackage).append('|')
                .append(input.latestText);
        for(CognitiveMessage m:input.recentContext){
            s.append('|').append(m.occurredAt)
                    .append('|').append(m.direction)
                    .append('|').append(m.sender)
                    .append('|').append(m.sensitiveRedacted?"<redacted>":m.text);
        }
        return Fingerprint.text(s.toString());
    }

    private static String failureKind(Throwable error){
        Throwable x=error;
        while(x!=null){
            if(x instanceof CognitiveContractException)return"INVALID_CONTRACT";
            x=x.getCause();
        }
        String message=error==null||error.getMessage()==null?"":error.getMessage();
        return message.contains("invalid cognitive output")?"INVALID_CONTRACT":"INFERENCE_FAILED";
    }

    private static boolean isShadowCurrent(Slot slot){
        Slot current=slot==null?null:SHADOW_SLOTS.get(slot.key);
        return current==slot&&current!=null&&current.generation==slot.generation;
    }

    private static boolean isAuthorityCurrent(AuthoritySlot slot){
        AuthoritySlot current=slot==null?null:AUTHORITY_SLOTS.get(slot.key);
        return current==slot&&current!=null&&current.generation==slot.generation;
    }

    private static String slotKey(long threadId,long signalId){
        return threadId>0?"thread:"+threadId:"signal:"+signalId;
    }

    private static void cancel(ScheduledFuture<?> future){
        if(future!=null)future.cancel(false);
    }

    private static void safeAccepted(AuthorityCallback callback,CognitiveResult result,long modelRunId){
        if(callback!=null)try{callback.accepted(result,modelRunId);}catch(Throwable ignored){}
    }

    private static void safeFallback(AuthorityCallback callback,String reason){
        if(callback!=null)try{callback.fallback(reason);}catch(Throwable ignored){}
    }

    private static String normalizedRoutingReason(String value){
        String clean=n(value).trim();
        return clean.isEmpty()?CognitiveAuthorityRouter.RoutingReason.HASH_CANARY.name():clean;
    }

    private static String clip(String value,int max){
        String clean=value==null?"":value.trim();
        return clean.length()<=max?clean:clean.substring(0,max);
    }

    private static String n(String value){
        return value==null?"":value;
    }

    private static final class Slot{
        final String key;
        final long signalId,threadId,generation;
        volatile ScheduledFuture<?> future;
        Slot(String key,long signalId,long threadId,long generation){
            this.key=key;this.signalId=signalId;this.threadId=threadId;this.generation=generation;
        }
    }

    private static final class AuthoritySlot{
        final String key;
        final long signalId,threadId,generation;
        final String routingReason;
        final int routingBucket;
        final AuthorityCallback callback;
        final AtomicBoolean terminal=new AtomicBoolean(false);
        volatile ScheduledFuture<?> future,timeoutFuture;
        volatile boolean started,superseded,timedOut;
        volatile long inferenceEnqueuedAt,nativeStartedAt,nativeFinishedAt;

        AuthoritySlot(
                String key,
                long signalId,
                long threadId,
                long generation,
                String routingReason,
                int routingBucket,
                AuthorityCallback callback
        ){
            this.key=key;
            this.signalId=signalId;
            this.threadId=threadId;
            this.generation=generation;
            this.routingReason=normalizedRoutingReason(routingReason);
            this.routingBucket=routingBucket;
            this.callback=callback;
        }
    }
}
