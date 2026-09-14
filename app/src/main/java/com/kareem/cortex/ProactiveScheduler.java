package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class ProactiveScheduler {
    private ProactiveScheduler(){}
    public static void enableDaily(Context c){if(StartupSafetyGate.active()||c==null)return;Calendar now=Calendar.getInstance(),next=Calendar.getInstance();next.set(Calendar.HOUR_OF_DAY,9);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);if(!next.after(now))next.add(Calendar.DAY_OF_YEAR,1);long delay=Math.max(0,next.getTimeInMillis()-now.getTimeInMillis());PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(ProactiveWorker.class,24,TimeUnit.HOURS).setInitialDelay(delay,TimeUnit.MILLISECONDS).build();WorkManager.getInstance(c.getApplicationContext()).enqueueUniquePeriodicWork("cortex_daily_proactive",ExistingPeriodicWorkPolicy.KEEP,r);}
    public static void reminder(Context c,long actionId,String title,String text,long whenMs){if(StartupSafetyGate.active()||c==null||actionId<=0)return;Context app=c.getApplicationContext();long delay=Math.max(1000,whenMs-System.currentTimeMillis());String unique=reminderWorkName(actionId);Data d=new Data.Builder().putInt("id",(int)(actionId%Integer.MAX_VALUE)).putLong("action_id",actionId).putString("title",title).putString("text",text).build();OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(ReminderWorker.class).setInputData(d).setInitialDelay(delay,TimeUnit.MILLISECONDS).addTag(unique).build();WorkManager.getInstance(app).enqueueUniqueWork(unique,ExistingWorkPolicy.REPLACE,r);}
    static String reminderWorkName(long actionId){return"cortex_reminder_"+Math.max(0,actionId);}
}
