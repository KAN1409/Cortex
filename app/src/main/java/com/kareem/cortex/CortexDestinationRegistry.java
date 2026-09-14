package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical product destination contract.
 *
 * Activities are implementation details. Product navigation owns these semantic destination IDs.
 */
public final class CortexDestinationRegistry {
    public static final String NOW = "NOW";
    public static final String WORK = "WORK";
    public static final String MEMORY = "MEMORY";
    public static final String CAPTURE = "CAPTURE";
    public static final String SETTINGS = "SETTINGS";
    public static final String SYSTEM_HEALTH = "SYSTEM_HEALTH";

    public enum Category { PRIMARY, SETTINGS, SYSTEM }
    public enum DeepLinkPolicy { NONE, INTERNAL_ONLY, EXTERNAL_ALLOWED }

    public static final class Destination {
        public final String destinationId;
        public final String route;
        public final String label;
        public final String icon;
        public final Category category;
        public final boolean productionVisible;
        public final DeepLinkPolicy deepLinkPolicy;

        Destination(String destinationId, String route, String label, String icon,
                    Category category, boolean productionVisible, DeepLinkPolicy deepLinkPolicy) {
            this.destinationId = clean(destinationId).toUpperCase(Locale.ROOT);
            this.route = clean(route).toLowerCase(Locale.ROOT);
            this.label = clean(label);
            this.icon = clean(icon);
            this.category = category;
            this.productionVisible = productionVisible;
            this.deepLinkPolicy = deepLinkPolicy;
        }
    }

    private static final List<Destination> ALL = Collections.unmodifiableList(Arrays.asList(
            d(NOW, "now", "Now", "bolt", Category.PRIMARY, true, DeepLinkPolicy.INTERNAL_ONLY),
            d(WORK, "work", "Work", "project", Category.PRIMARY, true, DeepLinkPolicy.INTERNAL_ONLY),
            d(MEMORY, "memory", "Memory", "photo", Category.PRIMARY, true, DeepLinkPolicy.INTERNAL_ONLY),
            d(CAPTURE, "capture", "Capture", "capture", Category.PRIMARY, true, DeepLinkPolicy.EXTERNAL_ALLOWED),
            d(SETTINGS, "settings", "Settings", "settings", Category.SETTINGS, true, DeepLinkPolicy.INTERNAL_ONLY),
            d(SYSTEM_HEALTH, "system-health", "System Health", "check", Category.SYSTEM, true, DeepLinkPolicy.INTERNAL_ONLY)
    ));

    private CortexDestinationRegistry() {}

    private static Destination d(String id, String route, String label, String icon,
                                 Category category, boolean visible, DeepLinkPolicy deepLinkPolicy) {
        return new Destination(id, route, label, icon, category, visible, deepLinkPolicy);
    }

    public static List<Destination> all() { return ALL; }

    public static List<Destination> primary() {
        ArrayList<Destination> out = new ArrayList<>();
        for (Destination d : ALL) if (d.category == Category.PRIMARY) out.add(d);
        return Collections.unmodifiableList(out);
    }

    public static Destination require(String destinationId) {
        String wanted = clean(destinationId).toUpperCase(Locale.ROOT);
        for (Destination d : ALL) if (d.destinationId.equals(wanted)) return d;
        throw new IllegalArgumentException("Unknown Cortex destination: " + destinationId);
    }

    public static boolean contains(String destinationId) {
        try { require(destinationId); return true; } catch (IllegalArgumentException ignored) { return false; }
    }

    public static void validateOrThrow() {
        Set<String> ids = new HashSet<>();
        Set<String> routes = new HashSet<>();
        for (Destination d : ALL) {
            if (d.destinationId.isEmpty() || d.route.isEmpty() || d.label.isEmpty() || d.category == null || d.deepLinkPolicy == null) {
                throw new IllegalStateException("Incomplete destination contract: " + d.destinationId);
            }
            if (!ids.add(d.destinationId)) throw new IllegalStateException("Duplicate destinationId: " + d.destinationId);
            if (!routes.add(d.route)) throw new IllegalStateException("Conflicting canonical destination route: " + d.route);
        }
        List<Destination> primary = primary();
        if (primary.size() != 4) throw new IllegalStateException("Cortex must expose exactly four primary destinations; found " + primary.size());
        Set<String> expected = new HashSet<>(Arrays.asList(NOW, WORK, MEMORY, CAPTURE));
        for (Destination d : primary) expected.remove(d.destinationId);
        if (!expected.isEmpty()) throw new IllegalStateException("Missing primary destination(s): " + expected);
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
