package com.kareem.cortex;

/**
 * Emergency cold-start quarantine.
 *
 * While active, startup-reachable code must not initialize WorkManager, open the Cortex database,
 * or enter OCR/ASR/LLM native runtimes. This is deliberately a compile-time constant so recovery
 * behavior cannot depend on preferences or database state that may itself be damaged.
 */
public final class StartupSafetyGate {
    public static final String VERSION = "startup_quarantine_001";
    private static final boolean ACTIVE = true;

    private StartupSafetyGate() {}

    public static boolean active() {
        return ACTIVE;
    }
}
