package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide analysis queue.
 *
 * Recovery rule: the queue may run after SafeCoreRuntime is ready even while the emergency
 * StartupSafetyGate remains active. Native OCR remains separately quarantined. Voice analysis
 * prefers Android on-device ASR and may use configured cloud fallback only when privacy allows.
 */
public final class AnalysisQueue {
    private static final AtomicBoolean running=new AtomicBoolean(false);
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-analysis");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cortex-analysis-watchdog");t.setDaemon(true);return t;});
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long OCR_TIMEOUT_SEC=150, AUDIO_TIMEOUT_SEC=240;

    private AnalysisQueue(){}

    /** Existing callers may still pass their DB helper; it is deliberately ignored for queue work. */
    public static void kick(Context context,VaultDb ignoredDb,Runnable changed){
        if(context==null)return;
        Context app=context.getApplicationContext();
        if(!safeQueueAllowed(app))return;
        if(!running.compareAndSet(false,true))return;
        WORKER.execute(()->startRun(app,changed));
    }

    private static boolean safeQueueAllowed(Context ctx){
        return ctx!=null
                && CapabilitySupervisor.allowed(ctx,CapabilitySupervisor.Capability.DATABASE)
                && CapabilitySupervisor.allowed(ctx,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION);
    }

    private static void startRun(Context ctx,Runnable changed){
        if(!safeQueueAllowed(ctx)){running.set(false);return;}
        VaultDb db=null;
        try{
            db=new VaultDb(ctx);
            drain(ctx,db,changed);
        }catch(Throwable fatal){
            CapabilitySupervisor.recordFailure(ctx,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION,fatal);
            finishRun(ctx,db,changed);
        }
    }

    /** Iterates synchronous work; async analyzers return and resume later on WORKER. */
    private static void drain(Context ctx,VaultDb db,Runnable changed){
        if(!safeQueueAllowed(ctx)){finishRun(ctx,db,changed);return;}
        while(true){
            KnowledgeItem item;
            try{item=AnalysisQueuePriority.next(db);}
            catch(Throwable e){finishRun(ctx,db,changed);return;}
            if(item==null){CapabilitySupervisor.recordHealthy(ctx,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION);finishRun(ctx,db,changed);return;}

            // Images must not wedge every other queue item while OCR native is intentionally
            // quarantined. Leave the image retryable and continue draining safe work behind it.
            if(("SCREENSHOT".equals(item.type)||"IMAGE".equals(item.type))
                    && !CapabilitySupervisor.allowed(ctx,CapabilitySupervisor.Capability.OCR_NATIVE)){
                safeFail(db,item.id,new Exception("RETRYABLE: OCR native runtime is quarantined; image retained for later analysis"),changed);
                continue;
            }

            try{db.markAnalyzing(item.id);}catch(Throwable e){finishRun(ctx,db,changed);return;}
            notifyChanged(changed);

            if("SCREENSHOT".equals(item.type)||"IMAGE".equals(item.type)){
                analyzeImage(ctx,db,item,changed);return;
            }
            if("AUDIO".equals(item.type)){
                analyzeAudio(ctx,db,item,changed);return;
            }

            try{
                if("FILE".equals(item.type)){
                    finish(db,item,AttachmentAnalyzer.analyze(item),changed);
                }else{
                    AnalysisResult r=LocalAnalyzer.analyze(item.rawText,"text/plain");
                    guardPassiveSources(db,item,r);
                    db.applyAnalysis(item.id,r);post(db,item,r);notifyChanged(changed);
                }
            }catch(Throwable e){safeFail(db,item.id,e,changed);}
        }
    }

    private static void analyzeImage(Context ctx,VaultDb db,KnowledgeItem item,Runnable changed){
        if(!safeQueueAllowed(ctx)||!CapabilitySupervisor.allowed(ctx,CapabilitySupervisor.Capability.OCR_NATIVE)){
            safeFail(db,item.id,new Exception("RETRYABLE: OCR native runtime is quarantined"),changed);
            drain(ctx,db,changed);return;
        }
        AtomicBoolean settled=new AtomicBoolean(false);
        ScheduledFuture<?> timeout=WATCHDOG.schedule(()->{
            if(!settled.compareAndSet(false,true))return;
            WORKER.execute(()->{safeFail(db,item.id,new TimeoutException("RETRYABLE: OCR timed out"),changed);drain(ctx,db,changed);});
        },OCR_TIMEOUT_SEC,TimeUnit.SECONDS);
        try{
            OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){
                public void ok(AnalysisResult r){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{try{finish(db,item,r,changed);}catch(Throwable e){safeFail(db,item.id,e,changed);}drain(ctx,db,changed);});}
                public void fail(Exception e){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{safeFail(db,item.id,e,changed);drain(ctx,db,changed);});}
            });
        }catch(Throwable e){
            if(settled.compareAndSet(false,true)){timeout.cancel(false);safeFail(db,item.id,e,changed);drain(ctx,db,changed);}
        }
    }

    private static void analyzeAudio(Context ctx,VaultDb db,KnowledgeItem item,Runnable changed){
        if(!safeQueueAllowed(ctx)){finishRun(ctx,db,changed);return;}
        AtomicBoolean settled=new AtomicBoolean(false);
        ScheduledFuture<?> timeout=WATCHDOG.schedule(()->{
            if(!settled.compareAndSet(false,true))return;
            WORKER.execute(()->{safeFail(db,item.id,new TimeoutException("RETRYABLE: audio analysis timed out"),changed);drain(ctx,db,changed);});
        },AUDIO_TIMEOUT_SEC,TimeUnit.SECONDS);
        try{
            AudioAnalyzer.analyze(ctx,item,new AudioAnalyzer.Callback(){
                public void ok(AnalysisResult r){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{try{db.applyAnalysis(item.id,r);AudioStore.save(db,item.id,r);post(db,item,r);notifyChanged(changed);}catch(Throwable e){safeFail(db,item.id,e,changed);}drain(ctx,db,changed);});}
                public void fail(Exception e){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{safeFail(db,item.id,e,changed);drain(ctx,db,changed);});}
            });
        }catch(Throwable e){
            if(settled.compareAndSet(false,true)){timeout.cancel(false);safeFail(db,item.id,e,changed);drain(ctx,db,changed);}
        }
    }

    private static void guardPassiveSources(VaultDb db,KnowledgeItem item,AnalysisResult r){
        if(item==null||r==null)return;
        if("CONTACT".equals(item.type)&&"contacts_sync".equals(item.source)&&!r.actions.isEmpty()){
            int suppressed=r.actions.size();r.actions.clear();
            try{JSONObject m=new JSONObject();m.put("suppressed_actions",suppressed);m.put("policy","contacts_are_passive_evidence");DiagnosticsLog.info(db,"analysis","contact_action_suppressed","safe",item.id,0,0,0,0,0,m);}catch(Throwable ignored){}
        }
    }

    private static void finish(VaultDb db,KnowledgeItem item,AnalysisResult r,Runnable changed){db.applyAnalysis(item.id,r);post(db,item,r);notifyChanged(changed);}
    private static void post(VaultDb db,KnowledgeItem item,AnalysisResult r){try{TemporalResolver.afterAnalysis(db,item.id);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(db,item.id);}catch(Throwable ignored){}try{IntentionalCognitiveBridge.afterAnalysis(db,item,r);}catch(Throwable ignored){}}
    private static void safeFail(VaultDb db,long id,Throwable e,Runnable changed){
        try{
            String message=e==null?"Unknown error":e.getMessage();
            if(message==null||message.trim().isEmpty())message=e==null?"Unknown error":e.getClass().getSimpleName();
            if(message.startsWith("RETRYABLE:")){String clean=message.substring("RETRYABLE:".length()).trim();db.markFailedRetryable(id,clean.isEmpty()?"Retryable analysis failure":clean);}
            else if(e instanceof OutOfMemoryError)db.markFailedRetryable(id,"Image/audio analysis exceeded safe memory; retry with bounded processing");
            else db.markFailed(id,message);
        }catch(Throwable ignored){}
        notifyChanged(changed);
    }

    private static void finishRun(Context ctx,VaultDb db,Runnable changed){
        try{if(db!=null)db.close();}catch(Throwable ignored){}
        running.set(false);notifyChanged(changed);
        if(!safeQueueAllowed(ctx))return;
        WORKER.execute(()->{
            if(running.get())return;
            VaultDb probe=null;
            try{probe=new VaultDb(ctx);if(probe.pendingCount()>0)kick(ctx,null,changed);}catch(Throwable ignored){}
            finally{if(probe!=null)try{probe.close();}catch(Throwable ignored){}}
        });
    }

    private static void notifyChanged(Runnable changed){if(changed!=null)MAIN.post(()->{try{changed.run();}catch(Throwable ignored){}});}
}
