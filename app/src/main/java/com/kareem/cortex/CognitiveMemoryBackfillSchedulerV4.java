package com.kareem.cortex;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Schedules bounded V4 migration as maintenance rather than capture-path work. */
public final class CognitiveMemoryBackfillSchedulerV4 {
    /**
     * Version the unique work name whenever scheduling semantics change.
     *
     * <p>V1 used a battery-not-low constraint. A pending V1 WorkSpec can survive an APK update,
     * and ExistingWorkPolicy.KEEP would preserve that stale constraint forever. V2 deliberately
     * uses a fresh unique name so installing the repaired build always gets the repaired policy.</p>
     */
    private static final String UNIQUE="cortex-v4-memory-backfill-v2";
    private CognitiveMemoryBackfillSchedulerV4(){}

    public static void schedule(Context context){
        if(context==null)return;
        Context app=context.getApplicationContext();
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(CognitiveMemoryBackfillWorkerV4.class)
                // This is bounded local SQLite work. Do not gate canonical-history migration on
                // battery state; the Worker yields between bounded runs and resumes via retry.
                .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS)
                .addTag(UNIQUE)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,work);
    }
}
