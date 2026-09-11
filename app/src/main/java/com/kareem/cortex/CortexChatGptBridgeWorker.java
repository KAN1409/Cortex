package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Durable best-effort teacher sync. Cortex keeps deciding locally if the bridge is offline. */
public final class CortexChatGptBridgeWorker extends Worker {
    public CortexChatGptBridgeWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        if(StartupSafetyGate.active())return Result.retry();
        if(!CortexChatGptBridgeConfig.enabled(getApplicationContext()))return Result.success();
        try{CortexChatGptBridge.sync(getApplicationContext());return Result.success();}
        catch(Throwable t){return Result.retry();}
    }
}
