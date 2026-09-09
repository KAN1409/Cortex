package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;

/**
 * Central navigation contract for Cortex primary destinations.
 * Primary destinations behave like tabs: they do not accumulate a deep back stack.
 * The center + remains an action surface and is intentionally not a primary destination.
 */
public final class CortexNavigation {
    private CortexNavigation() {}

    public static Intent primaryIntent(Activity from, Class<?> target) {
        return new Intent(from, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static Intent inputIntent(Activity from) {
        return new Intent(from, InputActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static void openPrimary(Activity from, Class<?> target) {
        if (from == null || target == null || from.isFinishing()) return;
        if (from.getClass().equals(target)) return;
        from.startActivity(primaryIntent(from, target));
        // Bottom-nav destinations are peers, not a history chain. Removing the caller
        // prevents Input/Now/Brief/Capture/Brain from unexpectedly resurfacing later.
        from.finish();
    }

    public static void openInput(Activity from) {
        if (from == null || from.isFinishing()) return;
        if (from instanceof InputActivity) return;
        from.startActivity(inputIntent(from));
    }
}
