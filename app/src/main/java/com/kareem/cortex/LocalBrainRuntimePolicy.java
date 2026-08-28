package com.kareem.cortex;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/** Device-safety policy for the always-available local brain. Capture never depends on this gate. */
public final class LocalBrainRuntimePolicy {
    private LocalBrainRuntimePolicy() {}

    public static boolean thermalAllowsInference(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 29) return true;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return true;
            return pm.getCurrentThermalStatus() < PowerManager.THERMAL_STATUS_SEVERE;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static int thermalStatus(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm == null ? -1 : pm.getCurrentThermalStatus();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** Keep the model warm normally; under real memory pressure release only after the idle horizon. */
    public static boolean onTrimMemory(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            return LocalLlmBridge.releaseCachedIfIdle(LocalBrainConfig.IDLE_WARM_MS);
        }
        return false;
    }
}
