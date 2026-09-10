package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** One bounded idempotent drain for stateful meaning projections plus non-invasive v70 shadow evaluation. */
public final class StatefulMeaningWorker extends Worker {
    public StatefulMeaningWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        Context app=getApplicationContext();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DATABASE))return Result.success();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION))return Result.success();
        VaultDb db=null;
        try{
            db=new VaultDb(app);
            StatefulMeaningRebuilder.run(db,160);

            // v70 remains shadow-only. It records the cognitive answer without changing Now.
            try{
                CognitiveShadowStore.run(db.getWritableDatabase(),5);
            }catch(Throwable ignored){
                // Shadow cognition must never break the established deterministic drain.
            }

            CapabilitySupervisor.recordHealthy(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION);
            return StatefulMeaningRebuilder.hasBacklog(db)?Result.retry():Result.success();
        }catch(Throwable t){
            CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION,t);
            return Result.retry();
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
        }
    }
}
