package com.kareem.cortex;

import android.app.Application;

/**
 * Process bootstrap must stay deliberately inert.
 *
 * Native/process-exit inspection and SQLite compatibility work are NOT allowed from Application.onCreate().
 * A previous build performed both here; if either platform/native path is unstable on a specific device,
 * that turns a recoverable feature crash into an unrecoverable startup crash loop.
 *
 * Only the Java uncaught-exception recorder is installed at process start because it is file-only and has
 * no database, model, JNI, provider or ActivityManager trace dependency.
 */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
    }
}
