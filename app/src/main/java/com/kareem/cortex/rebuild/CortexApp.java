package com.kareem.cortex.rebuild;

import android.app.Application;

/** Fresh Cortex process entry: recover any transcript that finished before cognition was applied. */
public final class CortexApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        BrainIntakeQueue.recoverPending(this, null);
    }
}
