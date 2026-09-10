package com.kareem.cortex;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

/**
 * Process bootstrap stays deliberately inert while still providing WorkManager's configuration.
 *
 * The default WorkManager AndroidX Startup initializer is intentionally removed in the manifest so
 * persisted JobScheduler jobs cannot race application bootstrap. Because SystemJobService may still
 * be recreated by Android for persisted jobs, CortexApp must implement Configuration.Provider so
 * WorkManager can initialize safely on demand before SystemJobService is created.
 *
 * No database, model, JNI, process-exit trace or heavyweight maintenance is started here.
 */
public final class CortexApp extends Application implements Configuration.Provider {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
    }

    @NonNull
    @Override public Configuration getWorkManagerConfiguration(){
        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }
}
