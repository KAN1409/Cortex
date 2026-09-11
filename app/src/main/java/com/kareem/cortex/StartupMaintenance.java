package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.kareem.cortex.visualmemory.VisualMemoryRuntime;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs non-UI maintenance only after the app is already visible. */
public final class StartupMaintenance {
    private static final AtomicBoolean scheduled=new AtomicBoolean(false);
    private StartupMaintenance(){}

    public static void schedule(Context context){
        if(StartupSafetyGate.active())return;
        if(context==null||!scheduled.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        PhoneContextScheduler.schedule(app);
        StatefulMeaningScheduler.kick(app);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            Thread t=new Thread(()->run(app),"cortex-maintenance");
            t.setPriority(Thread.NORM_PRIORITY-1);
            t.start();
        },1800);
    }

    private static void run(Context context){
        if(StartupSafetyGate.active())return;
        VaultDb db=null;
        try{
            db=new VaultDb(context);
            CognitiveSchema.ensure(db.getWritableDatabase());
            StatefulMeaningStore.ensure(db.getWritableDatabase());
            RelevanceDecisionStatusStore.ensure(db);
            PhoneContextStore.ensure(db);
            KnowledgeV2Schema.ensure(db.getWritableDatabase());
            if(PhoneUsageAccess.has(context))PhoneUsageAccess.syncRecent(context,db,System.currentTimeMillis()-2L*60L*60L*1000L);
            importLastCrash(context,db);
            AdjudicationRecovery.run(context,db);
            ContactSafetyMaintenance.run(db);
            EntityGraphMaintenance.run(db);
            EntityQualityMaintenance.run(db);
            IntentionalCognitiveBridge.backfill(db,250);
            StatefulMeaningRebuilder.run(db,240);
            EnvironmentPreflight.run(context);
            AdjudicationRecovery.schedule(context);
            if(StatefulMeaningRebuilder.hasBacklog(db))StatefulMeaningScheduler.kick(context);

            // Completion pipeline: all of this is idempotent/unique work. It repairs semantic
            // failures, accounts for every OCR-complete screenshot, and rebuilds Knowledge V2
            // after quality-policy changes without delaying app startup.
            VisualMemoryRuntime.enqueueCompletionMaintenance(context);
            KnowledgeV2Scheduler.enqueue(context);
        }catch(Throwable ignored){
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
        }
    }

    /** Record one compact fingerprint/frame per uncaught Java crash. Full stack remains in last_crash.txt. */
    private static void importLastCrash(Context context,VaultDb db){
        try{
            String crash=CrashRecorder.read(context,12000);if(crash.trim().isEmpty())return;
            String hash=Fingerprint.text(crash);String old=context.getSharedPreferences("cortex_crash_diag",Context.MODE_PRIVATE).getString("imported_hash","");if(hash.equals(old))return;
            JSONObject meta=new JSONObject();meta.put("crash_hash",hash);meta.put("exception",exceptionLine(crash));meta.put("top_frame",topFrame(crash));meta.put("crash_file","last_crash.txt");
            DiagnosticsLog.log(db,"CRITICAL","CrashRecorder","uncaught_java_crash","recorded",exceptionClass(crash),"UNCAUGHT_JAVA_CRASH",0,0,0,0,0,0,meta);
            context.getSharedPreferences("cortex_crash_diag",Context.MODE_PRIVATE).edit().putString("imported_hash",hash).apply();
        }catch(Throwable ignored){}
    }
    private static String exceptionLine(String s){for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("java.")||x.startsWith("android.")||x.startsWith("kotlin.")||x.contains("Exception")||x.contains("Error"))return clip(x,280);}return"unknown";}
    private static String exceptionClass(String s){String x=exceptionLine(s);int p=x.indexOf(':');return clip(p>0?x.substring(0,p):x,100);}
    private static String topFrame(String s){for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("at com.kareem.cortex."))return clip(x,280);}for(String line:s.split("\\r?\\n")){String x=line.trim();if(x.startsWith("at "))return clip(x,280);}return"";}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n);}
}
