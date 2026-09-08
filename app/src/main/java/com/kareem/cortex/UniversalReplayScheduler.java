package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import androidx.work.*;

/** Schedules versioned semantic replay from immutable raw evidence. */
public final class UniversalReplayScheduler {
    private UniversalReplayScheduler(){}
    public static long enqueue(Context c,long fromAt,long toAt){Context app=c.getApplicationContext();VaultDb db=new VaultDb(app);try{UniversalEventStore.ensure(db.getWritableDatabase());long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("from_at",Math.max(0,fromAt));v.put("to_at",toAt>0?toAt:now);v.put("processor_version",UniversalEventStore.PROCESSOR_VERSION);v.put("state","queued");v.put("created_at",now);v.put("updated_at",now);long id=db.getWritableDatabase().insertOrThrow("ue_reprocess_requests",null,v);Data d=new Data.Builder().putLong("request_id",id).build();OneTimeWorkRequest w=new OneTimeWorkRequest.Builder(UniversalReplayWorker.class).setInputData(d).addTag("cortex-universal-replay").build();WorkManager.getInstance(app).enqueueUniqueWork("cortex-universal-replay-"+id,ExistingWorkPolicy.KEEP,w);return id;}finally{db.close();}}
}
