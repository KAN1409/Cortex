package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated reliability boundary for user-created voice captures.
 *
 * Manual voice must never be wedged indefinitely behind passive backlog or a process death.
 * The immutable WAV remains the source of truth; only derived transcript/analysis state changes.
 */
public final class VoiceCapturePipeline {
    private static final long STALE_ANALYZING_MS=120_000L;
    private static final long HARD_TIMEOUT_MS=45_000L;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-voice-capture");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cortex-voice-timeout");t.setDaemon(true);return t;});
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Set<Long> IN_FLIGHT=ConcurrentHashMap.newKeySet();

    private VoiceCapturePipeline(){}

    public static void submit(Context context,long itemId,Runnable changed){
        if(context==null||itemId<=0||!IN_FLIGHT.add(itemId))return;
        Context app=context.getApplicationContext();
        WORKER.execute(()->process(app,itemId,changed));
    }

    /** Called from voice-facing surfaces so interrupted analysis cannot stay "Processing" forever. */
    public static int recoverAndResume(Context context){
        if(context==null)return 0;
        Context app=context.getApplicationContext();VaultDb db=null;int recovered=0;
        try{
            db=new VaultDb(app);recovered=recoverStale(db);
            Cursor c=db.getReadableDatabase().rawQuery(
                    "SELECT id FROM knowledge_items WHERE type='AUDIO' AND source IN ('manual_recording','audio_import') AND status='queued' ORDER BY created_at ASC LIMIT 12",null);
            while(c.moveToNext())submit(app,c.getLong(0),null);c.close();
        }catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        return recovered;
    }

    static int recoverStale(VaultDb db){
        if(db==null)return 0;long cutoff=System.currentTimeMillis()-STALE_ANALYZING_MS;
        android.content.ContentValues v=new android.content.ContentValues();v.put("status","queued");v.put("analysis_error","Recovered after interrupted voice analysis");v.put("updated_at",System.currentTimeMillis());
        return db.getWritableDatabase().update("knowledge_items",v,
                "type='AUDIO' AND source IN ('manual_recording','audio_import') AND status='analyzing' AND updated_at<?",
                new String[]{String.valueOf(cutoff)});
    }

    private static void process(Context ctx,long itemId,Runnable changed){
        VaultDb db=null;AtomicBoolean settled=new AtomicBoolean(false);ScheduledFuture<?> timeout=null;
        try{
            db=new VaultDb(ctx);recoverStale(db);KnowledgeItem item=db.getById(itemId);
            if(item==null||!"AUDIO".equalsIgnoreCase(item.type)){finish(itemId,db,changed);return;}
            if("analyzed".equalsIgnoreCase(item.status)||"complete".equalsIgnoreCase(item.status)||"done".equalsIgnoreCase(item.status)){finish(itemId,db,changed);return;}

            android.content.ContentValues claim=new android.content.ContentValues();claim.put("status","analyzing");claim.put("analysis_error","");claim.put("updated_at",System.currentTimeMillis());
            int claimed=db.getWritableDatabase().update("knowledge_items",claim,"id=? AND status IN ('queued','failed_retryable','analysis_failed')",new String[]{String.valueOf(itemId)});
            if(claimed==0){KnowledgeItem now=db.getById(itemId);if(now!=null&&"analyzing".equalsIgnoreCase(now.status)&&System.currentTimeMillis()-now.updatedAt<STALE_ANALYZING_MS){finish(itemId,db,changed);return;}recoverStale(db);claimed=db.getWritableDatabase().update("knowledge_items",claim,"id=? AND status='queued'",new String[]{String.valueOf(itemId)});if(claimed==0){finish(itemId,db,changed);return;}}
            notifyChanged(changed);

            final VaultDb live=db;db=null;
            timeout=TIMER.schedule(()->{
                if(!settled.compareAndSet(false,true))return;
                WORKER.execute(()->{try{live.markFailedRetryable(itemId,"Voice analysis exceeded 45 seconds and was released for retry");}catch(Throwable ignored){}finally{close(live);IN_FLIGHT.remove(itemId);notifyChanged(changed);}});
            },HARD_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            final ScheduledFuture<?> timeoutRef=timeout;
            AudioAnalyzer.analyze(ctx,item,new AudioAnalyzer.Callback(){
                public void ok(AnalysisResult r){
                    if(!settled.compareAndSet(false,true))return;timeoutRef.cancel(false);
                    WORKER.execute(()->{try{live.applyAnalysis(itemId,r);AudioStore.save(live,itemId,r);try{TemporalResolver.afterAnalysis(live,itemId);}catch(Throwable ignored){}try{CoreBrainEngine.afterAnalysis(live,itemId);}catch(Throwable ignored){}try{IntentionalCognitiveBridge.afterAnalysis(live,item,r);}catch(Throwable ignored){}CapabilitySupervisor.recordHealthy(ctx,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION);}catch(Throwable e){try{live.markFailedRetryable(itemId,"Voice post-processing failed: "+safe(e));}catch(Throwable ignored){}}finally{close(live);IN_FLIGHT.remove(itemId);notifyChanged(changed);}});
                }
                public void fail(Exception e){
                    if(!settled.compareAndSet(false,true))return;timeoutRef.cancel(false);
                    WORKER.execute(()->{try{String m=e==null?"Voice analysis failed":e.getMessage();if(m==null)m="Voice analysis failed";if(m.startsWith("RETRYABLE:"))m=m.substring("RETRYABLE:".length()).trim();live.markFailedRetryable(itemId,m);}catch(Throwable ignored){}finally{close(live);IN_FLIGHT.remove(itemId);notifyChanged(changed);}});
                }
            });
        }catch(Throwable e){if(db!=null)try{db.markFailedRetryable(itemId,"Voice pipeline failed safely: "+safe(e));}catch(Throwable ignored){}finish(itemId,db,changed);}
    }

    private static void finish(long id,VaultDb db,Runnable changed){close(db);IN_FLIGHT.remove(id);notifyChanged(changed);}
    private static void close(VaultDb db){if(db!=null)try{db.close();}catch(Throwable ignored){}}
    private static void notifyChanged(Runnable r){if(r!=null)MAIN.post(()->{try{r.run();}catch(Throwable ignored){}});}
    private static String safe(Throwable e){if(e==null)return"unknown";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();}
}
