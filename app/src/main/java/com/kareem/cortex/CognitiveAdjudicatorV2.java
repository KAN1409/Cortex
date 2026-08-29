package com.kareem.cortex;

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
    private static final long QUIET_MS=3000L;
    private static final long CANARY_TIMEOUT_MS=4000L;
    private static final ScheduledExecutorService SCHEDULER=Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService MODEL_EXECUTOR=Executors.newSingleThreadExecutor();
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
            next.future=SCHEDULER.schedule(()->fireShadow(app,next),QUIET_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    public static void enqueueAuthoritative(Context context,long signalId,long threadId,AuthorityCallback callback){
        if(context==null||signalId<=0){safeFallback(callback,"INVALID_INPUT");return;}
        Context app=context.getApplicationContext();
        if(!CognitiveFeatureFlags.authorityCanaryEnabled(app)){safeFallback(callback,"CANARY_DISABLED");return;}
        String key=slotKey(threadId,signalId);
        AUTHORITY_SLOTS.compute(key,(ignored,previous)->{
            if(previous!=null)supersede(previous);
            AuthoritySlot next=new AuthoritySlot(key,signalId,threadId,GENERATION.incrementAndGet(),callback);
            next.future=SCHEDULER.schedule(()->fireAuthority(app,next),QUIET_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    private static void fireShadow(Context app,Slot slot){if(isShadowCurrent(slot))MODEL_EXECUTOR.execute(()->analyzeShadow(app,slot));}
    private static void fireAuthority(Context app,AuthoritySlot slot){if(isAuthorityCurrent(slot)&&!slot.terminal.get())MODEL_EXECUTOR.execute(()->analyzeAuthority(app,slot));}

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

            LocalQwenBrain brain=new LocalQwenBrain(app);long started=System.currentTimeMillis();LocalBrainRun run=brain.classifyWithTelemetry(input);long latency=System.currentTimeMillis()-started;
            if(!isShadowCurrent(slot)){recordSuperseded(db,slot,run,latency);return;}

            LegacyCognitiveSnapshot legacy=LegacyCognitiveSnapshotStore.get(db,slot.signalId);
            JSONObject telemetry=CognitiveShadowComparator.compare(slot.signalId,legacy,run.result);
            telemetry.put("policy",POLICY);telemetry.put("generation",slot.generation);telemetry.put("signal_family",input.family.name());telemetry.put("source_app",clip(input.sourceApp,80));telemetry.put("tokens_per_second",run.tokensPerSecond);telemetry.put("tokens_generated",run.tokensGenerated);telemetry.put("generation_ms",run.generationMs);telemetry.put("model_load_ms",run.modelLoadMs);telemetry.put("duration_ms",run.durationMs);telemetry.put("cache_hit",run.cacheHit);telemetry.put("outcome","COMPLETE");

            modelRunId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,"cognitive_v2_shadow","complete",inputHash(input),latency,0,run.tokensGenerated,run.result.confidence,telemetry.toString(),"");
            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_complete",telemetry.optString("comparison",""),0,slot.threadId,slot.signalId,0,modelRunId,latency,new JSONObject().put("policy",POLICY).put("comparison",telemetry.optString("comparison","")).put("family",input.family.name()));
        }catch(Throwable e){
            String error=e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage());
            try{
                if(modelRunId<=0){JSONObject out=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId).put("policy",POLICY).put("outcome","FAILED").put("failure_kind",failureKind(e));modelRunId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,"cognitive_v2_shadow","failed","",0,0,0,0,out.toString(),clip(error,500));}
                DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","shadow_failed",e,"COGNITIVE_V2_SHADOW",0,slot.threadId,slot.signalId,0,modelRunId,new JSONObject().put("policy",POLICY).put("failure_kind",failureKind(e)));
            }catch(Throwable ignored){}
        }finally{SHADOW_SLOTS.remove(slot.key,slot);try{db.close();}catch(Throwable ignored){}}
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
            if(!CognitiveStore.updateRawCognitiveState(db,slot.signalId,"LOCAL_RUNNING",CANARY_POLICY,"V2 canary local inference running")){authorityFallback(db,slot,"STATE_TRANSITION_FAILED","failed",null,0,input);return;}

            slot.started=true;slot.timeoutFuture=SCHEDULER.schedule(()->timeoutAuthority(app,slot),CANARY_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            LocalQwenBrain brain=new LocalQwenBrain(app);long started=System.currentTimeMillis();run=brain.classifyWithTelemetry(input);latency=System.currentTimeMillis()-started;

            if(slot.superseded||!isAuthorityCurrent(slot)){
                recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,input,null);
                return;
            }
            if(slot.terminal.get())return;
            if(!claimAuthority(slot))return;

            if(!CognitiveFeatureFlags.authorityCanaryEnabled(app)){
                recordAuthorityRun(db,slot,"fallback","CANARY_DISABLED",run,latency,input,null);safeFallback(slot.callback,"CANARY_DISABLED");return;
            }
            if(!canAcceptAuthoritatively(run.result)){
                String reason=authorityFallbackReason(run.result);recordAuthorityRun(db,slot,"fallback",reason,run,latency,input,null);safeFallback(slot.callback,reason);return;
            }

            try{
                CognitiveStore.CanaryApply applied=CognitiveStore.applyCanaryAuthority(db,slot.signalId,slot.threadId,run.result,run,latency,inputHash(input),CANARY_POLICY);
                DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","canary_applied",run.result.disposition.name(),0,slot.threadId,slot.signalId,0,applied.modelRunId,latency,new JSONObject().put("policy",CANARY_POLICY).put("disposition",run.result.disposition.name()).put("derived_count",applied.derivedIds.size()));
                safeAccepted(slot.callback,run.result,applied.modelRunId);
            }catch(Throwable applyError){
                String detail=applyError.getMessage()==null?applyError.getClass().getSimpleName():applyError.getMessage();
                if(detail.contains("CANARY_SUPERSEDED")){
                    CognitiveStore.updateRawCognitiveState(db,slot.signalId,"SUPERSEDED",CANARY_POLICY,"Newer signal superseded V2 canary before apply");
                    recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,input,applyError);return;
                }
                recordAuthorityRun(db,slot,"failed","APPLY_FAILED",run,latency,input,applyError);safeFallback(slot.callback,"APPLY_FAILED");
            }
        }catch(Throwable e){
            if(slot.superseded){try{recordAuthorityRun(db,slot,"superseded","SUPERSEDED",run,latency,null,e);}catch(Throwable ignored){}return;}
            if(slot.terminal.get())return;
            if(claimAuthority(slot)){
                try{recordAuthorityRun(db,slot,"failed",failureKind(e),run,latency,null,e);}catch(Throwable ignored){}
                safeFallback(slot.callback,failureKind(e));
            }
        }finally{
            cancel(slot.timeoutFuture);AUTHORITY_SLOTS.remove(slot.key,slot);try{db.close();}catch(Throwable ignored){}
        }
    }

    private static void timeoutAuthority(Context app,AuthoritySlot slot){
        if(slot==null||!isAuthorityCurrent(slot)||!slot.terminal.compareAndSet(false,true))return;
        slot.timedOut=true;AUTHORITY_SLOTS.remove(slot.key,slot);
        VaultDb db=new VaultDb(app);try{recordAuthorityRun(db,slot,"timeout","TIMEOUT",null,CANARY_TIMEOUT_MS,null,null);DiagnosticsLog.warn(db,"CognitiveAdjudicatorV2","canary_timeout","legacy_fallback","COGNITIVE_V2_CANARY_TIMEOUT",0,slot.threadId,slot.signalId,0,0,null);}catch(Throwable ignored){}finally{try{db.close();}catch(Throwable ignored){}}
        safeFallback(slot.callback,"TIMEOUT");
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

    private static void authorityFallback(VaultDb db,AuthoritySlot slot,String reason,String state,LocalBrainRun run,long latency,CognitiveInput input){
        if(!claimAuthority(slot))return;recordAuthorityRun(db,slot,state,reason,run,latency,input,null);safeFallback(slot.callback,reason);
    }

    private static boolean claimAuthority(AuthoritySlot slot){
        if(slot==null||!isAuthorityCurrent(slot)||!slot.terminal.compareAndSet(false,true))return false;
        cancel(slot.timeoutFuture);AUTHORITY_SLOTS.remove(slot.key,slot);return true;
    }

    private static void supersede(AuthoritySlot slot){
        if(slot==null)return;slot.superseded=true;cancel(slot.future);cancel(slot.timeoutFuture);
        if(slot.terminal.compareAndSet(false,true))safeFallback(slot.callback,"SUPERSEDED");
    }

    private static long recordAuthorityRun(VaultDb db,AuthoritySlot slot,String state,String reason,LocalBrainRun run,long latency,CognitiveInput input,Throwable error){
        try{
            JSONObject output=new JSONObject().put("schema","cognitive_canary_001").put("signal_id",slot.signalId).put("policy",CANARY_POLICY).put("outcome",n(state).toUpperCase()).put("reason",n(reason)).put("generation",slot.generation);
            double confidence=0;int tokens=0;if(run!=null&&run.result!=null){confidence=run.result.confidence;tokens=run.tokensGenerated;output.put("disposition",run.result.disposition==null?"":run.result.disposition.name());output.put("confidence",run.result.confidence);JSONArray items=new JSONArray();for(CognitiveItem item:run.result.items){if(item==null||item.kind==null)continue;items.put(new JSONObject().put("kind",item.kind.name()).put("summary",clip(item.summary,240)).put("importance",item.importance).put("urgency",item.urgency));}output.put("items",items);output.put("tokens_per_second",run.tokensPerSecond);output.put("generation_ms",run.generationMs);output.put("model_load_ms",run.modelLoadMs);output.put("cache_hit",run.cacheHit);}
            String hash=input==null?"":inputHash(input);String err=error==null?"":clip(error.getClass().getSimpleName()+(error.getMessage()==null?"":": "+error.getMessage()),500);
            long runId=AiJobStore.modelRun(db,0,1,"cognitive_authority","local",LocalModelManager.MODEL_NAME,"cognitive_v2_canary",state,hash,Math.max(0,latency),0,tokens,confidence,output.toString(),err);
            if(runId>0)CognitiveStore.link(db,"model_run",runId,"raw_signal",slot.signalId,"canary_evaluated",confidence,"{\"policy\":\""+CANARY_POLICY+"\"}");
            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","canary_terminal",reason,0,slot.threadId,slot.signalId,0,runId,latency,new JSONObject().put("policy",CANARY_POLICY).put("state",state).put("reason",reason));return runId;
        }catch(Throwable ignored){return 0;}
    }

    private static boolean isHardNoise(VaultDb db,long signalId){
        Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"disposition","filter_engine"},"id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");boolean noise=false;if(c.moveToFirst())noise="IGNORE".equalsIgnoreCase(c.getString(0))&&"deterministic_fast_gate".equalsIgnoreCase(c.getString(1));c.close();return noise;
    }

    private static void recordSkipped(VaultDb db,Slot slot,String reason){
        try{JSONObject output=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId).put("policy",POLICY).put("outcome","SKIPPED").put("reason",reason);long runId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,"cognitive_v2_shadow","skipped","",0,0,0,0,output.toString(),"");DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_skipped",reason,0,slot.threadId,slot.signalId,0,runId,0,new JSONObject().put("policy",POLICY).put("reason",reason));}catch(Throwable ignored){}
    }

    private static void recordSuperseded(VaultDb db,Slot slot,LocalBrainRun run,long latency){
        try{JSONObject output=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId).put("policy",POLICY).put("outcome","SUPERSEDED").put("generation",slot.generation);long runId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,"cognitive_v2_shadow","superseded","",latency,0,run.tokensGenerated,run.result.confidence,output.toString(),"");DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_superseded","safe",0,slot.threadId,slot.signalId,0,runId,latency,new JSONObject().put("policy",POLICY).put("generation",slot.generation));}catch(Throwable ignored){}
    }

    private static String inputHash(CognitiveInput input){StringBuilder s=new StringBuilder().append(input.signalId).append('|').append(input.occurredAt).append('|').append(input.family.name()).append('|').append(input.sourcePackage).append('|').append(input.latestText);for(CognitiveMessage m:input.recentContext)s.append('|').append(m.occurredAt).append('|').append(m.direction).append('|').append(m.sender).append('|').append(m.sensitiveRedacted?"<redacted>":m.text);return Fingerprint.text(s.toString());}
    private static String failureKind(Throwable e){Throwable x=e;while(x!=null){if(x instanceof CognitiveContractException)return"INVALID_CONTRACT";x=x.getCause();}String m=e==null||e.getMessage()==null?"":e.getMessage();return m.contains("invalid cognitive output")?"INVALID_CONTRACT":"INFERENCE_FAILED";}
    private static boolean isShadowCurrent(Slot slot){Slot current=slot==null?null:SHADOW_SLOTS.get(slot.key);return current==slot&&current!=null&&current.generation==slot.generation;}
    private static boolean isAuthorityCurrent(AuthoritySlot slot){AuthoritySlot current=slot==null?null:AUTHORITY_SLOTS.get(slot.key);return current==slot&&current!=null&&current.generation==slot.generation;}
    private static String slotKey(long threadId,long signalId){return threadId>0?"thread:"+threadId:"signal:"+signalId;}
    private static void cancel(ScheduledFuture<?> f){if(f!=null)f.cancel(false);}
    private static void safeAccepted(AuthorityCallback c,CognitiveResult result,long modelRunId){if(c!=null)try{c.accepted(result,modelRunId);}catch(Throwable ignored){}}
    private static void safeFallback(AuthorityCallback c,String reason){if(c!=null)try{c.fallback(reason);}catch(Throwable ignored){}}
    private static String clip(String s,int max){String x=s==null?"":s.trim();return x.length()<=max?x:x.substring(0,max);}
    private static String n(String s){return s==null?"":s;}

    private static final class Slot{final String key;final long signalId,threadId,generation;volatile ScheduledFuture<?> future;Slot(String key,long signalId,long threadId,long generation){this.key=key;this.signalId=signalId;this.threadId=threadId;this.generation=generation;}}
    private static final class AuthoritySlot{final String key;final long signalId,threadId,generation;final AuthorityCallback callback;final AtomicBoolean terminal=new AtomicBoolean(false);volatile ScheduledFuture<?> future,timeoutFuture;volatile boolean started,superseded,timedOut;AuthoritySlot(String key,long signalId,long threadId,long generation,AuthorityCallback callback){this.key=key;this.signalId=signalId;this.threadId=threadId;this.generation=generation;this.callback=callback;}}
}
