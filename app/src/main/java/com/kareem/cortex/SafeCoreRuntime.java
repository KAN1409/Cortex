package com.kareem.cortex;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Staged recovery bridge between a visually stable launcher and the full Cortex runtime. */
public final class SafeCoreRuntime {
    public static final String VERSION = "safe_core_runtime_006_chatgpt_bridge";
    private static final long POST_RESUME_SETTLE_MS = 1200L;

    public enum Phase { COLD_START, UI_STABLE_PROBING, CORE_READY, CORE_FAILED }
    private static final AtomicBoolean ARMED=new AtomicBoolean(false);
    private static final AtomicReference<Phase> PHASE=new AtomicReference<>(Phase.COLD_START);
    private SafeCoreRuntime(){}
    public static Phase phase(){return PHASE.get();}
    public static boolean ready(){return PHASE.get()==Phase.CORE_READY;}

    public static void armAfterLauncherResume(Context context){
        if(context==null||!ARMED.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(()->beginProbe(app),POST_RESUME_SETTLE_MS);
    }

    private static void beginProbe(Context app){
        if(!PHASE.compareAndSet(Phase.COLD_START,Phase.UI_STABLE_PROBING))return;
        Thread probe=new Thread(()->probeSafeCore(app),"cortex-safe-core-probe");probe.setPriority(Thread.NORM_PRIORITY-1);probe.start();
    }

    private static void probeSafeCore(Context app){
        VaultDb db=null;
        try{
            db=new VaultDb(app);Cursor c=db.getReadableDatabase().rawQuery("SELECT 1",null);boolean ok=c.moveToFirst()&&c.getInt(0)==1;c.close();
            if(!ok)throw new IllegalStateException("database health probe returned no row");
            CapabilitySupervisor.recordHealthy(app,CapabilitySupervisor.Capability.DATABASE);
            PHASE.set(Phase.CORE_READY);

            StartupSafetyGate.releaseAfterSafeCore();

            try{StartupMaintenance.schedule(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{StatefulMeaningScheduler.kick(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{AnalysisQueue.kick(app,null,null);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION,t);}
            try{KnowledgeV2Recovery.recover(app);KnowledgeV2Scheduler.enqueue(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{
                if(GeminiKeyStore.has(app)){
                    recoverVisualFailuresOnce(app);
                    VisualIntelligenceScheduler.kick(app);
                }
            }catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{ProactiveScheduler.enableDaily(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{NexusScheduler.enable(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}
            try{CortexChatGptBridgeScheduler.enable(app);}catch(Throwable t){CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING,t);}

            try{NotificationListenerService.requestRebind(new ComponentName(app,NotificationCaptureService.class));}catch(Throwable ignored){}
        }catch(Throwable t){
            CapabilitySupervisor.recordFailure(app,CapabilitySupervisor.Capability.DATABASE,t);PHASE.set(Phase.CORE_FAILED);
        }finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }

    private static void recoverVisualFailuresOnce(Context app){
        SharedPreferences p=app.getSharedPreferences("cortex_completion_recovery",Context.MODE_PRIVATE);
        String key="visual_failed_recovery_pipeline";
        if(p.getInt(key,-1)==VisualInsightStore.PIPELINE_VERSION)return;
        VaultDb db=null;int requeued=0;
        try{
            db=new VaultDb(app);VisualInsightStore.ensure(db);
            int failed=VisualInsightStore.countFailed(db);
            if(failed>0)requeued=VisualInsightStore.requeueFailed(db,Math.min(20,failed));
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
            p.edit().putInt(key,VisualInsightStore.PIPELINE_VERSION).putInt("visual_failed_requeued",requeued).putLong("visual_failed_recovery_at",System.currentTimeMillis()).apply();
        }
    }

    static boolean safeCapability(CapabilitySupervisor.Capability capability){
        return capability==CapabilitySupervisor.Capability.DATABASE
                ||capability==CapabilitySupervisor.Capability.RAW_NOTIFICATION_CAPTURE
                ||capability==CapabilitySupervisor.Capability.DETERMINISTIC_COGNITION
                ||capability==CapabilitySupervisor.Capability.BACKGROUND_SCHEDULING;
    }
    static void resetForTests(){ARMED.set(false);PHASE.set(Phase.COLD_START);StartupSafetyGate.resetForTests();}
    static void forceReadyForTests(){ARMED.set(true);PHASE.set(Phase.CORE_READY);StartupSafetyGate.releaseAfterSafeCore();}
}
