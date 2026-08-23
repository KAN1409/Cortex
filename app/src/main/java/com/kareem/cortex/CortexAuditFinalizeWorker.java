package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import org.json.JSONObject;

/** Final audit checkpoint. Converts observation tests into pass/warn outcomes and keeps every NOT RUN test explicit. */
public class CortexAuditFinalizeWorker extends Worker {
    public CortexAuditFinalizeWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){long runId=getInputData().getLong("run_id",0);if(runId<=0)return Result.failure();Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);try{CortexAuditStore.Run r=CortexAuditStore.get(db,runId);if(r==null||"canceled".equals(r.status)||"complete".equals(r.status))return Result.success();CortexAuditStore.updateRun(db,runId,"finalizing","Final checkpoint","Summarizing 72-hour evidence",99,"Final audit checkpoint running",null);JSONObject m=CortexAuditSoakWorker.capture(ctx,db,runId,"final");CortexAuditSoakWorker.updateObservations(ctx,db,r,m,true);
        int failed=CortexAuditStore.countTests(db,runId,"fail"),warn=CortexAuditStore.countTests(db,runId,"warn"),notRun=CortexAuditStore.countTests(db,runId,"not_run"),pass=CortexAuditStore.countTests(db,runId,"pass");String summary="Full audit finished • pass "+pass+" • warnings "+warn+" • failures "+failed+" • protected/not run "+notRun;CortexAuditStore.updateRun(db,runId,"complete","Complete","Audit complete",100,summary,failed>0?failed+" automatic test(s) failed":"");CortexAuditStore.log(db,runId,failed>0?"error":"info","audit","completed",summary,m);WorkManager.getInstance(ctx).cancelUniqueWork(CortexAuditScheduler.soak(runId));return Result.success();}catch(Exception e){CortexAuditStore.updateRun(db,runId,"complete","Complete with finalizer error","Audit complete with finalizer error",100,"Audit window ended but final summary hit an error",e.toString());CortexAuditStore.log(db,runId,"error","audit","finalizer_error",e.toString(),null);return Result.success();}finally{db.close();}}
}
