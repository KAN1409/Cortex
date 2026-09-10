package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Guarantees one stateful meaning drain at a time; retries are idempotent. */
public final class StatefulMeaningScheduler {
    public static final String UNIQUE_WORK="cortex-stateful-meaning-drain";
    private StatefulMeaningScheduler(){}
    public static void kick(Context c){
        if(c==null)return;
        Context app=c.getApplicationContext();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DATABASE))return;
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING))return;
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION))return;
        Constraints x=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(StatefulMeaningWorker.class).setConstraints(x).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag(UNIQUE_WORK).build();
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_WORK,ExistingWorkPolicy.KEEP,r);
    }
}
