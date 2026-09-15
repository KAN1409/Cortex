package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Fast local Tier-0 refresh. No model/network dependency. */
public final class WorkDeterministicInsightWorker extends Worker {
    public WorkDeterministicInsightWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        VaultDb db=null;try{
            db=new VaultDb(getApplicationContext());
            WorkDeterministicInsightEngine.Result r=WorkDeterministicInsightEngine.refresh(db,8);
            return Result.success(new Data.Builder().putInt("considered",r.considered).putInt("published",r.published).putInt("suppressed",r.suppressed).putInt("skipped",r.skipped).build());
        }catch(Throwable t){
            return Result.failure(new Data.Builder().putString("error","Deterministic Work intelligence failed: "+t.getClass().getSimpleName()).build());
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }
}
