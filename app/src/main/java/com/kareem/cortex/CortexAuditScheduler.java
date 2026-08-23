package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import androidx.work.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/** One-tap persistent audit orchestration. Immediate checks run once; lightweight observation samples run every 6h; finalization occurs at 72h. */
public final class CortexAuditScheduler {
    private CortexAuditScheduler(){}
    static String initial(long id){return "cortex-full-audit-initial-"+id;}static String soak(long id){return "cortex-full-audit-soak-"+id;}static String finish(long id){return "cortex-full-audit-finalize-"+id;}

    public static long start(Context c){long tap=SystemClock.elapsedRealtime();Context app=c.getApplicationContext();VaultDb db=new VaultDb(app);CortexAuditStore.Run r=CortexAuditStore.start(app,db);long id=r.id;WorkManager wm=WorkManager.getInstance(app);Data d=new Data.Builder().putLong("run_id",id).build();
        OneTimeWorkRequest first=new OneTimeWorkRequest.Builder(CortexAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-full-audit").build();wm.enqueueUniqueWork(initial(id),ExistingWorkPolicy.KEEP,first);
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(CortexAuditSoakWorker.class,6,TimeUnit.HOURS).setInitialDelay(1,TimeUnit.HOURS).setInputData(d).addTag("cortex-full-audit").build();wm.enqueueUniquePeriodicWork(soak(id),ExistingPeriodicWorkPolicy.KEEP,periodic);
        OneTimeWorkRequest fin=new OneTimeWorkRequest.Builder(CortexAuditFinalizeWorker.class).setInitialDelay(CortexAuditStore.DEFAULT_DURATION_MS,TimeUnit.MILLISECONDS).setInputData(d).addTag("cortex-full-audit").build();wm.enqueueUniqueWork(finish(id),ExistingWorkPolicy.KEEP,fin);
        long ms=SystemClock.elapsedRealtime()-tap;try{CortexAuditStore.log(db,id,"info","audit_ui","work_scheduled","Start button acknowledged and WorkManager jobs scheduled in "+ms+" ms",new JSONObject().put("ui_schedule_latency_ms",ms));}catch(Exception ignored){}db.close();return id;}

    public static void stop(Context c,long runId){Context app=c.getApplicationContext();WorkManager wm=WorkManager.getInstance(app);wm.cancelUniqueWork(initial(runId));wm.cancelUniqueWork(soak(runId));wm.cancelUniqueWork(finish(runId));VaultDb db=new VaultDb(app);CortexAuditStore.updateRun(db,runId,"canceled","Stopped by user","Audit stopped",-1,"Audit stopped by user. Existing results remain in Debug Export.","");CortexAuditStore.log(db,runId,"warning","audit_ui","stopped","User stopped the multi-day audit. Collected evidence was kept.",null);db.close();}

    public static void finalizeNow(Context c,long runId){Context app=c.getApplicationContext();Data d=new Data.Builder().putLong("run_id",runId).build();WorkManager.getInstance(app).enqueueUniqueWork(finish(runId),ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(CortexAuditFinalizeWorker.class).setInputData(d).addTag("cortex-full-audit").build());}
}
