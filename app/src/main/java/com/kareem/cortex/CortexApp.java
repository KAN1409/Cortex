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
        // This is intentionally limited to an idempotent persisted-schema compatibility repair.
        // It does not create the DB, rebuild tables, backfill semantic data, or run heavy recovery.
        DatabaseCompatibilityRepair.repairExistingDatabase(this);
    }
}
