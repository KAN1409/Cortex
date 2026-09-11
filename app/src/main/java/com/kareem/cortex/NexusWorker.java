package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Durable background refresh for the NEXUS projection inside Cortex. */
public final class NexusWorker extends Worker {
    public NexusWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.retry();
        VaultDb db=new VaultDb(getApplicationContext());
        try{NexusEngine.refresh(db);return Result.success();}
        catch(Throwable t){return Result.retry();}
        finally{try{db.close();}catch(Throwable ignored){}}
    }
}
