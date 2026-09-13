package com.kareem.cortex;

import android.app.Application;
import android.content.Context;

/**
 * Cortex process bootstrap.
 *
 * Keep Application startup intentionally inert. AndroidX owns provider-first WorkManager
 * initialization. Cortex installs only crash recording and a lightweight lifecycle observer here;
 * no database, WorkManager scheduling, model, JNI or heavyweight maintenance runs in onCreate().
 */
public final class CortexApp extends Application {
    private static volatile Context appContext;
    public static Context context(){return appContext;}

    @Override public void onCreate(){
        super.onCreate();
        appContext=getApplicationContext();
        CrashRecorder.install(this);
        SafeCoreLifecycle.install(this);
    }
}
