package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/**
 * Central reliability boundary for Cortex capabilities.
 *
 * Phase A is intentionally behavior-preserving: the existing emergency StartupSafetyGate
 * remains authoritative for startup-reachable data/background work. This supervisor adds
 * per-capability state and circuit-breaker semantics so later recovery can re-enable the
 * deterministic core without implicitly enabling native OCR/ASR/LLM runtimes.
 */
public final class CapabilitySupervisor {
    public static final String VERSION = "capability_supervisor_001";
    private static final String PREF = "cortex_capability_supervisor";
    private static final int DEFAULT_FAILURE_LIMIT = 2;

    public enum Capability {
        CORE_UI,
        DATABASE,
        RAW_NOTIFICATION_CAPTURE,
        DETERMINISTIC_COGNITION,
        BACKGROUND_SCHEDULING,
        OCR_NATIVE,
        ASR_NATIVE,
        LOCAL_LLM_NATIVE,
        PROACTIVE_ACTIONS
    }

    public enum State {
        READY,
        QUARANTINED,
        DEGRADED
    }

    public static final class Status {
        public final Capability capability;
        public final State state;
        public final int consecutiveFailures;
        public final long lastFailureAt;
        public final String reason;

        Status(Capability capability, State state, int failures, long lastFailureAt, String reason) {
            this.capability = capability;
            this.state = state;
            this.consecutiveFailures = Math.max(0, failures);
            this.lastFailureAt = Math.max(0L, lastFailureAt);
            this.reason = reason == null ? "" : reason.trim();
        }

        public boolean allowed() {
            return state == State.READY;
        }
    }

    private CapabilitySupervisor() {}

    /**
     * Current recovery contract. CORE_UI stays usable. Everything that could touch Cortex
     * persistence/background/native execution stays blocked while the emergency gate is active.
     */
    public static boolean allowed(Context context, Capability capability) {
        if (capability == null) return false;
        if (capability == Capability.CORE_UI) return true;
        if (StartupSafetyGate.active()) return false;
        return status(context, capability).allowed();
    }

    public static Status status(Context context, Capability capability) {
        if (capability == null) {
            return new Status(null, State.QUARANTINED, 0, 0L, "unknown capability");
        }
        if (capability == Capability.CORE_UI) {
            return new Status(capability, State.READY, 0, 0L, "core UI is recovery-safe");
        }
        if (StartupSafetyGate.active()) {
            return new Status(capability, State.QUARANTINED, 0, 0L, "global startup recovery quarantine");
        }
        if (context == null) {
            return new Status(capability, State.QUARANTINED, 0, 0L, "context unavailable");
        }

        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = key(capability);
        int failures = p.getInt(key + ".failures", 0);
        long lastFailureAt = p.getLong(key + ".last_failure_at", 0L);
        String reason = p.getString(key + ".reason", "");
        boolean forced = p.getBoolean(key + ".quarantined", false);
        if (forced || failures >= DEFAULT_FAILURE_LIMIT) {
            return new Status(capability, State.QUARANTINED, failures, lastFailureAt,
                    reason.isEmpty() ? "capability circuit breaker open" : reason);
        }
        if (failures > 0) {
            return new Status(capability, State.DEGRADED, failures, lastFailureAt,
                    reason.isEmpty() ? "recent capability failure" : reason);
        }
        return new Status(capability, State.READY, 0, 0L, "ready");
    }

    /** Records a bounded failure signal; two consecutive failures open only this capability's breaker. */
    public static void recordFailure(Context context, Capability capability, Throwable error) {
        if (context == null || capability == null || capability == Capability.CORE_UI) return;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = key(capability);
        int failures = Math.min(1000, p.getInt(key + ".failures", 0) + 1);
        String reason = error == null ? "capability failure" :
                error.getClass().getSimpleName() + ": " + safe(error.getMessage());
        p.edit()
                .putInt(key + ".failures", failures)
                .putLong(key + ".last_failure_at", System.currentTimeMillis())
                .putString(key + ".reason", reason)
                .putBoolean(key + ".quarantined", failures >= DEFAULT_FAILURE_LIMIT)
                .apply();
    }

    /** A verified successful probe closes only the selected capability's breaker. */
    public static void recordHealthy(Context context, Capability capability) {
        if (context == null || capability == null || capability == Capability.CORE_UI) return;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = key(capability);
        p.edit()
                .remove(key + ".failures")
                .remove(key + ".last_failure_at")
                .remove(key + ".reason")
                .remove(key + ".quarantined")
                .apply();
    }

    static String key(Capability capability) {
        return capability.name().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        String s = value == null ? "" : value.trim();
        return s.length() <= 240 ? s : s.substring(0, 240);
    }
}
