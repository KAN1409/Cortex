package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Schedules low-frequency teacher sync while keeping all real-time ranking local. */
public final class CortexChatGptBridgeScheduler {
    private static final String PERIODIC="cortex-chatgpt-policy-periodic",ONCE="cortex-chatgpt-policy-now";
    private CortexChatGptBridgeScheduler(){}
    public static void enable(Context c){
        Context app=c.getApplicationContext();if(!CortexChatGptBridgeConfig.enabled(app))return;
        Constraints net=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest p=new PeriodicWorkRequest.Builder(CortexChatGptBridgeWorker.class,30,TimeUnit.MINUTES).setConstraints(net).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,p);
        kick(app);
    }
    public static void kick(Context c){
        Context app=c.getApplicationContext();if(!CortexChatGptBridgeConfig.enabled(app))return;
        Constraints net=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(CortexChatGptBridgeWorker.class).setConstraints(net).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(app).enqueueUniqueWork(ONCE,ExistingWorkPolicy.REPLACE,r);
    }
}
