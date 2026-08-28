package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Debounced, budgeted autonomous Deep Brain loop. */
public final class CognitiveReasoningOrchestratorV4 {
    public static final String DEFAULT_QUESTION="What needs my attention now, why, and what should I do next?";
    private static final String UNIQUE_WORK="cortex-autonomous-deep-brain-v1";
    private static final CopyOnWriteArrayList<WeakReference<Listener>> LISTENERS=new CopyOnWriteArrayList<>();
    private CognitiveReasoningOrchestratorV4(){}

    public interface Listener{void onCognitiveReasoningUpdated();}
    public static void addListener(Listener l){if(l==null)return;removeListener(l);LISTENERS.add(new WeakReference<>(l));}
    public static void removeListener(Listener l){for(WeakReference<Listener> r:LISTENERS){Listener x=r.get();if(x==null||x==l)LISTENERS.remove(r);}}

    /**
     * Durable coalescing queue. New triggers never replace/cancel a currently-running provider call.
     * Bursty callbacks inside the short enqueue debounce share the already-queued reevaluation; later
     * meaningful changes append safely. Every Worker re-evaluates canonical freshness, fingerprint,
     * cooldown and budget at execution time, so already-covered queued work becomes a cheap no-op.
     */
    public static void schedule(Context context,String trigger){schedule(context,trigger,false);}
    private static void schedule(Context context,String trigger,boolean force){
        if(context==null)return;Context app=context.getApplicationContext();if(!CognitiveAutoReasoningSettingsV4.enabled(app)||!GeminiKeyStore.has(app))return;long claimedAt=System.currentTimeMillis();
        if(!CognitiveAutoReasoningSettingsV4.claimEnqueueSlot(app,claimedAt,force))return;
        try{
            Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            Data input=new Data.Builder().putString(CognitiveReasoningWorkerV4.KEY_TRIGGER,n(trigger)).build();
            OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(CognitiveReasoningWorkerV4.class)
                    .setConstraints(constraints)
                    .setInputData(input)
                    .setInitialDelay(4,TimeUnit.SECONDS)
                    .addTag(UNIQUE_WORK)
                    .build();
            WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_WORK,ExistingWorkPolicy.APPEND_OR_REPLACE,work);
        }catch(Throwable ignored){CognitiveAutoReasoningSettingsV4.releaseEnqueueSlot(app,claimedAt);}
    }

    static RunResult run(Context context,String trigger){
        if(context==null)return RunResult.skipped("no_context");Context app=context.getApplicationContext();VaultDb db=null;String runId="";long started=System.currentTimeMillis();CognitiveAutoReasoningPolicyV4.Decision decision=null;
        try{
            if(!CognitiveAutoReasoningSettingsV4.enabled(app)||!GeminiKeyStore.has(app))return RunResult.skipped("disabled_or_unconfigured");
            db=new VaultDb(app);CognitiveStoreV4.ensure(db);CognitiveDeepBrainStoreV4.ensure(db);CognitiveReasoningRunStoreV4.ensure(db);
            try{CognitiveSituationEngineV4.refresh(db);CognitiveDeepBrainReconcilerV4.reconcile(db);}catch(Throwable ignored){}
            decision=CognitiveAutoReasoningPolicyV4.evaluate(CognitivePulseProjectionV4.current(db,12),started);if(!decision.shouldRun)return RunResult.skipped(decision.reason);
            CognitiveAutoReasoningSettingsV4.Gate gate=CognitiveAutoReasoningSettingsV4.canStart(app,decision.fingerprint,decision.urgent,started);if(!gate.allowed)return RunResult.skipped(gate.reason);
            // Internal Gemini dispatch is not an export. The request stays CREATED until Cortex
            // validates/applies the provider response; ChatGPT share still uses EXPORTED explicitly.
            CognitiveDeepBrainPacketBuilderV4.Packet packet=CognitiveDeepBrainPacketBuilderV4.build(app,db,DEFAULT_QUESTION);
            CognitiveReasoningProviderV4 provider=new GeminiCognitiveReasoningProviderV4();String model=provider.model(app);CognitiveAutoReasoningSettingsV4.markStarted(app,decision.fingerprint,started);runId=CognitiveReasoningRunStoreV4.begin(db,packet.requestId,provider.id(),model,trigger,decision.fingerprint,started);
            CognitiveReasoningProviderV4.Result modelResult=provider.reason(app,packet);CognitiveDeepBrainApplyV4.Result applied=CognitiveDeepBrainApplyV4.apply(db,modelResult.rawResponse,CognitiveDeepBrainApplyV4.ORIGIN_GEMINI_AUTONOMOUS);long now=System.currentTimeMillis();CognitiveReasoningRunStoreV4.complete(db,runId,modelResult.durationMs,now);CognitiveAutoReasoningSettingsV4.markSuccess(app,decision.fingerprint,decision.urgent,now);
            try{DiagnosticsLog.info(db,"CognitiveReasoningOrchestratorV4","autonomous_reasoning_applied",provider.id()+":"+model,0,0,0,0,0,modelResult.durationMs,null);}catch(Throwable ignored){}
            notifyListeners();return new RunResult(true,false,"applied",provider.id(),model,packet.requestId,decision.freshCount,applied.rankedPrioritiesStored,applied.actionsCreated);
        }catch(Throwable e){
            long now=System.currentTimeMillis();
            if(isStaleContext(e)){
                CognitiveAutoReasoningSettingsV4.clearTransientGateAfterStaleContext(app);
                if(db!=null&&!runId.isEmpty())try{CognitiveReasoningRunStoreV4.stale(db,runId,Math.max(0,now-started),now);}catch(Throwable ignored){}
                if(db!=null)try{DiagnosticsLog.info(db,"CognitiveReasoningOrchestratorV4","autonomous_reasoning_stale_context","fresh canonical context superseded provider response",0,0,0,0,0,Math.max(0,now-started),null);}catch(Throwable ignored){}
                // Forced recovery must not be swallowed by the normal burst debounce. It appends
                // behind the current Worker and therefore still never cancels an in-flight call.
                schedule(app,"stale_context_refresh",true);
                return new RunResult(false,true,"stale_context","gemini",GeminiModelConfig.generationModel(app),"",decision==null?0:decision.freshCount,0,0);
            }
            CognitiveAutoReasoningSettingsV4.markFailure(app,now);if(db!=null&&!runId.isEmpty())try{CognitiveReasoningRunStoreV4.fail(db,runId,e.getClass().getSimpleName()+": "+n(e.getMessage()),Math.max(0,now-started),now);}catch(Throwable ignored){}if(db!=null)try{DiagnosticsLog.warn(db,"CognitiveReasoningOrchestratorV4","autonomous_reasoning_failed","failed",e.getClass().getSimpleName(),0,0,0,0,0,null);}catch(Throwable ignored){}return new RunResult(false,false,"failed:"+e.getClass().getSimpleName(),"gemini",GeminiModelConfig.generationModel(app),"",decision==null?0:decision.freshCount,0,0);
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    static boolean isStaleContext(Throwable e){String message=e==null?"":n(e.getMessage());return e instanceof IllegalArgumentException&&message.startsWith("Cortex context changed after this Deep Brain request was built");}
    private static void notifyListeners(){new Handler(Looper.getMainLooper()).post(()->{for(WeakReference<Listener> r:LISTENERS){Listener x=r.get();if(x==null){LISTENERS.remove(r);continue;}try{x.onCognitiveReasoningUpdated();}catch(Throwable ignored){}}});}
    private static String n(String s){return s==null?"":s.trim();}
    public static final class RunResult{public final boolean applied,skipped;public final String state,provider,model,requestId;public final int freshCount,priorities,actions;RunResult(boolean applied,boolean skipped,String state,String provider,String model,String requestId,int fresh,int priorities,int actions){this.applied=applied;this.skipped=skipped;this.state=n(state);this.provider=n(provider);this.model=n(model);this.requestId=n(requestId);freshCount=fresh;this.priorities=priorities;this.actions=actions;}static RunResult skipped(String reason){return new RunResult(false,true,reason,"","","",0,0,0);}}
}
