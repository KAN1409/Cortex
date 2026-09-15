package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class DiscoveryV3DeepScheduler {
    private static final String NOW="cortex-discovery-v3-deep-now";
    private static final String PERIODIC="cortex-discovery-v3-deep-periodic";
    private DiscoveryV3DeepScheduler(){}

    /** Heavy council stays explicit/manual. */
    public static void kick(Context c){
        if(c==null)return;
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(DiscoveryV3DeepWorker.class)
                .setInputData(new Data.Builder().putBoolean("user_requested",true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(NOW,ExistingWorkPolicy.KEEP,r);
    }

    /** Explicit request joins the single durable council execution instead of cancelling an active model pass. */
    public static void kickFresh(Context c){
        if(c==null)return;
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(DiscoveryV3DeepWorker.class)
                .setInputData(new Data.Builder().putBoolean("user_requested",true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(NOW,ExistingWorkPolicy.KEEP,r);
    }

    /** Startup migration cancels legacy heavy recurrence and refreshes cheap deterministic intelligence. */
    public static void enable(Context c){
        if(c==null)return;
        Context app=c.getApplicationContext();
        WorkManager.getInstance(app).cancelUniqueWork(PERIODIC);
        WorkDeterministicInsightScheduler.kick(app);
    }
}
