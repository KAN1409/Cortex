package com.kareem.cortex;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class NexusScheduler {
    private static final String NOW="cortex-nexus-refresh-now";
    private static final String PERIODIC="cortex-nexus-refresh-periodic";
    private NexusScheduler(){}

    public static void enable(Context context){
        if(context==null||StartupSafetyGate.active())return;Context app=context.getApplicationContext();
        WorkManager wm=WorkManager.getInstance(app);
        wm.enqueueUniqueWork(NOW,ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(NexusWorker.class).build());
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(NexusWorker.class,6,TimeUnit.HOURS).build();
        wm.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,periodic);
    }

    /** Coalesces bursts of notifications/captures into one NEXUS refresh instead of restart-spamming WorkManager. */
    public static void kick(Context context){
        if(context==null||StartupSafetyGate.active())return;
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                NOW,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(NexusWorker.class).build());
    }
}
