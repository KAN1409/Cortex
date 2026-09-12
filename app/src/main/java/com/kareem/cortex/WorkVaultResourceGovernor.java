package com.kareem.cortex;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

/** Controls Work Vault indexing throughput only; it has no authority over cognitive judgment. */
public final class WorkVaultResourceGovernor {
    public static final String VERSION="work_vault_resource_governor_001";
    private WorkVaultResourceGovernor(){}

    public enum Decision{RUN,THROTTLE,PAUSE}

    public static Decision current(Context context){
        if(context==null)return Decision.RUN;
        int thermal=PowerManager.THERMAL_STATUS_NONE;
        if(Build.VERSION.SDK_INT>=29){
            try{
                PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
                if(pm!=null)thermal=pm.getCurrentThermalStatus();
            }catch(Throwable ignored){}
        }
        int battery=100;boolean charging=false;
        try{
            Intent b=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b!=null){
                int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                if(level>=0&&scale>0)battery=Math.max(0,Math.min(100,Math.round(level*100f/scale)));
                int status=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
                charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;
            }
        }catch(Throwable ignored){}
        return decide(thermal,battery,charging);
    }

    static Decision decide(int thermalStatus,int batteryPercent,boolean charging){
        if(thermalStatus>=PowerManager.THERMAL_STATUS_SEVERE)return Decision.PAUSE;
        if(!charging&&batteryPercent>=0&&batteryPercent<=15)return Decision.PAUSE;
        if(thermalStatus>=PowerManager.THERMAL_STATUS_MODERATE)return Decision.THROTTLE;
        if(!charging&&batteryPercent>=0&&batteryPercent<=25)return Decision.THROTTLE;
        return Decision.RUN;
    }

    public static int recommendedBatchSize(Decision d){
        return d==Decision.THROTTLE?12:48;
    }

    public static long recommendedBatchMillis(Decision d){
        return d==Decision.THROTTLE?60_000L:180_000L;
    }
}
