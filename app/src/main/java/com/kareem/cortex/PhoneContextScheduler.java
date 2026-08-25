package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class PhoneContextScheduler {
    private static final String ONCE="cortex-phone-context-sync";
    private static final String PERIODIC="cortex-phone-context-periodic";
    private PhoneContextScheduler(){}

    public static void schedule(Context c){
        if(c==null)return;Context app=c.getApplicationContext();
        try{WorkManager wm=WorkManager.getInstance(app);OneTimeWorkRequest once=new OneTimeWorkRequest.Builder(PhoneContextSyncWorker.class).addTag(ONCE).build();wm.enqueueUniqueWork(ONCE,ExistingWorkPolicy.REPLACE,once);PeriodicWorkRequest p=new PeriodicWorkRequest.Builder(PhoneContextSyncWorker.class,30,TimeUnit.MINUTES).addTag(PERIODIC).build();wm.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,p);}catch(Throwable ignored){}
    }
}
