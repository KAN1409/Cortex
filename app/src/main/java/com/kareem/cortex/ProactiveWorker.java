package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.*;

public final class ProactiveWorker extends Worker {
    public ProactiveWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){try{VaultDb db=new VaultDb(getApplicationContext());ArrayList<ProactiveSignal> xs=ProactiveEngine.scan(getApplicationContext(),db,4);if(!xs.isEmpty()){CortexNotifications.digest(getApplicationContext(),xs);ProactiveEngine.markSurfaced(getApplicationContext(),xs);}db.close();return Result.success();}catch(Exception e){return Result.retry();}}
}
