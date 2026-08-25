package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import androidx.work.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/** One-tap audit orchestration plus an explicit real stability-soak mode. */
public final class CortexAuditScheduler {
    private CortexAuditScheduler(){}
    static String initial(long id){return "cortex-full-audit-initial-"+id;}
    static String soak(long id){return "cortex-full-audit-soak-"+id;}
    static String finish(long id){return "cortex-full-audit-finalize-"+id;}

    public static final class SoakState {
        public final long runId,startedAt,targetEndAt,completedAt;
        public final String status,phase,summary;
        SoakState(long id,long started,long target,long completed,String st,String ph,String sum){runId=id;startedAt=started;targetEndAt=target;completedAt=completed;status=safe(st);phase=safe(ph);summary=safe(sum);}
        public boolean active(){return "starting".equals(status)||"running".equals(status)||"soaking".equals(status)||"finalizing".equals(status);}
    }

    public static long start(Context c){
        long tap=SystemClock.elapsedRealtime();Context app=c.getApplicationContext();VaultDb db=new VaultDb(app);
        CortexAuditStore.Run existing=CortexAuditStore.active(db);if(existing!=null){long id=existing.id;db.close();return id;}
        CortexAuditStore.Run r=CortexAuditStore.start(app,db);long id=r.id;long now=System.currentTimeMillis();db.getWritableDatabase().execSQL("UPDATE cortex_audit_runs SET target_end_at=?,summary=? WHERE id=?",new Object[]{now+30L*60L*1000L,"Immediate Cortex full application audit",id});db.getWritableDatabase().execSQL("UPDATE cortex_audit_events SET detail=? WHERE run_id=? AND event='started'",new Object[]{"Button tap accepted. Functional intelligence + immediate full-app audit queued.",id});
        WorkManager wm=WorkManager.getInstance(app);Data d=new Data.Builder().putLong("run_id",id).build();OneTimeWorkRequest functional=new OneTimeWorkRequest.Builder(CortexFunctionalAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-functional-audit").build();OneTimeWorkRequest first=new OneTimeWorkRequest.Builder(CortexAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-full-audit").build();OneTimeWorkRequest fin=new OneTimeWorkRequest.Builder(CortexAuditFinalizeWorker.class).setInputData(d).addTag("cortex-full-audit").build();wm.beginUniqueWork(initial(id),ExistingWorkPolicy.REPLACE,functional).then(first).then(fin).enqueue();
        long ms=SystemClock.elapsedRealtime()-tap;try{CortexAuditStore.log(db,id,"info","audit_ui","work_scheduled","Start button acknowledged; functional test + full-app audit + final summary queued in "+ms+" ms",new JSONObject().put("ui_schedule_latency_ms",ms).put("mode","immediate"));}catch(Exception ignored){}db.close();return id;
    }

    /** Explicit non-destructive 30-minute soak with real background samples. */
    public static long startSoak(Context c){
        Context app=c.getApplicationContext();VaultDb db=new VaultDb(app);
        try{
            CortexAuditStore.Run existing=CortexAuditStore.active(db);if(existing!=null)return existing.id;
            CortexAuditStore.Run r=CortexAuditStore.start(app,db);long id=r.id,now=System.currentTimeMillis();
            db.getWritableDatabase().execSQL("UPDATE cortex_audit_runs SET target_end_at=?,summary=?,phase=?,current_test=? WHERE id=?",new Object[]{now+30L*60L*1000L,"30-minute Cortex stability soak","Preparing stability observation","Running baseline functional checks",id});
            CortexAuditStore.log(db,id,"info","audit_ui","stability_soak_started","30-minute stability soak requested. Cortex will sample real background behavior without creating fake personal data.",new JSONObject().put("mode","stability_soak").put("duration_minutes",30));
            WorkManager wm=WorkManager.getInstance(app);Data d=new Data.Builder().putLong("run_id",id).build();
            OneTimeWorkRequest functional=new OneTimeWorkRequest.Builder(CortexFunctionalAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-stability-soak").build();
            OneTimeWorkRequest baseline=new OneTimeWorkRequest.Builder(CortexAuditWorker.class).setInputData(d).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-stability-soak").build();
            wm.beginUniqueWork(initial(id),ExistingWorkPolicy.REPLACE,functional).then(baseline).enqueue();
            OneTimeWorkRequest sample1=sample(d,5),sample2=sample(d,10),sample3=sample(d,10);
            OneTimeWorkRequest fin=new OneTimeWorkRequest.Builder(CortexAuditSoakFinalizeWorker.class).setInputData(d).setInitialDelay(5,TimeUnit.MINUTES).addTag("cortex-stability-soak").build();
            wm.beginUniqueWork(soak(id),ExistingWorkPolicy.REPLACE,sample1).then(sample2).then(sample3).then(fin).enqueue();
            return id;
        }catch(Exception e){throw new RuntimeException(e);}finally{db.close();}
    }

    private static OneTimeWorkRequest sample(Data d,long delayMin){return new OneTimeWorkRequest.Builder(CortexAuditSoakWorker.class).setInputData(d).setInitialDelay(delayMin,TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag("cortex-stability-soak").build();}

    /** Latest explicit soak, separate from the immediate full-audit history. */
    public static SoakState soakState(VaultDb db){
        if(db==null)return null;CortexAuditStore.ensure(db);Cursor c=db.getReadableDatabase().rawQuery("SELECT r.id,r.started_at,r.target_end_at,r.completed_at,r.status,r.phase,r.summary FROM cortex_audit_runs r WHERE EXISTS(SELECT 1 FROM cortex_audit_events e WHERE e.run_id=r.id AND e.event='stability_soak_started') ORDER BY r.id DESC LIMIT 1",null);SoakState s=null;if(c.moveToFirst())s=new SoakState(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6));c.close();return s;
    }

    public static void stop(Context c,long runId){Context app=c.getApplicationContext();WorkManager wm=WorkManager.getInstance(app);wm.cancelUniqueWork(initial(runId));wm.cancelUniqueWork(soak(runId));wm.cancelUniqueWork(finish(runId));VaultDb db=new VaultDb(app);CortexAuditStore.updateRun(db,runId,"canceled","Stopped by user","Audit/soak stopped",-1,"Cortex diagnostic run stopped by user. Existing results remain in Debug Export.","");CortexAuditStore.log(db,runId,"warning","audit_ui","stopped","User stopped the Cortex diagnostic run. Collected evidence was kept.",null);db.close();}

    public static void finalizeNow(Context c,long runId){Context app=c.getApplicationContext();Data d=new Data.Builder().putLong("run_id",runId).build();WorkManager.getInstance(app).enqueueUniqueWork(finish(runId),ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(CortexAuditFinalizeWorker.class).setInputData(d).addTag("cortex-full-audit").build());}
    private static String safe(String s){return s==null?"":s;}
}
