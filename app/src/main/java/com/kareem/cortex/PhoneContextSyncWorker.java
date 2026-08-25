package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Periodic local phone-context sync. Accessibility remains the real-time sensor. */
public final class PhoneContextSyncWorker extends Worker {
    public PhoneContextSyncWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){Context c=getApplicationContext();if(!PrivacyPolicy.canCollect(c,"phone_context"))return Result.success();VaultDb db=null;try{db=new VaultDb(c);PhoneContextStore.ensure(db);long since=System.currentTimeMillis()-2L*60L*60L*1000L;if(PhoneUsageAccess.has(c))PhoneUsageAccess.syncRecent(c,db,since);if(ShizukuContextBridge.granted())ShizukuContextBridge.captureProcessSnapshot(c,db);PhoneContextStore.cleanup(db);return Result.success();}catch(Throwable e){return Result.retry();}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
}
