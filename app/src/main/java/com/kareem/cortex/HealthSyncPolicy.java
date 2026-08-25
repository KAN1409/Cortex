package com.kareem.cortex;

import androidx.health.connect.client.HealthConnectClient;

/** User-actionable Health Connect state/failure policy. Health sync never silently claims partial success. */
public final class HealthSyncPolicy {
    public static final class Failure {
        public final String state,kind,nextAction;
        Failure(String state,String kind,String nextAction){this.state=state;this.kind=kind;this.nextAction=nextAction;}
    }
    private HealthSyncPolicy(){}

    public static Failure sdk(int sdkStatus){
        if(sdkStatus==HealthConnectClient.SDK_AVAILABLE)return new Failure("READY","","Health Connect is available.");
        if(sdkStatus==HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)return new Failure(HealthSyncResult.UPDATE_REQUIRED,"provider_update","Update Health Connect, then return to Cortex.");
        return new Failure(HealthSyncResult.UNAVAILABLE,"provider_unavailable","Health Connect is unavailable on this device.");
    }

    public static Failure classify(Throwable t){
        String m=t==null||t.getMessage()==null?"":t.getMessage().toLowerCase();
        if(t instanceof SecurityException)return new Failure(HealthSyncResult.NEEDS_ACCESS,"missing_permission","Grant all requested Health Connect read scopes, then sync again.");
        if(t instanceof IllegalStateException&&m.contains("update"))return new Failure(HealthSyncResult.UPDATE_REQUIRED,"provider_update","Update Health Connect, then sync again.");
        if(t instanceof IllegalStateException&&m.contains("unavailable"))return new Failure(HealthSyncResult.UNAVAILABLE,"provider_unavailable","Health Connect is unavailable on this device.");
        if(m.contains("permission")||m.contains("access"))return new Failure(HealthSyncResult.NEEDS_ACCESS,"missing_permission","Review Health Connect permissions, then sync again.");
        return new Failure(HealthSyncResult.ERROR,"health_connect_error","Health Connect stopped before the sync completed. Review source status and retry explicitly.");
    }
}
