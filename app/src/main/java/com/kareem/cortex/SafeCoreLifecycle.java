package com.kareem.cortex;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight lifecycle hook.
 *
 * Cortex used to hard-code InputActivity as the only surface allowed to arm the runtime. The real
 * launcher is NowActivity, so normal launches could permanently skip SafeCoreRuntime and one-time
 * migrations. Arm from the first resumed Cortex activity instead. SafeCoreRuntime is itself
 * idempotent, and this additional guard keeps lifecycle work strictly one-shot per process.
 */
public final class SafeCoreLifecycle implements Application.ActivityLifecycleCallbacks {
    private final AtomicBoolean firstResumeHandled=new AtomicBoolean(false);
    private SafeCoreLifecycle() {}

    public static void install(Application app) {
        if (app != null) app.registerActivityLifecycleCallbacks(new SafeCoreLifecycle());
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity == null || !firstResumeHandled.compareAndSet(false,true)) return;
        SafeCoreRuntime.armAfterLauncherResume(activity);
        DocumentIntelligenceMigration.runAfterLauncher(activity.getApplicationContext());
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
