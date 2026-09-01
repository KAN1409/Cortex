package com.kareem.cortex;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Process bootstrap stays light. UI consistency is applied after each Activity has drawn;
 * DB maintenance/recovery remains deferred to the first visible PRIME surface.
 */
public class CortexApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        CrashRecorder.install(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}
            public void onActivityStarted(Activity a){}
            public void onActivityResumed(Activity a){
                if(a!=null&&a.getWindow()!=null&&a.getWindow().getDecorView()!=null)a.getWindow().getDecorView().post(()->CortexGlobalSkin.apply(a));
            }
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        });
        // Never open Cortex DB, run migrations, recovery or backfills on process start.
        // StartupMaintenance is scheduled by the first visible PRIME activity.
    }
}
