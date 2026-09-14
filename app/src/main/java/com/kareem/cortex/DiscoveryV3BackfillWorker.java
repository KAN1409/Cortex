package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public final class DiscoveryV3BackfillWorker extends Worker {
    public static final String UNIQUE="cortex-discovery-v3-backfill";
    public DiscoveryV3BackfillWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        VaultDb db=null;try{db=new VaultDb(getApplicationContext());int total=0;
            for(int i=0;i<6&&!isStopped();i++){int n=DiscoveryV3Engine.backfill(db,48);total+=n;if(n<48)break;}
            if(hasMore(db))enqueue(getApplicationContext(),15);
            return Result.success(new Data.Builder().putInt("processed",total).build());
        }catch(Throwable t){return getRunAttemptCount()<3?Result.retry():Result.failure();}
        finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }
    private boolean hasMore(VaultDb db){android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM knowledge_items k LEFT JOIN discovery_v3_evidence e ON e.item_id=k.id WHERE k.status='analyzed' AND e.item_id IS NULL LIMIT 1",null);boolean yes=c.moveToFirst();c.close();return yes;}
    public static void enqueue(Context c){enqueue(c,0);}
    private static void enqueue(Context c,long delay){OneTimeWorkRequest.Builder b=new OneTimeWorkRequest.Builder(DiscoveryV3BackfillWorker.class).setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES);if(delay>0)b.setInitialDelay(delay,TimeUnit.SECONDS);WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork(UNIQUE,delay>0?ExistingWorkPolicy.APPEND_OR_REPLACE:ExistingWorkPolicy.KEEP,b.build());}
}
