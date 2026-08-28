package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;

import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cognitive V2 shadow runner. It may write model_runs + diagnostics only.
 * It must never change raw legacy decisions, derived intelligence, Review, memory or Pulse.
 */
public final class CognitiveAdjudicatorV2 {
    public static final String POLICY="cognitive_v2_shadow_001";
    private static final long QUIET_MS=3000L;
    private static final ScheduledExecutorService SCHEDULER=Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService MODEL_EXECUTOR=Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String,Slot> SLOTS=new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION=new AtomicLong();

    private CognitiveAdjudicatorV2(){}

    public static void enqueueShadow(Context context,long signalId,long threadId){
        if(context==null||signalId<=0)return;
        Context app=context.getApplicationContext();
        if(!CognitiveFeatureFlags.shadowEnabled(app))return;
        String key=threadId>0?"thread:"+threadId:"signal:"+signalId;
        SLOTS.compute(key,(ignored,previous)->{
            if(previous!=null&&previous.future!=null)previous.future.cancel(false);
            Slot next=new Slot(key,signalId,threadId,GENERATION.incrementAndGet());
            next.future=SCHEDULER.schedule(()->fire(app,next),QUIET_MS,TimeUnit.MILLISECONDS);
            return next;
        });
    }

    private static void fire(Context app,Slot slot){if(isCurrent(slot))MODEL_EXECUTOR.execute(()->analyze(app,slot));}

