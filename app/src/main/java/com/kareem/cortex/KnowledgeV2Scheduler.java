package com.kareem.cortex;

import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OneTimeWorkRequestBuilder;
import androidx.work.WorkManager;

public final class KnowledgeV2Scheduler {
    private KnowledgeV2Scheduler(){}

    public static void enqueue(Context context){
        if(context==null)return;
        OneTimeWorkRequest request=new OneTimeWorkRequestBuilder<KnowledgeV2ExtractionWorker>().build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                KnowledgeV2ExtractionWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
        );
    }
}
