package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class ScreenshotDeepOcrScheduler {
    static final String UNIQUE="cortex-screenshot-deep-ocr";
    private ScreenshotDeepOcrScheduler(){}
    static Constraints constraints(){return new Constraints.Builder().setRequiresCharging(true).setRequiresStorageNotLow(true).build();}
    static OneTimeWorkRequest one(){return new OneTimeWorkRequest.Builder(ScreenshotDeepOcrWorker.class).setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag(UNIQUE).build();}
    public static void kick(Context c){WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,one());}
    static void continueChain(Context c){WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.APPEND_OR_REPLACE,one());}
    public static void enablePeriodic(Context c){PeriodicWorkRequest p=new PeriodicWorkRequest.Builder(ScreenshotDeepOcrWorker.class,12,TimeUnit.HOURS).setConstraints(constraints()).addTag(UNIQUE+"-periodic").build();WorkManager.getInstance(c.getApplicationContext()).enqueueUniquePeriodicWork(UNIQUE+"-periodic",ExistingPeriodicWorkPolicy.KEEP,p);}
}
