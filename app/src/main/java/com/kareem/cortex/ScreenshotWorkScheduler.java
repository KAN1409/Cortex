package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Durable single-file screenshot OCR queue. WorkManager restarts it after process death/reboot. */
public final class ScreenshotWorkScheduler {
    static final String UNIQUE="cortex-screenshot-analysis";
    private ScreenshotWorkScheduler(){}

    private static OneTimeWorkRequest request(){
        Constraints constraints=new Constraints.Builder().setRequiresStorageNotLow(true).build();
        return new OneTimeWorkRequest.Builder(ScreenshotAnalysisWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS)
                .addTag(UNIQUE)
                .build();
    }

    public static void kick(Context c){
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,request());
    }

    static void continueChain(Context c){
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.APPEND_OR_REPLACE,request());
    }
}
