package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Durable fast screenshot OCR queue. Deep three-pass enrichment runs separately while charging. */
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
        Context app=c.getApplicationContext();
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,request());
        ScreenshotDeepOcrScheduler.enablePeriodic(app);
        ScreenshotDeepOcrScheduler.kick(app);
    }

    static void continueChain(Context c){
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.APPEND_OR_REPLACE,request());
    }
}
