package com.kareem.cortex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs non-UI maintenance only after the app is already visible. */
public final class StartupMaintenance {
    private static final AtomicBoolean scheduled=new AtomicBoolean(false);
    private StartupMaintenance(){}

    public static void schedule(Context context){
        if(context==null||!scheduled.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            Thread t=new Thread(()->run(app),"cortex-maintenance");
            t.setPriority(Thread.NORM_PRIORITY-1);
            t.start();
        },1800);
    }

    private static void run(Context context){
        VaultDb db=null;
        try{
            db=new VaultDb(context);
            CognitiveSchema.ensure(db.getWritableDatabase());
            RelevanceDecisionStatusStore.ensure(db);
            AdjudicationRecovery.run(context,db);
            ContactSafetyMaintenance.run(db);
            EntityGraphMaintenance.run(db);
            IntentionalCognitiveBridge.backfill(db,250);
            EnvironmentPreflight.run(context);
            AdjudicationRecovery.schedule(context);
        }catch(Throwable ignored){
        }finally{
            if(db!=null)try{db.close();}catch(Throwable ignored){}
        }
    }
}
