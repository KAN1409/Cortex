package com.kareem.cortex;

import android.content.Context;
import androidx.work.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class ProactiveScheduler {
    private ProactiveScheduler(){}
    public static void enableDaily(Context c){Calendar now=Calendar.getInstance(),next=Calendar.getInstance();next.set(Calendar.HOUR_OF_DAY,9);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);if(!next.after(now))next.add(Calendar.DAY_OF_YEAR,1);long delay=Math.max(0,next.getTimeInMillis()-now.getTimeInMillis());PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(ProactiveWorker.class,24,TimeUnit.HOURS).setInitialDelay(delay,TimeUnit.MILLISECONDS).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("cortex_daily_proactive",ExistingPeriodicWorkPolicy.KEEP,r);}
    public static void enableAttentionMaintenance(Context c){if(c==null)return;Constraints constraints=new Constraints.Builder().setRequiresBatteryNotLow(true).build();PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(AttentionMaintenanceWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints).build();WorkManager.getInstance(c.getApplicationContext()).enqueueUniquePeriodicWork("cortex_attention_maintenance",ExistingPeriodicWorkPolicy.KEEP,r);}
    public static void reminder(Context c,long actionId,String title,String text,long whenMs){long delay=Math.max(1000,whenMs-System.currentTimeMillis());Data d=new Data.Builder().putInt("id",(int)(actionId%Integer.MAX_VALUE)).putString("title",title).putString("text",text).build();OneTimeWorkRequest r=new OneTimeWorkRequest.Builder(ReminderWorker.class).setInputData(d).setInitialDelay(delay,TimeUnit.MILLISECONDS).addTag("cortex_reminder_"+actionId).build();WorkManager.getInstance(c).enqueue(r);}
}
