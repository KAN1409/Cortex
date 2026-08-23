package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import org.json.JSONObject;

/** Final immediate audit checkpoint. Long-duration-only checks stay explicit instead of blocking the result. */
public class CortexAuditFinalizeWorker extends Worker {
    public CortexAuditFinalizeWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){long runId=getInputData().getLong("run_id",0);if(runId<=0)return Result.failure();Context ctx=getApplicationContext();VaultDb db=new VaultDb(ctx);try{CortexAuditStore.Run r=CortexAuditStore.get(db,runId);if(r==null||"canceled".equals(r.status)||"complete".equals(r.status))return Result.success();CortexAuditStore.updateRun(db,runId,"finalizing","Final checkpoint","Summarizing immediate full-app evidence",96,"Final audit checkpoint running",null);JSONObject m=CortexAuditSoakWorker.capture(ctx,db,runId,"instant_final");CortexAuditSoakWorker.updateObservations(ctx,db,r,m,true);
        CortexAuditStore.updateObservation(db,runId,"background_soak","not_run","info","Long-duration soak was intentionally not required for this run. The full automatic suite completed immediately; background stability can be tested separately if ever needed.",new JSONObject().put("mode","immediate").put("samples",CortexAuditStore.sampleCount(db,runId)));
        CortexAuditV49Hardening.apply(ctx,db,runId,m);
        int failed=CortexAuditStore.countTests(db,runId,"fail"),warn=CortexAuditStore.countTests(db,runId,"warn"),notRun=CortexAuditStore.countTests(db,runId,"not_run"),pass=CortexAuditStore.countTests(db,runId,"pass");String summary="Full audit finished now • pass "+pass+" • warnings "+warn+" • failures "+failed+" • not run/protected "+notRun;CortexAuditStore.updateRun(db,runId,"complete","Complete","Audit complete",100,summary,failed>0?failed+" automatic test(s) failed":"");CortexAuditStore.log(db,runId,failed>0?"error":"info","audit","completed",summary,m);WorkManager.getInstance(ctx).cancelUniqueWork(CortexAuditScheduler.soak(runId));return Result.success();}catch(Exception e){CortexAuditStore.updateRun(db,runId,"complete","Complete with finalizer error","Audit complete with finalizer error",100,"Immediate audit finished but final summary hit an error",e.toString());CortexAuditStore.log(db,runId,"error","audit","finalizer_error",e.toString(),null);return Result.success();}finally{db.close();}}
}
