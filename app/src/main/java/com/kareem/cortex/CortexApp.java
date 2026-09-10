package com.kareem.cortex;

import android.app.Application;

/**
 * Cortex process bootstrap.
 *
 * Keep Application startup intentionally inert. Background scheduling is initialized by AndroidX
 * before persisted jobs can recreate their services. Cortex does not own a second bootstrap path.
 *
 * No database, model, JNI, process-exit trace or heavyweight maintenance is started here.
 */
public final class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
    }
}
