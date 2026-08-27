package com.kareem.cortex;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Periodic temporal refresh for due/follow-up/snooze transitions. */
public final class AttentionMaintenanceWorker extends Worker {
    public AttentionMaintenanceWorker(Context context,WorkerParameters params){super(context,params);}
    @Override public Result doWork(){VaultDb db=null;try{db=new VaultDb(getApplicationContext());AttentionMaintenance.refresh(db);return Result.success();}catch(Throwable e){try{if(db!=null)DiagnosticsLog.error(db,"AttentionMaintenanceWorker","refresh",e,"ATTENTION_MAINTENANCE",0,0,0,0,0,null);}catch(Throwable ignored){}return Result.retry();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
