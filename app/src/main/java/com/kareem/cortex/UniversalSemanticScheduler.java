package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Background semantic refinement uses only a background-capable local model. Gemini Nano remains foreground-only. */
public final class UniversalSemanticScheduler {
    private UniversalSemanticScheduler(){}
    public static void kick(Context c){
        if(StartupSafetyGate.active()||c==null)return;
        Constraints constraints=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest req=new OneTimeWorkRequest.Builder(UniversalSemanticWorker.class).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag("cortex-universal-semantic").build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork("cortex-universal-semantic",ExistingWorkPolicy.KEEP,req);
        StatefulMeaningScheduler.kick(c);
    }
}
