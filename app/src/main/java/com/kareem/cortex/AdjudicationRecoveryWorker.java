package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Belt-and-suspenders recovery for stale local adjudication jobs after process death. */
public final class AdjudicationRecoveryWorker extends Worker {
    public AdjudicationRecoveryWorker(@NonNull Context appContext,@NonNull WorkerParameters params){super(appContext,params);}
    @NonNull @Override public Result doWork(){VaultDb db=null;try{db=new VaultDb(getApplicationContext());AdjudicationRecovery.run(getApplicationContext(),db);return Result.success();}catch(Throwable e){return Result.retry();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