    private static void analyze(Context app,Slot slot){
        VaultDb db=new VaultDb(app);long modelRunId=0;
        try{
            if(!isCurrent(slot))return;
            CognitiveInput input=CognitiveInputFactory.load(db,slot.signalId);
            if(input==null){recordSkipped(db,slot,"SIGNAL_MISSING");return;}
            if(input.latestText.isEmpty()){recordSkipped(db,slot,"EMPTY_CONTENT");return;}
            if(SensitiveSignalPolicy.containsSecret(input.latestText)){recordSkipped(db,slot,"SENSITIVE_BLOCKED");return;}
            if(isHardNoise(db,slot.signalId)){recordSkipped(db,slot,"HARD_NOISE");return;}
            if(!LocalModelManager.installed(app)){recordSkipped(db,slot,"MODEL_NOT_READY");return;}

            LegacyCognitiveSnapshot legacy=LegacyCognitiveSnapshotStore.get(db,slot.signalId);
            LocalQwenBrain brain=new LocalQwenBrain(app);
            long started=System.currentTimeMillis();
            LocalBrainRun run=brain.classifyWithTelemetry(input);
            long latency=System.currentTimeMillis()-started;

            if(!isCurrent(slot)){recordSuperseded(db,slot,run,latency);return;}

            JSONObject telemetry=CognitiveShadowComparator.compare(slot.signalId,legacy,run.result);
            telemetry.put("policy",POLICY);
            telemetry.put("generation",slot.generation);
            telemetry.put("signal_family",input.family.name());
            telemetry.put("source_app",clip(input.sourceApp,80));
            telemetry.put("tokens_per_second",run.tokensPerSecond);
            telemetry.put("tokens_generated",run.tokensGenerated);
            telemetry.put("generation_ms",run.generationMs);
            telemetry.put("model_load_ms",run.modelLoadMs);
            telemetry.put("duration_ms",run.durationMs);
            telemetry.put("cache_hit",run.cacheHit);
            telemetry.put("outcome","COMPLETE");

            modelRunId=AiJobStore.modelRun(
                    db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","complete",inputHash(input),latency,0,run.tokensGenerated,
                    run.result.confidence,telemetry.toString(),"");

            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_complete",
                    telemetry.optString("comparison",""),0,slot.threadId,slot.signalId,0,modelRunId,latency,
                    new JSONObject().put("policy",POLICY).put("comparison",telemetry.optString("comparison","")).put("family",input.family.name()));
        }catch(Throwable e){
            String error=e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage());
            try{
                if(modelRunId<=0){
                    JSONObject out=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId)
                            .put("policy",POLICY).put("outcome","FAILED").put("failure_kind",failureKind(e));
                    modelRunId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                            "cognitive_v2_shadow","failed","",0,0,0,0,out.toString(),clip(error,500));
                }
                DiagnosticsLog.error(db,"CognitiveAdjudicatorV2","shadow_failed",e,"COGNITIVE_V2_SHADOW",
                        0,slot.threadId,slot.signalId,0,modelRunId,new JSONObject().put("policy",POLICY).put("failure_kind",failureKind(e)));
            }catch(Throwable ignored){}
        }finally{
            SLOTS.remove(slot.key,slot);
            try{db.close();}catch(Throwable ignored){}
        }
    }

    private static boolean isHardNoise(VaultDb db,long signalId){
        Cursor c=db.getReadableDatabase().query("raw_signals",new String[]{"disposition","filter_engine"},
                "id=?",new String[]{String.valueOf(signalId)},null,null,null,"1");
        boolean noise=false;
        if(c.moveToFirst())noise="IGNORE".equalsIgnoreCase(c.getString(0))&&"deterministic_fast_gate".equalsIgnoreCase(c.getString(1));
        c.close();return noise;
    }

    private static void recordSkipped(VaultDb db,Slot slot,String reason){
        try{
            JSONObject output=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId)
                    .put("policy",POLICY).put("outcome","SKIPPED").put("reason",reason);
            long runId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","skipped","",0,0,0,0,output.toString(),"");
            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_skipped",reason,0,slot.threadId,slot.signalId,0,runId,0,
                    new JSONObject().put("policy",POLICY).put("reason",reason));
        }catch(Throwable ignored){}
    }

    private static void recordSuperseded(VaultDb db,Slot slot,LocalBrainRun run,long latency){
        try{
            JSONObject output=new JSONObject().put("schema","cognitive_shadow_001").put("signal_id",slot.signalId)
                    .put("policy",POLICY).put("outcome","SUPERSEDED").put("generation",slot.generation);
            long runId=AiJobStore.modelRun(db,0,1,"cognitive_shadow","local",LocalModelManager.MODEL_NAME,
                    "cognitive_v2_shadow","superseded","",latency,0,run.tokensGenerated,run.result.confidence,output.toString(),"");
            DiagnosticsLog.info(db,"CognitiveAdjudicatorV2","shadow_superseded","safe",0,slot.threadId,slot.signalId,0,runId,latency,
                    new JSONObject().put("policy",POLICY).put("generation",slot.generation));
        }catch(Throwable ignored){}
    }

    private static String inputHash(CognitiveInput input){
        StringBuilder s=new StringBuilder().append(input.signalId).append('|').append(input.occurredAt).append('|')
                .append(input.family.name()).append('|').append(input.sourcePackage).append('|').append(input.latestText);
        for(CognitiveMessage m:input.recentContext)s.append('|').append(m.occurredAt).append('|').append(m.direction).append('|').append(m.sender).append('|').append(m.sensitiveRedacted?"<redacted>":m.text);
        return Fingerprint.text(s.toString());
    }

    private static String failureKind(Throwable e){
        Throwable x=e;while(x!=null){if(x instanceof CognitiveContractException)return"INVALID_CONTRACT";x=x.getCause();}
        String m=e==null||e.getMessage()==null?"":e.getMessage();return m.contains("invalid cognitive output")?"INVALID_CONTRACT":"INFERENCE_FAILED";
    }

    private static boolean isCurrent(Slot slot){Slot current=slot==null?null:SLOTS.get(slot.key);return current==slot&&current!=null&&current.generation==slot.generation;}
    private static String clip(String s,int max){String x=s==null?"":s.trim();return x.length()<=max?x:x.substring(0,max);}

    private static final class Slot{
        final String key;final long signalId,threadId,generation;volatile ScheduledFuture<?> future;
        Slot(String key,long signalId,long threadId,long generation){this.key=key;this.signalId=signalId;this.threadId=threadId;this.generation=generation;}
    }
}
