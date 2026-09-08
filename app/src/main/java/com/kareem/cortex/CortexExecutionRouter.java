package com.kareem.cortex;

/**
 * Pure policy for choosing the least-privileged real execution lane.
 *
 * No cloud/paid inference lane exists here by design. Shizuku is optional and can
 * never be the only path for core voice, memory, search or assistant context.
 */
public final class CortexExecutionRouter {
    public enum Operation {
        NORMAL_ANDROID_ACTION,
        ASSISTANT_CONTEXT,
        NOTIFICATION_CONTEXT,
        UI_INTERACTION,
        PRIVILEGED_SETTING,
        VOICE_TRANSCRIPTION
    }

    public enum Lane {
        NORMAL_ANDROID,
        ASSISTANT,
        NOTIFICATION_LISTENER,
        ACCESSIBILITY,
        PERSISTED_PRIVILEGE,
        PLATFORM_ON_DEVICE_ASR,
        LOCAL_ASR_MODEL,
        SHIZUKU_OPTIONAL,
        UNAVAILABLE
    }

    public static final class Availability {
        public final boolean normalAndroid;
        public final boolean assistantHeld;
        public final boolean notificationListener;
        public final boolean accessibility;
        public final boolean persistedPrivilege;
        public final boolean shizukuAlive;
        public final boolean platformOnDeviceAsrVerified;
        public final boolean localAsrModelReady;

        public Availability(boolean normalAndroid,
                            boolean assistantHeld,
                            boolean notificationListener,
                            boolean accessibility,
                            boolean persistedPrivilege,
                            boolean shizukuAlive,
                            boolean platformOnDeviceAsrVerified,
                            boolean localAsrModelReady) {
            this.normalAndroid = normalAndroid;
            this.assistantHeld = assistantHeld;
            this.notificationListener = notificationListener;
            this.accessibility = accessibility;
            this.persistedPrivilege = persistedPrivilege;
            this.shizukuAlive = shizukuAlive;
            this.platformOnDeviceAsrVerified = platformOnDeviceAsrVerified;
            this.localAsrModelReady = localAsrModelReady;
        }
    }

    public static final class Decision {
        public final Lane lane;
        public final String reason;

        Decision(Lane lane, String reason) {
            this.lane = lane;
            this.reason = reason;
        }

        public boolean available() {
            return lane != Lane.UNAVAILABLE;
        }
    }

    private CortexExecutionRouter() {}

    public static Decision route(Operation operation, Availability a) {
        if (operation == null) return no("No operation supplied");
        if (a == null) return no("Runtime capability state unavailable");

        switch (operation) {
            case VOICE_TRANSCRIPTION:
                if (a.platformOnDeviceAsrVerified) {
                    return yes(Lane.PLATFORM_ON_DEVICE_ASR,
                            "Verified Android on-device speech is available");
                }
                if (a.localAsrModelReady) {
                    return yes(Lane.LOCAL_ASR_MODEL,
                            "Cortex local ASR model is ready");
                }
                return no("No verified zero-cost local transcription engine is ready");

            case ASSISTANT_CONTEXT:
                if (a.assistantHeld) {
                    return yes(Lane.ASSISTANT,
                            "Cortex is the user-selected Android assistant");
                }
                if (a.accessibility) {
                    return yes(Lane.ACCESSIBILITY,
                            "Assistant role is unavailable; Accessibility is the granted context fallback");
                }
                return no("Assistant role is not held and no context fallback is granted");

            case NOTIFICATION_CONTEXT:
                if (a.notificationListener) {
                    return yes(Lane.NOTIFICATION_LISTENER,
                            "Notification listener access is granted");
                }
                return no("Notification listener access is not granted");

            case UI_INTERACTION:
                if (a.normalAndroid) {
                    return yes(Lane.NORMAL_ANDROID,
                            "A standard Android action can perform the requested UI operation");
                }
                if (a.accessibility) {
                    return yes(Lane.ACCESSIBILITY,
                            "Accessibility can perform the user-visible interaction");
                }
                if (a.shizukuAlive) {
                    return yes(Lane.SHIZUKU_OPTIONAL,
                            "Optional Shizuku lane is alive");
                }
                return no("No real UI execution lane is currently available");

            case PRIVILEGED_SETTING:
                if (a.normalAndroid) {
                    return yes(Lane.NORMAL_ANDROID,
                            "A public Android API can perform this setting change");
                }
                if (a.persistedPrivilege) {
                    return yes(Lane.PERSISTED_PRIVILEGE,
                            "A previously granted durable permission/AppOp is sufficient");
                }
                if (a.accessibility) {
                    return yes(Lane.ACCESSIBILITY,
                            "The same change can be performed visibly through Settings UI");
                }
                if (a.shizukuAlive) {
                    return yes(Lane.SHIZUKU_OPTIONAL,
                            "Optional Shizuku privilege is alive");
                }
                return no("This protected setting cannot currently be changed without extra privilege");

            case NORMAL_ANDROID_ACTION:
            default:
                if (a.normalAndroid) {
                    return yes(Lane.NORMAL_ANDROID,
                            "Standard Android API/Intent/provider is available");
                }
                if (a.accessibility) {
                    return yes(Lane.ACCESSIBILITY,
                            "Standard path unavailable; granted Accessibility can perform the visible action");
                }
                if (a.shizukuAlive) {
                    return yes(Lane.SHIZUKU_OPTIONAL,
                            "Optional Shizuku lane is alive");
                }
                return no("No supported execution lane is currently available");
        }
    }

    private static Decision yes(Lane lane, String reason) {
        return new Decision(lane, reason);
    }

    private static Decision no(String reason) {
        return new Decision(Lane.UNAVAILABLE, reason);
    }
}
