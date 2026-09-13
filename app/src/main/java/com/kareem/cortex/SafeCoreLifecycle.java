package com.kareem.cortex;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Lightweight lifecycle hook: no DB/WorkManager/native work occurs until InputActivity is resumed. */
public final class SafeCoreLifecycle implements Application.ActivityLifecycleCallbacks {
    private SafeCoreLifecycle() {}

    public static void install(Application app) {
        if (app != null) app.registerActivityLifecycleCallbacks(new SafeCoreLifecycle());
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof InputActivity) {
            SafeCoreRuntime.armAfterLauncherResume(activity);
            DocumentIntelligenceMigration.runAfterLauncher(activity.getApplicationContext());
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
