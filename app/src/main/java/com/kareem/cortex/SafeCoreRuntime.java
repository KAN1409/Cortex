package com.kareem.cortex;

import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Staged recovery bridge between a visually stable launcher and the safe Java/SQLite Cortex core.
 *
 * The emergency StartupSafetyGate deliberately remains active in recovery builds so every legacy
 * native entry point that already checks it stays quarantined. Only explicitly classified safe
 * capabilities may pass through CapabilitySupervisor after this runtime completes a post-UI DB
 * probe. This avoids turning one global switch off and accidentally re-enabling OCR/ASR/LLM code.
 */
public final class SafeCoreRuntime {
    public static final String VERSION = "safe_core_runtime_001";
    private static final long POST_RESUME_SETTLE_MS = 1200L;

    public enum Phase {
        COLD_START,
        UI_STABLE_PROBING,
        CORE_READY,
        CORE_FAILED
    }

    private static final AtomicBoolean ARMED = new AtomicBoolean(false);
    private static final AtomicReference<Phase> PHASE = new AtomicReference<>(Phase.COLD_START);

    private SafeCoreRuntime() {}

    public static Phase phase() {
        return PHASE.get();
    }

    public static boolean ready() {
        return PHASE.get() == Phase.CORE_READY;
    }

    /**
     * Called only from the launcher after resume. The delay protects first-frame usability; the
     * database probe then runs off the main thread. No native AI/OCR/ASR runtime is touched here.
     */
    public static void armAfterLauncherResume(Context context) {
        if (context == null || !ARMED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> beginProbe(app), POST_RESUME_SETTLE_MS);
    }

    private static void beginProbe(Context app) {
        if (!PHASE.compareAndSet(Phase.COLD_START, Phase.UI_STABLE_PROBING)) return;
        Thread probe = new Thread(() -> probeSafeCore(app), "cortex-safe-core-probe");
        probe.setPriority(Thread.NORM_PRIORITY - 1);
        probe.start();
    }

    private static void probeSafeCore(Context app) {
        VaultDb db = null;
        try {
            db = new VaultDb(app);
            Cursor c = db.getReadableDatabase().rawQuery("SELECT 1", null);
            boolean ok = c.moveToFirst() && c.getInt(0) == 1;
            c.close();
            if (!ok) throw new IllegalStateException("database health probe returned no row");

            CapabilitySupervisor.recordHealthy(app, CapabilitySupervisor.Capability.DATABASE);
            PHASE.set(Phase.CORE_READY);

            // Restore only the deterministic projection/shadow drain. Semantic local-LLM work,
            // OCR, ASR and proactive actions remain quarantined by their own capability policy.
            try {
                StatefulMeaningScheduler.kick(app);
            } catch (Throwable t) {
                CapabilitySupervisor.recordFailure(app, CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING, t);
            }

            // The listener may have connected while cold-start quarantine was active. A rebind
            // gives it one fresh active-notification snapshot after the safe core is ready.
            try {
                NotificationListenerService.requestRebind(new ComponentName(app, NotificationCaptureService.class));
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            CapabilitySupervisor.recordFailure(app, CapabilitySupervisor.Capability.DATABASE, t);
            PHASE.set(Phase.CORE_FAILED);
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean safeCapability(CapabilitySupervisor.Capability capability) {
        return capability == CapabilitySupervisor.Capability.DATABASE
                || capability == CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE
                || capability == CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION
                || capability == CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING;
    }

    static void resetForTests() {
        ARMED.set(false);
        PHASE.set(Phase.COLD_START);
    }

    static void forceReadyForTests() {
        ARMED.set(true);
        PHASE.set(Phase.CORE_READY);
    }
}
