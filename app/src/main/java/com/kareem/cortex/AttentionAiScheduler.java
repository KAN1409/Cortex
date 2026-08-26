package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;

/** Coalesces attention refinement work; never blocks UI. */
public final class AttentionAiScheduler {
    private static final String UNIQUE="cortex-attention-ai";
    private AttentionAiScheduler(){}
    public static void kick(Context context){
        if(context==null||!ExternalBrainProvider.configured(context))return;Context app=context.getApplicationContext();
        Constraints c=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest w=new OneTimeWorkRequest.Builder(AttentionAiWorker.class).setConstraints(c).addTag(UNIQUE).build();
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE,ExistingWorkPolicy.KEEP,w);
    }
}
