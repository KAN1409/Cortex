package com.kareem.cortex;

import android.app.Application;

/** Lightweight process bootstrap for the cognitive data foundation. */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        Thread t=new Thread(()->{
            VaultDb db=null;
            try{
                db=new VaultDb(this);
                CognitiveSchema.ensure(db.getWritableDatabase());
            }catch(Throwable ignored){
            }finally{
                if(db!=null)try{db.close();}catch(Throwable ignored){}
            }
        },"cortex-schema-bootstrap");
        t.setPriority(Thread.NORM_PRIORITY-1);
        t.start();
    }
}
