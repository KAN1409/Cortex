package com.kareem.cortex.rebuild;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single cognition lane. Perception may finish independently, but product state changes only here. */
public final class BrainIntakeQueue {
    public interface Callback { void complete(long evidenceId, BrainStore.ApplyResult result, Exception error); }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor(r->new Thread(r,"cortex-brain-intake"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RECOVERY_RUNNING=new AtomicBoolean(false);
    private BrainIntakeQueue(){}

    public static void processVoice(Context context,long evidenceId,Callback callback){Context app=context.getApplicationContext();QUEUE.execute(()->processOne(app,evidenceId,callback));}

    public static void recoverPending(Context context,Callback callback){
        if(!RECOVERY_RUNNING.compareAndSet(false,true))return;Context app=context.getApplicationContext();
        QUEUE.execute(()->{CortexDb db=null;try{db=new CortexDb(app);List<Long> ids=BrainStore.pendingVoiceEvidence(db,4);for(Long id:ids){if(id==null||id<=0)continue;processOne(app,id,callback);}}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}RECOVERY_RUNNING.set(false);}});
    }

    private static void processOne(Context app,long evidenceId,Callback callback){
        CortexDb db=null;BrainStore.ApplyResult applied=null;Exception error=null;
        try{
            db=new CortexDb(app);BrainStore.ensure(db);if(BrainStore.applied(db,evidenceId)){return;}
            CortexDb.AttachmentEvidence evidence=db.attachmentEvidence(evidenceId);if(evidence==null)throw new IllegalArgumentException("Evidence not found");
            String transcript=BrainStore.transcript(db,evidenceId);if(transcript==null||transcript.trim().isEmpty())throw new IllegalArgumentException("Transcript not found");
            BrainStore.markRunning(db,evidenceId);
            String context=BrainStore.contextSnapshot(db,12);
            BrainIntakeEngine.Decision decision=CortexBrainRouter.understand(app,evidence,transcript,context);
            applied=BrainStore.apply(db,evidenceId,decision);
        }catch(Exception e){error=e;if(db!=null)try{BrainStore.markFailed(db,evidenceId,e);}catch(Throwable ignored){}
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
        BrainStore.ApplyResult finalApplied=applied;Exception finalError=error;
        if(callback!=null)MAIN.post(()->callback.complete(evidenceId,finalApplied,finalError));
    }
}
