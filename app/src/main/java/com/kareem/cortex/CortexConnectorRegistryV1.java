package com.kareem.cortex;

import android.content.Context;
import android.content.pm.PackageManager;

/** Package identity registry for local connector apps. Caller UID is authoritative, not payload text. */
public final class CortexConnectorRegistryV1 {
    private CortexConnectorRegistryV1() {}

    public static Identity resolve(Context context, int sendingUid) {
        if (context == null || sendingUid <= 0) return null;
        try {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(sendingUid);
            if (packages == null) return null;
            for (String pkg : packages) {
                if ("com.kareem.secondbrain".equals(pkg)) {
                    return new Identity("second_brain", pkg, 100);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static final class Identity {
        public final String connectorId, packageName;
        /** Higher value wins when multiple sensors describe the same physical event. */
        public final int sourcePriority;
        Identity(String connectorId, String packageName, int sourcePriority) {
            this.connectorId = connectorId;
            this.packageName = packageName;
            this.sourcePriority = sourcePriority;
        }
    }
}
