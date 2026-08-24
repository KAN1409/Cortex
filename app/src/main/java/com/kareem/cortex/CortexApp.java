package com.kareem.cortex;

import android.app.Application;

/** Lightweight process bootstrap for cognitive storage + production environment readiness. */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Thread t=new Thread(()->{
            VaultDb db=null;
            try{
                db=new VaultDb(this);
                CognitiveSchema.ensure(db.getWritableDatabase());
                RelevanceDecisionStatusStore.ensure(db);
                AdjudicationRecovery.run(this,db);
                ContactSafetyMaintenance.run(db);
                EntityGraphMaintenance.run(db);
                IntentionalCognitiveBridge.backfill(db,250);
                EnvironmentPreflight.run(this);
                AdjudicationRecovery.schedule(this);
            }catch(Throwable ignored){
            }finally{
                if(db!=null)try{db.close();}catch(Throwable ignored){}
            }
        },"cortex-bootstrap");
        t.setPriority(Thread.NORM_PRIORITY-1);
        t.start();
    }
}
