package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** One bounded idempotent drain for stateful meaning projections plus non-invasive v70 shadow evaluation. */
public final class StatefulMeaningWorker extends Worker {
    public StatefulMeaningWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());
            StatefulMeaningRebuilder.run(db,160);

            // v70 runs beside the production projection path. It records what the cognitive
            // brain would have surfaced but never writes the tables consumed by Now.
            try{
                CognitiveShadowStore.run(db.getWritableDatabase(),5);
            }catch(Throwable ignored){
                // Shadow cognition must never break the established capture/meaning drain.
            }

            return StatefulMeaningRebuilder.hasBacklog(db)?Result.retry():Result.success();
        }catch(Throwable t){
            return Result.retry();
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
        }
    }
}
