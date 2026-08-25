package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Strong vision only after cheap local triage. Background scope starts at v47 install to avoid cloud-processing the old library blindly. */
public class VisualIntelligenceWorker extends Worker {
    private static final int BATCH=2;
    public VisualIntelligenceWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);VisualInsightStore.ensure(db);VisualRecoveryStore.ensure(db);long start=VisualInsightStore.backgroundStart(ctx);int done=0;try{
        VisualRecoveryStore.adoptLegacyFailures(db);VisualRecoveryStore.activateDue(db);
        long gateWait=VisionRateLimitGate.remainingMs(ctx);if(gateWait>0){VisualInsightStore.setWorker(ctx,"waiting",0,"Vision provider cooling down","Retrying automatically in about "+Math.max(1,Math.round(gateWait/1000.0))+"s");VisualIntelligenceScheduler.continueChain(ctx,gateWait);return Result.success();}
        while(done<BATCH&&!isStopped()){
            KnowledgeItem k=VisualInsightStore.nextBackground(db,start);if(k==null)break;
            try{
                VisualInsightStore.setWorker(ctx,"running",k.id,"Local value + privacy check","Deciding whether this screenshot deserves strong vision");
                VisualTriage.Result t=VisualTriage.evaluate(k);String triage=t.reason+(t.signals.isEmpty()?"":" • signal="+t.signals)+(t.privacyScore>0?" • privacy_score="+t.privacyScore+"/100":"");
                if(t.sensitive){VisualInsightStore.saveState(db,k.id,"local_only","local_only",triage);VisualRecoveryStore.clear(db,k.id);VisualInsightStore.setWorker(ctx,"running",k.id,"Protected locally",triage);done++;continue;}
                if(t.ephemeral&&t.valueScore<25){VisualInsightStore.saveState(db,k.id,"skipped","safe",triage);VisualRecoveryStore.clear(db,k.id);VisualInsightStore.setWorker(ctx,"running",k.id,"Skipped low-value screen",triage);done++;continue;}
                if(!GeminiKeyStore.has(ctx)){VisualInsightStore.saveState(db,k.id,"waiting_provider","safe","Gemini vision is not configured");VisualInsightStore.setWorker(ctx,"waiting",k.id,"Waiting for vision provider","Gemini key not configured. Configure Gemini, then Cortex can resume this item.");break;}
                try{
                    VisualInsightStore.setWorker(ctx,"running",k.id,"Understanding the image","Strong vision is reading objects, text, context and useful actions");JSONObject r=GeminiVisionAnalyzer.analyze(ctx,k);VisualInsightStore.saveModel(db,k.id,r.optString("_provider","gemini-vision"),r);VisualRecoveryStore.clear(db,k.id);VisualInsightStore.setWorker(ctx,"running",k.id,"Saved useful understanding",r.optString("description",""));done++;
                }catch(Throwable e){Failure f=recordFailure(db,ctx,k,e);if(f.wait)break;done++;}
            }catch(Throwable e){Failure f=recordFailure(db,ctx,k,e);if(f.wait)break;done++;}
        }
        if(!GeminiKeyStore.has(ctx)){VisualInsightStore.setWorker(ctx,"waiting",0,"Waiting for vision provider","Configure Gemini to resume pending visual understanding. No retry loop is running.");return Result.success();}
        VisualRecoveryStore.activateDue(db);
        if(VisualInsightStore.countPendingSince(db,start)>0)VisualIntelligenceScheduler.continueChain(ctx);
        else {long retryDelay=VisualRecoveryStore.nextDelayMs(db);if(retryDelay>=0){VisualInsightStore.setWorker(ctx,"waiting",0,"Waiting for bounded visual retry","Retrying recoverable visual work in about "+Math.max(1,Math.round(retryDelay/1000.0))+"s");VisualIntelligenceScheduler.continueChain(ctx,retryDelay);}else VisualInsightStore.setWorker(ctx,"idle",0,"Caught up","New screenshots will be understood when useful, charging and online");}
        return Result.success();
    }catch(Throwable e){try{VisualInsightStore.setWorker(ctx,"waiting",0,"Worker-level retry",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}catch(Throwable ignored){}return Result.retry();}finally{try{db.close();}catch(Throwable ignored){}}}

    private static final class Failure {final boolean wait;Failure(boolean wait){this.wait=wait;}}
    private Failure recordFailure(VaultDb db,Context ctx,KnowledgeItem k,Throwable error){
        int previous=VisualRecoveryStore.attempts(db,k.id);VisualFailurePolicy.Decision decision=VisualFailurePolicy.classify(error,previous);VisualRecoveryStore.State state=VisualRecoveryStore.record(db,k.id,decision,error);String msg=error.getClass().getSimpleName()+": "+(error.getMessage()==null?"":error.getMessage());
        if(state!=null&&state.recoverable){VisualInsightStore.saveState(db,k.id,"retry_wait","safe",decision.kind+" · "+msg+" · "+decision.nextAction);long wait=Math.max(1_000L,state.nextRetryAt-System.currentTimeMillis());VisualInsightStore.setWorker(ctx,"waiting",k.id,"Visual retry scheduled",decision.nextAction+" Retry in about "+Math.max(1,Math.round(wait/1000.0))+"s · attempt "+state.attempts+"/"+VisualFailurePolicy.MAX_TRANSIENT_ATTEMPTS);return new Failure(true);}
        VisualInsightStore.saveState(db,k.id,"failed","safe",decision.kind+" · "+msg+" · "+decision.nextAction);VisualInsightStore.setWorker(ctx,"running",k.id,"Visual item needs attention",decision.nextAction);return new Failure(false);
    }
}
