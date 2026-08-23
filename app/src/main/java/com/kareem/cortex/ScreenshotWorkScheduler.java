package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Durable fast screenshot extraction queue. Strong visual understanding runs separately after local triage. */
public final class ScreenshotWorkScheduler {
    static final String UNIQUE="cortex-screenshot-analysis";
    private ScreenshotWorkScheduler(){}

    private static OneTimeWorkRequest request(){Constraints constraints=new Constraints.Builder().setRequiresStorageNotLow(true).build();return new OneTimeWorkRequest.Builder(ScreenshotAnalysisWorker.class).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).addTag(UNIQUE).build();}

    public static void kick(Context c){Context app=c.getApplicationContext();WorkManager wm=WorkManager.getInstance(app);wm.enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,request());
        // v47: stop the old blind 3-pass OCR backfill. Keep its stored evidence, but do not spend more battery on it.
        wm.cancelUniqueWork("cortex-screenshot-deep-ocr");wm.cancelUniqueWork("cortex-screenshot-deep-ocr-periodic");
        VisualIntelligenceScheduler.enablePeriodic(app);VisualIntelligenceScheduler.kick(app);
    }

    static void continueChain(Context c){WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.APPEND_OR_REPLACE,request());}
}
