package com.kareem.cortex;

import android.app.Application;

/**
 * Cortex process bootstrap.
 *
 * Keep Application startup intentionally inert. WorkManager uses its default AndroidX Startup
 * ContentProvider initializer so persisted SystemJobService jobs always see an initialized
 * WorkManager before any service is created. Do not manually initialize WorkManager here and do not
 * implement Configuration.Provider unless the default initializer is deliberately removed again.
 *
 * No database, model, JNI, process-exit trace or heavyweight maintenance is started here.
 */
public final class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
    }
}
