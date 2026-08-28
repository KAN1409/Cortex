package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide analysis queue.
 *
 * Crash-safety rules:
 * - never analyze on an Activity/UI thread;
 * - never recurse through a backlog;
 * - never borrow an Activity-owned SQLiteOpenHelper;
 * - async OCR/ASR jobs have watchdogs so one missing callback cannot wedge the queue forever;
 * - UI change callbacks are always posted to the main thread.
 */
public final class AnalysisQueue {
    private static final AtomicBoolean running=new AtomicBoolean(false);
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-analysis");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cortex-analysis-watchdog");t.setDaemon(true);return t;});
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long OCR_TIMEOUT_SEC=150, AUDIO_TIMEOUT_SEC=240;

    private AnalysisQueue(){}
    public static boolean isRunning(){return running.get();}

    /** Existing callers may still pass their DB helper; it is deliberately ignored for queue work. */
    public static void kick(Context context,VaultDb ignoredDb,Runnable changed){
        if(context==null)return;
        Context app=context.getApplicationContext();
        if(!running.compareAndSet(false,true))return;
        WORKER.execute(()->startRun(app,changed));
    }

    private static void startRun(Context ctx,Runnable changed){
        VaultDb db=null;
        try{
            db=new VaultDb(ctx);DiagnosticsLog.ensure(db);
            JSONObject meta=new JSONObject();meta.put("pending",db.pendingCount());
            DiagnosticsLog.info(db,"analysis_queue","run_started","running",0,0,0,0,0,0,meta);
            drain(ctx,db,changed);
        }catch(Throwable fatal){
            if(db!=null)DiagnosticsLog.error(db,"analysis_queue","run_fatal",fatal,"ANALYSIS_RUN_FATAL",0,0,0,0,0,null);
            finishRun(ctx,db,changed);
        }
    }

    /** Iterates synchronous work; async analyzers return and resume later on WORKER. */
    private static void drain(Context ctx,VaultDb db,Runnable changed){
        while(true){
            KnowledgeItem item;
            try{item=db.nextPending();}
            catch(Throwable e){DiagnosticsLog.error(db,"analysis_queue","next_pending",e,"ANALYSIS_NEXT",0,0,0,0,0,null);finishRun(ctx,db,changed);return;}
            if(item==null){finishRun(ctx,db,changed);return;}

            try{db.markAnalyzing(item.id);logItemStart(ctx,db,item);}catch(Throwable e){DiagnosticsLog.error(db,"analysis_queue","mark_analyzing",e,"ANALYSIS_MARK",item.id,0,0,0,0,null);finishRun(ctx,db,changed);return;}
            notifyChanged(changed);

            if("SCREENSHOT".equals(item.type)||"IMAGE".equals(item.type)){
                analyzeImage(ctx,db,item,changed);return;
            }
            if("AUDIO".equals(item.type)){
                analyzeAudio(ctx,db,item,changed);return;
            }

            try{
                if("FILE".equals(item.type)){
                    finish(ctx,db,item,AttachmentAnalyzer.analyze(item),changed);
                }else{
                    AnalysisResult r=LocalAnalyzer.analyze(item.rawText,"text/plain");
                    guardPassiveSources(db,item,r);
                    db.applyAnalysis(item.id,r);post(ctx,db,item,r);logSuccess(db,item,r,0);notifyChanged(changed);
                }
            }catch(Throwable e){safeFail(db,item.id,e,changed);}
        }
    }

    private static void analyzeImage(Context ctx,VaultDb db,KnowledgeItem item,Runnable changed){
        AtomicBoolean settled=new AtomicBoolean(false);
        ScheduledFuture<?> timeout=WATCHDOG.schedule(()->{
            if(!settled.compareAndSet(false,true))return;
            WORKER.execute(()->{safeFail(db,item.id,new TimeoutException("RETRYABLE: OCR timed out"),changed);drain(ctx,db,changed);});
        },OCR_TIMEOUT_SEC,TimeUnit.SECONDS);
        try{
            OcrAnalyzer.analyze(ctx,item,new OcrAnalyzer.Callback(){
                public void ok(AnalysisResult r){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{try{finish(ctx,db,item,r,changed);}catch(Throwable e){safeFail(db,item.id,e,changed);}drain(ctx,db,changed);});}
                public void fail(Exception e){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{safeFail(db,item.id,e,changed);drain(ctx,db,changed);});}
            });
        }catch(Throwable e){
            if(settled.compareAndSet(false,true)){timeout.cancel(false);safeFail(db,item.id,e,changed);drain(ctx,db,changed);}
        }
    }

    private static void analyzeAudio(Context ctx,VaultDb db,KnowledgeItem item,Runnable changed){
        final long started=System.currentTimeMillis();
        try{
            JSONObject meta=new JSONObject();File f=item.attachmentPath==null?null:new File(item.attachmentPath);meta.put("attachment_exists",f!=null&&f.exists());meta.put("attachment_bytes",f!=null&&f.exists()?f.length():0);meta.put("cloud_audio_allowed",PrivacyPolicy.canUseCloud(ctx,"audio"));meta.put("gemini_configured",GeminiKeyStore.has(ctx));meta.put("groq_configured",GroqKeyStore.has(ctx));
            DiagnosticsLog.info(db,"audio_asr","attempt","started",item.id,0,0,0,0,0,meta);
        }catch(Throwable ignored){}
        AtomicBoolean settled=new AtomicBoolean(false);
        ScheduledFuture<?> timeout=WATCHDOG.schedule(()->{
            if(!settled.compareAndSet(false,true))return;
            WORKER.execute(()->{safeFail(db,item.id,new TimeoutException("RETRYABLE: audio analysis timed out"),changed);drain(ctx,db,changed);});
        },AUDIO_TIMEOUT_SEC,TimeUnit.SECONDS);
        try{
            AudioAnalyzer.analyze(ctx,item,new AudioAnalyzer.Callback(){
                public void ok(AnalysisResult r){if(!settled.compareAndSet(false,true))return;timeout.cancel(false);WORKER.execute(()->{try{db.applyAnalysis(item.id,r);AudioStore.save(db,item.id,r);post(ctx,db,item,r);logSuccess(db,item,r,System.currentTimeMillis()-started);notifyChanged(changed);}catch(Throwable e){safeFail(db,item.id,e,changed);}drain(ctx,db,changed);});}
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

    private static void finish(Context ctx,VaultDb db,KnowledgeItem item,AnalysisResult r,Runnable changed){db.applyAnalysis(item.id,r);post(ctx,db,item,r);logSuccess(db,item,r,0);notifyChanged(changed);}
    private static void post(Context ctx,VaultDb db,KnowledgeItem item,AnalysisResult r){try{TemporalResolver.afterAnalysis(db,item.id);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(db,item.id);}catch(Throwable ignored){}try{IntentionalCognitiveBridge.afterAnalysis(ctx,db,item,r);}catch(Throwable ignored){}}

    private static void safeFail(VaultDb db,long id,Throwable e,Runnable changed){
        try{
            String message=e==null?"Unknown error":e.getMessage();
            if(message==null||message.trim().isEmpty())message=e==null?"Unknown error":e.getClass().getSimpleName();
            String code=message.startsWith("RETRYABLE:")?"RETRYABLE":"ANALYSIS_FAILED";
            if(message.startsWith("RETRYABLE:")){String clean=message.substring("RETRYABLE:".length()).trim();db.markFailedRetryable(id,clean.isEmpty()?"Retryable analysis failure":clean);}
            else if(e instanceof OutOfMemoryError){code="OOM_RETRYABLE";db.markFailedRetryable(id,"Image/audio analysis exceeded safe memory; retry with bounded processing");}
            else db.markFailed(id,message);
            JSONObject meta=new JSONObject();meta.put("retryable","RETRYABLE".equals(code)||"OOM_RETRYABLE".equals(code));meta.put("error_summary",clip(message,240));
            DiagnosticsLog.error(db,"analysis_queue","item_failed",e,code,id,0,0,0,0,meta);
        }catch(Throwable ignored){}
        notifyChanged(changed);
    }

    private static void finishRun(Context ctx,VaultDb db,Runnable changed){
        try{if(db!=null){JSONObject meta=new JSONObject();meta.put("pending",db.pendingCount());meta.put("failed",db.failedCount());DiagnosticsLog.info(db,"analysis_queue","run_finished","idle",0,0,0,0,0,0,meta);}}catch(Throwable ignored){}
        try{if(db!=null)db.close();}catch(Throwable ignored){}
        running.set(false);notifyChanged(changed);
        WORKER.execute(()->{
            if(running.get())return;
            VaultDb probe=null;
            try{probe=new VaultDb(ctx);if(probe.pendingCount()>0)kick(ctx,null,changed);}catch(Throwable ignored){}
            finally{if(probe!=null)try{probe.close();}catch(Throwable ignored){}}
        });
    }

    private static void logItemStart(Context ctx,VaultDb db,KnowledgeItem item){try{JSONObject meta=new JSONObject();meta.put("type",item.type);meta.put("source",item.source);meta.put("has_attachment",item.attachmentPath!=null&&!item.attachmentPath.isEmpty());if("AUDIO".equals(item.type)){meta.put("cloud_audio_allowed",PrivacyPolicy.canUseCloud(ctx,"audio"));meta.put("gemini_configured",GeminiKeyStore.has(ctx));meta.put("groq_configured",GroqKeyStore.has(ctx));}DiagnosticsLog.info(db,"analysis_queue","item_started","analyzing",item.id,0,0,0,0,0,meta);}catch(Throwable ignored){}}
    private static void logSuccess(VaultDb db,KnowledgeItem item,AnalysisResult r,long latency){try{JSONObject meta=new JSONObject();meta.put("type",item.type);meta.put("engine",r==null?"":r.engine);meta.put("extracted_chars",r==null||r.extractedText==null?0:r.extractedText.length());meta.put("audio_duration_ms",r==null?0:r.audioDurationMs);meta.put("audio_processed_ms",r==null?0:r.audioProcessedDurationMs);DiagnosticsLog.info(db,"analysis_queue","item_analyzed","analyzed",item.id,0,0,0,0,latency,meta);}catch(Throwable ignored){}}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
    private static void notifyChanged(Runnable changed){if(changed!=null)MAIN.post(()->{try{changed.run();}catch(Throwable ignored){}});}
}
