package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Periodic UsageStats backfill. Accessibility remains the real-time sensor. */
public final class PhoneContextSyncWorker extends Worker {
    public PhoneContextSyncWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){Context c=getApplicationContext();if(!PrivacyPolicy.canCollect(c,"phone_context"))return Result.success();if(!PhoneUsageAccess.has(c))return Result.success();VaultDb db=null;try{db=new VaultDb(c);PhoneContextStore.ensure(db);long since=System.currentTimeMillis()-2L*60L*60L*1000L;PhoneUsageAccess.syncRecent(c,db,since);PhoneContextStore.cleanup(db);return Result.success();}catch(Throwable e){return Result.retry();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
