package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Background semantic refinement uses the native local model and stays quarantined in recovery. */
public final class UniversalSemanticScheduler {
    private UniversalSemanticScheduler(){}
    public static void kick(Context c){
        if(StartupSafetyGate.active()||c==null)return;
        Context app=c.getApplicationContext();
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.DATABASE))return;
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING))return;
        if(!CapabilitySupervisor.allowed(app,CapabilitySupervisor.Capability.LOCAL_LLM_NATIVE))return;
        Constraints constraints=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest req=new OneTimeWorkRequest.Builder(UniversalSemanticWorker.class).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag("cortex-universal-semantic").build();
        WorkManager.getInstance(app).enqueueUniqueWork("cortex-universal-semantic",ExistingWorkPolicy.KEEP,req);
        StatefulMeaningScheduler.kick(app);
    }
}
