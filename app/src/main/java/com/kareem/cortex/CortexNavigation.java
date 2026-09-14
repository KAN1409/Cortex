package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;

/**
 * Central navigation bridge from semantic product destinations to current Activity implementations.
 * Activities are implementation details; callers should prefer destination IDs.
 */
public final class CortexNavigation {
    private CortexNavigation() {}

    public static Intent primaryIntent(Activity from, String destinationId) {
        return new Intent(from, activityFor(destinationId))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /** Compatibility bridge for detail/legacy callers during incremental migration. */
    public static Intent primaryIntent(Activity from, Class<?> target) {
        return new Intent(from, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static Intent inputIntent(Activity from) {
        return new Intent(from, InputActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static void openPrimary(Activity from, String destinationId) {
        if (from == null || from.isFinishing()) return;
        Class<?> target = activityFor(destinationId);
        if (from.getClass().equals(target)) return;
        from.startActivity(primaryIntent(from, destinationId));
        from.finish();
    }

    /** Compatibility bridge for non-canonical callers. New primary navigation must use a destination ID. */
    public static void openPrimary(Activity from, Class<?> target) {
        if (from == null || target == null || from.isFinishing()) return;
        if (from.getClass().equals(target)) return;
        from.startActivity(primaryIntent(from, target));
        from.finish();
    }

    public static void openInput(Activity from) {
        if (from == null || from.isFinishing()) return;
        if (from instanceof InputActivity) return;
        from.startActivity(inputIntent(from));
    }

    public static Class<?> activityFor(String destinationId) {
        CortexDestinationRegistry.Destination destination = CortexDestinationRegistry.require(destinationId);
        if (CortexDestinationRegistry.NOW.equals(destination.destinationId)) return NowActivity.class;
        if (CortexDestinationRegistry.WORK.equals(destination.destinationId)) return WorkWorkspaceActivity.class;
        if (CortexDestinationRegistry.MEMORY.equals(destination.destinationId)) return VisualMemoryActivity.class;
        if (CortexDestinationRegistry.CAPTURE.equals(destination.destinationId)) return CaptureHubActivity.class;
        if (CortexDestinationRegistry.SETTINGS.equals(destination.destinationId)) return SettingsActivity.class;
        if (CortexDestinationRegistry.SYSTEM_HEALTH.equals(destination.destinationId)) return CortexAuditActivity.class;
        throw new IllegalArgumentException("No implementation mapped for destination " + destination.destinationId);
    }
}
