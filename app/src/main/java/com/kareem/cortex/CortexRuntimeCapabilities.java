package com.kareem.cortex;

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
        public final boolean platformOnDeviceSpeechAvailable;

        Snapshot(Transport transport, boolean validatedInternet, boolean metered,
                 boolean platformOnDeviceSpeechAvailable) {
            this.transport = transport;
            this.validatedInternet = validatedInternet;
            this.metered = metered;
            this.platformOnDeviceSpeechAvailable = platformOnDeviceSpeechAvailable;
        }

        public boolean cellularFirstReady() {
            return transport == Transport.CELLULAR || transport == Transport.WIFI ||
                    transport == Transport.ETHERNET || transport == Transport.VPN ||
                    transport == Transport.OFFLINE;
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
        boolean localSpeech = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app);
        return new Snapshot(transport, validated, metered, localSpeech);
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
