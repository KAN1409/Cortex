package com.kareem.cortex;

import android.app.Application;

/**
 * Process bootstrap must stay intentionally tiny.
 * Heavy DB maintenance/recovery is deferred until the first visible Cortex surface has drawn.
 */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
        ProcessExitRecorder.captureHistoricalExit(this);
        // This is intentionally limited to idempotent persisted-schema compatibility repair.
        // It never deletes, rebuilds or clears user data.
        DatabaseCompatibilityRepair.repairExistingDatabase(this);
    }
}
