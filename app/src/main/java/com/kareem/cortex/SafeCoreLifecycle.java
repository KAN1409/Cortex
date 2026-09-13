package com.kareem.cortex;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight lifecycle hook.
 *
 * Runtime startup must follow the actual first resumed Cortex surface, not a hard-coded activity
 * class. The launcher is currently NowActivity; share/deep-link entry points may differ. Safe core
 * remains idempotent and owns all post-resume recovery once the UI is stable.
 */
public final class SafeCoreLifecycle implements Application.ActivityLifecycleCallbacks {
    private final AtomicBoolean firstResumeHandled=new AtomicBoolean(false);
    SafeCoreLifecycle() {}

    public static void install(Application app) {
        if (app != null) app.registerActivityLifecycleCallbacks(new SafeCoreLifecycle());
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity == null || !firstResumeHandled.compareAndSet(false,true)) return;
        SafeCoreRuntime.armAfterLauncherResume(activity);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
