package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.*;

public final class CortexNotifications {
    public static final String ATTENTION="cortex_attention",REMINDERS="cortex_reminders";
    private CortexNotifications(){}
    public static void ensureChannels(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel(ATTENTION,"Cortex Prime attention",NotificationManager.IMPORTANCE_DEFAULT));n.createNotificationChannel(new NotificationChannel(REMINDERS,"Cortex Prime reminders",NotificationManager.IMPORTANCE_HIGH));}}
    public static boolean allowed(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    public static void digest(Context c,ArrayList<ProactiveSignal> xs){if(xs==null||xs.isEmpty()||!allowed(c))return;ensureChannels(c);StringBuilder big=new StringBuilder();for(int i=0;i<Math.min(4,xs.size());i++){if(i>0)big.append('\n');big.append("• ").append(xs.get(i).title);}Intent in=new Intent(c,BrainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,100,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);NotificationCompat.Builder b=new NotificationCompat.Builder(c,ATTENTION).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Cortex Prime found something worth your attention").setContentText(xs.get(0).title).setStyle(new NotificationCompat.BigTextStyle().bigText(big.toString())).setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true);((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4101,b.build());}
    public static void reminder(Context c,String title,String text,int id){if(!allowed(c))return;ensureChannels(c);Intent in=new Intent(c,BrainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,2000+id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);NotificationCompat.Builder b=new NotificationCompat.Builder(c,REMINDERS).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title==null||title.isEmpty()?"Cortex Prime reminder":title).setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).setAutoCancel(true);((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(5000+Math.abs(id%100000),b.build());}
}
