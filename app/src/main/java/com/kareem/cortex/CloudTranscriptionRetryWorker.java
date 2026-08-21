package com.kareem.cortex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Network-constrained retry for voice recordings that could not reach cloud ASR. */
public final class CloudTranscriptionRetryWorker extends Worker {
    private static final String KEY_ITEM_ID="item_id";
    private static final String WORK_PREFIX="cortex-cloud-transcription-";

    public CloudTranscriptionRetryWorker(@NonNull Context appContext,@NonNull WorkerParameters params){super(appContext,params);}

    static void enqueue(Context context,long itemId){
        if(itemId<=0)return;
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data input=new Data.Builder().putLong(KEY_ITEM_ID,itemId).build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(CloudTranscriptionRetryWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
                .addTag(WORK_PREFIX+itemId)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_PREFIX+itemId,ExistingWorkPolicy.REPLACE,request);
    }

    @NonNull @Override public Result doWork(){
        long itemId=getInputData().getLong(KEY_ITEM_ID,-1L);
        if(itemId<=0)return Result.failure();
        VaultDb db=new VaultDb(getApplicationContext());
        try{
            KnowledgeItem item=db.getById(itemId);
            if(item==null)return Result.failure();
            if("analyzed".equals(item.status))return Result.success();
            if(item.attachmentPath==null||item.attachmentPath.isEmpty())return Result.failure();
            File audio=new File(item.attachmentPath);
            if(!audio.exists())return Result.failure();

            db.markAnalyzing(itemId);
            TranscriptResult transcript=CloudAudioTranscriber.transcribeBlocking(audio);
            AnalysisResult analysis=AudioAnalyzer.toAnalysisResult(transcript);
            db.applyAnalysis(itemId,analysis);
            AudioStore.save(db,itemId,analysis);
            ChatGptDebugReview.notifyReady(getApplicationContext(),itemId);
            return Result.success();
        }catch(CloudAudioTranscriber.RetryableException e){
            db.markFailed(itemId,"Cloud ASR waiting for network/provider — "+safe(e));
            return getRunAttemptCount()>=8?Result.failure():Result.retry();
        }catch(Exception e){
            db.markFailed(itemId,"Cloud ASR failed — "+safe(e));
            return Result.failure();
        }finally{
            db.close();
        }
    }

    private static String safe(Throwable e){
        if(e==null)return "unknown";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;
    }
}