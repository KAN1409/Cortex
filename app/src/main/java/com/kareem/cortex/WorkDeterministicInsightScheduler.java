package com.kareem.cortex;

import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/** Coalesces cheap deterministic Work insight refreshes. */
public final class WorkDeterministicInsightScheduler {
    private static final String UNIQUE="cortex-work-deterministic-insights";
    private WorkDeterministicInsightScheduler(){}
    public static void kick(Context context){
        if(context==null)return;
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(WorkDeterministicInsightWorker.class).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,request);
    }
}
