package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.kareem.cortex.visualmemory.VisualMemoryRuntime;
import com.kareem.cortex.visualmemory.data.search.SemanticModelInstallWorker;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs non-UI maintenance only after the app is already visible, with stage-level observability. */
public final class StartupMaintenance {
    public static final String VERSION="startup_maintenance_002";
    private static final AtomicBoolean scheduled=new AtomicBoolean(false);
    private StartupMaintenance(){}

    public static void schedule(Context context){
        if(StartupSafetyGate.active())return;if(context==null||!scheduled.compareAndSet(false,true))return;Context app=context.getApplicationContext();PhoneContextScheduler.schedule(app);StatefulMeaningScheduler.kick(app);CortexNotifications.ensureChannels(app);ProactiveScheduler.enableDaily(app);new Handler(Looper.getMainLooper()).postDelayed(()->{Thread t=new Thread(()->run(app),"cortex-maintenance");t.setPriority(Thread.NORM_PRIORITY-1);t.start();},1800);
    }

    private static void run(Context context){
        if(StartupSafetyGate.active())return;VaultDb vault=null;
        try{
            vault=new VaultDb(context);final VaultDb db=vault;
            runStage(db,"schema",()->{CognitiveStore.ensure(db);StatefulMeaningStore.ensure(db.getWritableDatabase());CanonicalStateStore.ensure(db.getWritableDatabase());RelevanceDecisionStatusStore.ensure(db);PhoneContextStore.ensure(db);KnowledgeV2Schema.ensure(db.getWritableDatabase());});
            runStage(db,"phone_usage",()->{if(PhoneUsageAccess.has(context))PhoneUsageAccess.syncRecent(context,db,System.currentTimeMillis()-2L*60L*60L*1000L);});
            runStage(db,"last_crash",()->importLastCrash(context,db));
            runStage(db,"adjudication_recovery",()->AdjudicationRecovery.run(context,db));
            runStage(db,"contact_safety",()->ContactSafetyMaintenance.run(db));
            runStage(db,"entity_graph",()->EntityGraphMaintenance.run(db));
            runStage(db,"entity_quality",()->EntityQualityMaintenance.run(db));
            runStage(db,"v91_authority_migration",()->CortexV91Authority.migrate(db.getWritableDatabase()));
            runStage(db,"intentional_bridge",()->IntentionalCognitiveBridge.backfill(db,250));
            runStage(db,"stateful_rebuilder",()->StatefulMeaningRebuilder.run(db,500));
            runStage(db,"final_attention",()->CanonicalAttentionMaterializer.run(context,db));
            runStage(db,"authority_validation",()->{
                JSONObject policy=CortexPersonalPolicy.current(context);CortexV91Authority.Validation v=CortexV91Authority.validate(db.getReadableDatabase(),policy.optString("version","local"),policy.optInt("maxNowItems",7));
                if(!v.ok)throw new IllegalStateException(v.summary());
            });
            runStage(db,"environment_preflight",()->EnvironmentPreflight.run(context));
            runStage(db,"recovery_schedule",()->AdjudicationRecovery.schedule(context));
            runStage(db,"stateful_schedule",()->{if(StatefulMeaningRebuilder.hasBacklog(db))StatefulMeaningScheduler.kick(context);});
            runStage(db,"visual_completion",()->VisualMemoryRuntime.enqueueCompletionMaintenance(context));
            runStage(db,"semantic_model",()->SemanticModelInstallWorker.enqueue(context));
            runStage(db,"knowledge_v2",()->KnowledgeV2Scheduler.enqueue(context));
        }catch(Throwable t){if(vault!=null)try{DiagnosticsLog.error(vault,"StartupMaintenance","bootstrap",t,"STARTUP_MAINTENANCE_BOOTSTRAP",0,0,0,0,0,null);}catch(Throwable ignored){}}
        finally{if(vault!=null)try{vault.close();}catch(Throwable ignored){}}
    }

    private static void runStage(VaultDb db,String name,UnsafeStage stage){
        long start=System.currentTimeMillis();try{stage.run();DiagnosticsLog.info(db,"StartupMaintenance",name,"success",0,0,0,0,0,System.currentTimeMillis()-start,null);}catch(Throwable t){DiagnosticsLog.error(db,"StartupMaintenance",name,t,"STARTUP_STAGE_"+name.toUpperCase(),0,0,0,0,0,null);}
    }
    private interface UnsafeStage{void run() throws Throwable;}

    private static void importLastCrash(Context context,VaultDb db){try{String crash=CrashRecorder.read(context,12000);if(crash.trim().isEmpty())return;String hash=Fingerprint.text(crash),old=context.getSharedPreferences("cortex_crash_diag",Context.MODE_PRIVATE).getString("imported_hash","");if(hash.equals(old))return;JSONObject meta=new JSONObject();meta.put("crash_hash",hash);meta.put("exception",exceptionLine(crash));meta.put("top_frame",topFrame(crash));meta.put("crash_file","last_crash.txt");DiagnosticsLog.log(db,"CRITICAL","CrashRecorder","uncaught_java_crash","recorded",exceptionClass(crash),"UNCAUGHT_JAVA_CRASH",0,0,0,0,0,0,meta);context.getSharedPreferences("cortex_crash_diag",Context.MODE_PRIVATE).edit().putString("imported_hash",hash).apply();}catch(Throwable ignored){}}
    private static String exceptionLine(String s){for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("java.")||x.startsWith("android.")||x.startsWith("kotlin.")||x.contains("Exception")||x.contains("Error"))return clip(x,280);}return"unknown";}
    private static String exceptionClass(String s){String x=exceptionLine(s);int p=x.indexOf(':');return clip(p>0?x.substring(0,p):x,100);}
    private static String topFrame(String s){for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("at com.kareem.cortex."))return clip(x,280);}for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("at "))return clip(x,280);}return"";}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
}
