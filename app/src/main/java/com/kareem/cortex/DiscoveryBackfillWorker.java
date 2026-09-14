package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Incrementally adopts existing analyzed evidence into Discovery without blocking UI. */
public final class DiscoveryBackfillWorker extends Worker {
    public static final String UNIQUE="cortex-discovery-backfill-v2";
    public DiscoveryBackfillWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    @NonNull @Override public Result doWork(){
        VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());
            DiscoverySchema.ensure(db.getWritableDatabase());
            int total=0;
            for(int i=0;i<8;i++){
                if(isStopped())break;
                int n=DiscoveryEngine.backfill(db,32);
                total+=n;
                if(n<32)break;
            }
            if(hasUnadopted(db)){
                enqueue(getApplicationContext(),10);
            }
            return Result.success(new Data.Builder().putInt("processed",total).build());
        }catch(Throwable t){
            return getRunAttemptCount()<3?Result.retry():Result.failure();
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private boolean hasUnadopted(VaultDb db){
        android.database.Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT 1 FROM knowledge_items k LEFT JOIN discovery_annotations d ON d.item_id=k.id "+
                "WHERE k.status='analyzed' AND d.item_id IS NULL LIMIT 1",null);
        boolean yes=c.moveToFirst();c.close();return yes;
    }

    public static void enqueue(Context context){enqueue(context,0);}
    private static void enqueue(Context context,long delaySeconds){
        if(context==null)return;
        Constraints constraints=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
        OneTimeWorkRequest.Builder b=new OneTimeWorkRequest.Builder(DiscoveryBackfillWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES);
        if(delaySeconds>0)b.setInitialDelay(delaySeconds,TimeUnit.SECONDS);
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE,delaySeconds>0?ExistingWorkPolicy.APPEND_OR_REPLACE:ExistingWorkPolicy.KEEP,b.build());
    }
}
