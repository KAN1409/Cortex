package com.kareem.cortex.rebuild;

import android.app.Application;

/** Fresh Cortex process entry: recover pending cognition and expire short-lived test evidence. */
public final class CortexApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try (CortexDb db = new CortexDb(this)) { BrainStore.purgeExpiredShortEvidence(db); } catch (Throwable ignored) {}
        BrainIntakeQueue.recoverPending(this, null);
    }
}
