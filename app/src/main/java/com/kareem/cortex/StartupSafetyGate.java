package com.kareem.cortex;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cold-start quarantine.
 *
 * The gate starts closed on every process launch so provider/startup-reachable code cannot open
 * Cortex databases, schedule WorkManager chains, or enter native runtimes before the launcher is
 * stable. SafeCoreRuntime is the only production path allowed to release it, and only after the
 * delayed SQLite health probe succeeds. Per-capability circuit breakers remain responsible for
 * isolating a native component again if its own health probe or real execution fails.
 */
public final class StartupSafetyGate {
    public static final String VERSION = "startup_quarantine_002";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(true);

    private StartupSafetyGate() {}

    public static boolean active() {
        return ACTIVE.get();
    }

    /** Called only after the launcher settle window and verified database health probe. */
    static void releaseAfterSafeCore() {
        ACTIVE.set(false);
    }

    static void resetForTests() {
        ACTIVE.set(true);
    }
}
