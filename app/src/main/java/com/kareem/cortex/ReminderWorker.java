package com.kareem.cortex;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ReminderWorker extends Worker {
    public ReminderWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){String title=getInputData().getString("title"),text=getInputData().getString("text");int id=getInputData().getInt("id",0);CortexNotifications.reminder(getApplicationContext(),title,text==null?"Cortex reminder":text,id);return Result.success();}
}
