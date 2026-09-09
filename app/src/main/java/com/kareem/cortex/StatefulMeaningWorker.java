package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** One bounded idempotent drain for stateful meaning projections. */
public final class StatefulMeaningWorker extends Worker {
    public StatefulMeaningWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){VaultDb db=null;try{db=new VaultDb(getApplicationContext());StatefulMeaningRebuilder.run(db,160);return StatefulMeaningRebuilder.hasBacklog(db)?Result.retry():Result.success();}catch(Throwable t){return Result.retry();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
