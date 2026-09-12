package com.kareem.cortex;

import static org.junit.Assert.*;
import android.os.PowerManager;
import org.junit.Test;

public class WorkVaultResourceGovernorTest {
    @Test public void severeThermalPauses(){
        assertEquals(WorkVaultResourceGovernor.Decision.PAUSE,WorkVaultResourceGovernor.decide(PowerManager.THERMAL_STATUS_SEVERE,80,true));
    }

    @Test public void lowBatteryUnpluggedPauses(){
        assertEquals(WorkVaultResourceGovernor.Decision.PAUSE,WorkVaultResourceGovernor.decide(PowerManager.THERMAL_STATUS_NONE,15,false));
    }

    @Test public void moderateThermalThrottles(){
        assertEquals(WorkVaultResourceGovernor.Decision.THROTTLE,WorkVaultResourceGovernor.decide(PowerManager.THERMAL_STATUS_MODERATE,80,true));
    }

    @Test public void lowishBatteryUnpluggedThrottles(){
        assertEquals(WorkVaultResourceGovernor.Decision.THROTTLE,WorkVaultResourceGovernor.decide(PowerManager.THERMAL_STATUS_NONE,24,false));
    }

    @Test public void healthyChargingDeviceRunsFullSpeed(){
        assertEquals(WorkVaultResourceGovernor.Decision.RUN,WorkVaultResourceGovernor.decide(PowerManager.THERMAL_STATUS_NONE,80,true));
        assertEquals(48,WorkVaultResourceGovernor.recommendedBatchSize(WorkVaultResourceGovernor.Decision.RUN));
        assertEquals(12,WorkVaultResourceGovernor.recommendedBatchSize(WorkVaultResourceGovernor.Decision.THROTTLE));
    }
}
