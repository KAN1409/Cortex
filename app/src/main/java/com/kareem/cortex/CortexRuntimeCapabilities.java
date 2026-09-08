package com.kareem.cortex;

import android.app.role.RoleManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.speech.SpeechRecognizer;

/** Runtime facts only. No capability is inferred from device model names or network assumptions. */
public final class CortexRuntimeCapabilities {
    public enum Transport { CELLULAR, WIFI, ETHERNET, VPN, OTHER, OFFLINE }

    public static final class Snapshot {
        public final Transport transport;
        public final boolean validatedInternet;
        public final boolean metered;
        public final boolean platformSpeechRecognizerAvailable;
        public final boolean platformOnDeviceSpeechAvailable;
        public final boolean assistantRoleAvailable;
        public final boolean assistantRoleHeld;

        Snapshot(Transport transport, boolean validatedInternet, boolean metered,
                 boolean platformSpeechRecognizerAvailable,
                 boolean platformOnDeviceSpeechAvailable,
                 boolean assistantRoleAvailable,
                 boolean assistantRoleHeld) {
            this.transport = transport;
            this.validatedInternet = validatedInternet;
            this.metered = metered;
            this.platformSpeechRecognizerAvailable = platformSpeechRecognizerAvailable;
            this.platformOnDeviceSpeechAvailable = platformOnDeviceSpeechAvailable;
            this.assistantRoleAvailable = assistantRoleAvailable;
            this.assistantRoleHeld = assistantRoleHeld;
        }

        /** Core Cortex is deliberately transport-agnostic: cellular and offline are normal states. */
        public boolean coreReadyWithoutWifi() {
            return true;
        }

        /** Compatibility alias retained for the first recovery probes. */
        public boolean cellularFirstReady() {
            return coreReadyWithoutWifi();
        }
    }

    private CortexRuntimeCapabilities() {}

    public static Snapshot inspect(Context context) {
        Context app = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = null;
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            if (active != null) caps = cm.getNetworkCapabilities(active);
        }

        Transport transport = classify(caps);
        boolean validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean metered = cm != null && cm.isActiveNetworkMetered();
        boolean speech = SpeechRecognizer.isRecognitionAvailable(app);
        boolean localSpeech = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app);

        boolean assistantAvailable = false;
        boolean assistantHeld = false;
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = (RoleManager) app.getSystemService(Context.ROLE_SERVICE);
            if (roles != null) {
                assistantAvailable = roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT);
                assistantHeld = assistantAvailable && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT);
            }
        }

        return new Snapshot(transport, validated, metered, speech, localSpeech,
                assistantAvailable, assistantHeld);
    }

    static Transport classify(NetworkCapabilities caps) {
        if (caps == null) return Transport.OFFLINE;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return Transport.VPN;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return Transport.CELLULAR;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Transport.WIFI;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return Transport.ETHERNET;
        return Transport.OTHER;
    }
}
