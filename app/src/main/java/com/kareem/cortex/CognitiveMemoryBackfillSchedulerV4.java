package com.kareem.cortex;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Schedules bounded V4 migration as maintenance rather than capture-path work. */
public final class CognitiveMemoryBackfillSchedulerV4 {
    private static final String UNIQUE="cortex-v4-memory-backfill";
    private CognitiveMemoryBackfillSchedulerV4(){}

    public static void schedule(Context context){
        if(context==null)return;Context app=context.getApplicationContext();
        Constraints constraints=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(CognitiveMemoryBackfillWorkerV4.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS)
                .addTag(UNIQUE)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,work);
    }
}
