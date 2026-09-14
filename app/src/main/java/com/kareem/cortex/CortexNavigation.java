package com.kareem.cortex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * Central navigation bridge from semantic product destinations to implementation details.
 * Primary product navigation stays inside the canonical Compose shell.
 */
public final class CortexNavigation {
    public static final String EXTRA_DESTINATION_ID = "cortex_destination_id";
    public static final String EXTRA_OPEN_DOCK = "cortex_open_dock";

    private CortexNavigation() {}

    public static Intent primaryIntent(Context from, String destinationId) {
        CortexDestinationRegistry.Destination destination = CortexDestinationRegistry.require(destinationId);
        Intent intent = new Intent(from, activityFor(destination.destinationId))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (destination.category == CortexDestinationRegistry.Category.PRIMARY) {
            intent.putExtra(EXTRA_DESTINATION_ID, destination.destinationId);
        }
        return intent;
    }

    /** Compatibility bridge for detail/legacy callers during incremental migration. */
    public static Intent primaryIntent(Activity from, Class<?> target) {
        return new Intent(from, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /** Legacy method name retained for source compatibility; the canonical input surface is now Cortex Dock. */
    public static Intent inputIntent(Activity from) {
        return new Intent(from, CortexShellActivity.class)
                .putExtra(EXTRA_OPEN_DOCK, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public static void openPrimary(Activity from, String destinationId) {
        if (from == null || from.isFinishing()) return;
        CortexDestinationRegistry.Destination destination = CortexDestinationRegistry.require(destinationId);
        if (from instanceof CortexShellActivity && destination.category == CortexDestinationRegistry.Category.PRIMARY) {
            ((CortexShellActivity) from).navigateTo(destination.destinationId);
            return;
        }
        from.startActivity(primaryIntent(from, destination.destinationId));
        if (destination.category == CortexDestinationRegistry.Category.PRIMARY) from.finish();
    }

    /** Compatibility bridge for non-canonical detail callers. New primary navigation must use a destination ID. */
    public static void openPrimary(Activity from, Class<?> target) {
        if (from == null || target == null || from.isFinishing()) return;
        if (from.getClass().equals(target)) return;
        from.startActivity(primaryIntent(from, target));
        from.finish();
    }

    public static void openDock(Activity from) {
        if (from == null || from.isFinishing()) return;
        if (from instanceof CortexShellActivity) {
            ((CortexShellActivity) from).openDock();
            return;
        }
        from.startActivity(inputIntent(from));
    }

    /** @deprecated Use openDock; retained while legacy Java call sites migrate. */
    @Deprecated
    public static void openInput(Activity from) {
        openDock(from);
    }

    public static Class<?> activityFor(String destinationId) {
        CortexDestinationRegistry.Destination destination = CortexDestinationRegistry.require(destinationId);
        if (destination.category == CortexDestinationRegistry.Category.PRIMARY) return CortexShellActivity.class;
        if (CortexDestinationRegistry.SETTINGS.equals(destination.destinationId)) return SettingsActivity.class;
        if (CortexDestinationRegistry.SYSTEM_HEALTH.equals(destination.destinationId)) return CortexAuditActivity.class;
        throw new IllegalArgumentException("No implementation mapped for destination " + destination.destinationId);
    }
}
