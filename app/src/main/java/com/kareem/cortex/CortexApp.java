package com.kareem.cortex;

import android.app.Application;

/**
 * Process bootstrap must stay intentionally tiny.
 * Heavy DB maintenance/recovery is deferred until the first PRIME surface has drawn.
 */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
        // Never open Cortex DB, run migrations, recovery or backfills on process start.
        // StartupMaintenance is scheduled by the first visible PRIME activity.
    }

    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        try{LocalBrainRuntimePolicy.onTrimMemory(level);}catch(Throwable ignored){}
    }
}
