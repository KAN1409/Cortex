package com.kareem.cortex;

import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public final class KnowledgeV2Scheduler {
    public static final String UNIQUE_CHAIN="cortex-kv2-full-pipeline";
    private KnowledgeV2Scheduler(){}

    public static void enqueue(Context context){
        if(context==null||StartupSafetyGate.active())return;
        Context app=context.getApplicationContext();
        OneTimeWorkRequest extraction=new OneTimeWorkRequest.Builder(KnowledgeV2ExtractionWorker.class).build();
        OneTimeWorkRequest enrichment=new OneTimeWorkRequest.Builder(KnowledgeV2EnrichmentWorker.class).build();
        WorkManager.getInstance(app)
                .beginUniqueWork(UNIQUE_CHAIN,ExistingWorkPolicy.KEEP,extraction)
                .then(enrichment)
                .enqueue();
    }
}
