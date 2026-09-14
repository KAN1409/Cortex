package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class DiscoveryV3DeepScheduler {
    private static final String NOW="cortex-discovery-v3-deep-now";
    private static final String PERIODIC="cortex-discovery-v3-deep-periodic";
    private DiscoveryV3DeepScheduler(){}

    public static void kick(Context c){
        if(c==null)return;
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(DiscoveryV3DeepWorker.class)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(NOW,ExistingWorkPolicy.KEEP,r);
    }

    public static void enable(Context c){
        if(c==null)return;
        PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(DiscoveryV3DeepWorker.class,30,TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,r);
    }
}
