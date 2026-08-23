package com.kareem.cortex;

import android.content.Context;
import android.os.SystemClock;
import androidx.work.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/** One-tap audit orchestration. The full automatic suite runs now and finalizes immediately. */
public final class CortexAuditScheduler {
    private CortexAuditScheduler(){}
    static String initial(long id){return "cortex-full-audit-initial-"+id;}
    static String soak(long id){return "cortex-full-audit-soak-"+id;}
    static String finish(long id){return "cortex-full-audit-finalize-"+id;}

    public static long start(Context c){
        long tap=SystemClock.elapsedRealtime();Context app=c.getApplicationContext();VaultDb db=new VaultDb(app);CortexAuditStore.Run r=CortexAuditStore.start(app,db);long id=r.id;
        long now=System.currentTimeMillis();db.getWritableDatabase().execSQL("UPDATE cortex_audit_runs SET target_end_at=?,summary=? WHERE id=?",new Object[]{now+30L*60L*1000L,"Immediate Cortex full application audit",id});
        WorkManager wm=WorkManager.getInstance(app);Data d=new Data.Builder().putLong("run_id",id).build();OneTimeWorkRequest first=new OneTimeWorkRequest.Builder(CortexAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-full-audit").build();wm.enqueueUniqueWork(initial(id),ExistingWorkPolicy.REPLACE,first);
        long ms=SystemClock.elapsedRealtime()-tap;try{CortexAuditStore.log(db,id,"info","audit_ui","work_scheduled","Start button acknowledged; immediate full-app audit queued in "+ms+" ms",new JSONObject().put("ui_schedule_latency_ms",ms).put("mode","immediate"));}catch(Exception ignored){}db.close();return id;
    }

    public static void stop(Context c,long runId){Context app=c.getApplicationContext();WorkManager wm=WorkManager.getInstance(app);wm.cancelUniqueWork(initial(runId));wm.cancelUniqueWork(soak(runId));wm.cancelUniqueWork(finish(runId));VaultDb db=new VaultDb(app);CortexAuditStore.updateRun(db,runId,"canceled","Stopped by user","Audit stopped",-1,"Audit stopped by user. Existing results remain in Debug Export.","");CortexAuditStore.log(db,runId,"warning","audit_ui","stopped","User stopped the immediate full-app audit. Collected evidence was kept.",null);db.close();}

    public static void finalizeNow(Context c,long runId){Context app=c.getApplicationContext();Data d=new Data.Builder().putLong("run_id",runId).build();WorkManager.getInstance(app).enqueueUniqueWork(finish(runId),ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(CortexAuditFinalizeWorker.class).setInputData(d).addTag("cortex-full-audit").build());}
}
