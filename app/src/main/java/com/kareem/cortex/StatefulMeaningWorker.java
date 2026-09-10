package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** One bounded idempotent drain for safe semantics, stateful projections, Brain memory and v70 Now cognition. */
public final class StatefulMeaningWorker extends Worker {
    public StatefulMeaningWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        Context app=getApplicationContext();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DATABASE))return Result.success();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION))return Result.success();
        VaultDb db=null;
        try{
            db=new VaultDb(app);
            DeterministicSemanticRecovery.recover(db,200);
            StatefulMeaningRebuilder.run(db,240);
            SemanticMemoryBridge.sync(db,300);
            try{
                CognitiveShadowStore.run(db.getWritableDatabase(),8);
            }catch(Throwable ignored){
                // Cognitive ranking must never break capture/semantic persistence.
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
