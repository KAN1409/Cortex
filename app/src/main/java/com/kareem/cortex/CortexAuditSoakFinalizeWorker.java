package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import org.json.JSONObject;

/** Final checkpoint for the explicit 30-minute stability soak. */
public final class CortexAuditSoakFinalizeWorker extends Worker {
    public CortexAuditSoakFinalizeWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        long runId=getInputData().getLong("run_id",0);if(runId<=0)return Result.failure();Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);
        try{
            CortexAuditStore.Run r=CortexAuditStore.get(db,runId);if(r==null||"canceled".equals(r.status)||"complete".equals(r.status))return Result.success();
            CortexAuditStore.updateRun(db,runId,"finalizing","Stability soak final checkpoint","Comparing background samples",96,"30-minute stability soak is finishing",null);
            JSONObject m=CortexAuditSoakWorker.capture(ctx,db,runId,"soak_final");
            CortexAuditSoakWorker.updateObservations(ctx,db,r,m,true);
            CortexAuditV49Hardening.apply(ctx,db,runId,m);
            int samples=CortexAuditStore.sampleCount(db,runId),failed=CortexAuditStore.countTests(db,runId,"fail"),warn=CortexAuditStore.countTests(db,runId,"warn"),pass=CortexAuditStore.countTests(db,runId,"pass");
            String summary="30-minute stability soak complete • samples "+samples+" • pass "+pass+" • warnings "+warn+" • failures "+failed;
            CortexAuditStore.updateRun(db,runId,"complete","Stability soak complete","Observation finished",100,summary,failed>0?failed+" diagnostic test(s) failed":"");
            CortexAuditStore.log(db,runId,failed>0?"error":"info","audit","stability_soak_completed",summary,m);
            WorkManager.getInstance(ctx).cancelUniqueWork(CortexAuditScheduler.soak(runId));
            return Result.success();
        }catch(Throwable e){
            CortexAuditStore.updateRun(db,runId,"complete","Stability soak finished with error","Final checkpoint error",100,"Stability soak ended, but the final summary hit an error",e.toString());
            try{CortexAuditStore.log(db,runId,"error","audit","stability_soak_finalizer_error",e.toString(),null);}catch(Throwable ignored){}
            return Result.success();
        }finally{db.close();}
    }
}
