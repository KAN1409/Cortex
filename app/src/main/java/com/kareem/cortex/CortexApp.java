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
                EnvironmentPreflight.run(this);
            }catch(Throwable ignored){
            }finally{
                if(db!=null)try{db.close();}catch(Throwable ignored){}
            }
        },"cortex-bootstrap");
        t.setPriority(Thread.NORM_PRIORITY-1);
        t.start();
    }
}
