package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** New screenshots only by default: charging + network. Old library stays local until user chooses to analyze it. */
public final class VisualIntelligenceScheduler {
    static final String UNIQUE="cortex-visual-intelligence";
    private VisualIntelligenceScheduler(){}
    static Constraints constraints(){return new Constraints.Builder().setRequiresCharging(true).setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build();}
    static OneTimeWorkRequest one(Context c){return one(c,0);}
    static OneTimeWorkRequest one(Context c,long requestedDelayMs){OneTimeWorkRequest.Builder b=new OneTimeWorkRequest.Builder(VisualIntelligenceWorker.class).setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).addTag(UNIQUE);long delay=Math.max(Math.max(0,requestedDelayMs),VisionRateLimitGate.remainingMs(c));if(delay>0)b.setInitialDelay(delay,TimeUnit.MILLISECONDS);return b.build();}
    public static void kick(Context c){Context app=c.getApplicationContext();VisualInsightStore.backgroundStart(app);WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,one(app));}
    static void continueChain(Context c){continueChain(c,0);}
    static void continueChain(Context c,long delayMs){Context app=c.getApplicationContext();WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.APPEND_OR_REPLACE,one(app,delayMs));}
    public static void enablePeriodic(Context c){Context app=c.getApplicationContext();VisualInsightStore.backgroundStart(app);PeriodicWorkRequest p=new PeriodicWorkRequest.Builder(VisualIntelligenceWorker.class,6,TimeUnit.HOURS).setConstraints(constraints()).addTag(UNIQUE+"-periodic").build();WorkManager.getInstance(app).enqueueUniquePeriodicWork(UNIQUE+"-periodic",ExistingPeriodicWorkPolicy.KEEP,p);}
}
