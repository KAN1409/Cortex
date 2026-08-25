package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.Build;
import org.json.JSONObject;
import java.io.File;

/** Shared recorder used by the Brief hero control, home-screen widget and notification stop action. */
public final class CortexRecordService extends Service {
    public static final String ACTION_START="com.kareem.cortex.action.RECORD_START";
    public static final String ACTION_STOP="com.kareem.cortex.action.RECORD_STOP";
    private static final String PREF="cortex_record_state",KEY_RUNNING="running",KEY_STARTED="started_at";
    private static final String CHANNEL="cortex_voice_recording";private static final int NOTIFICATION_ID=41024;
    private final AudioCapture recorder=new AudioCapture();private volatile boolean stopping=false;

    public static boolean isRecording(Context c){return c.getSharedPreferences(PREF,MODE_PRIVATE).getBoolean(KEY_RUNNING,false);}
    public static long startedAt(Context c){return c.getSharedPreferences(PREF,MODE_PRIVATE).getLong(KEY_STARTED,0L);}
    static void setState(Context c,boolean running,long started){c.getSharedPreferences(PREF,MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,running).putLong(KEY_STARTED,running?started:0L).apply();CortexRecordWidget.updateAll(c);}

    @Override public void onCreate(){super.onCreate();ensureChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){String a=intent==null?null:intent.getAction();if(ACTION_STOP.equals(a)){stopAndPersist();return START_NOT_STICKY;}if(ACTION_START.equals(a)){startRecording();return START_NOT_STICKY;}if(!recorder.isRunning())stopSelf();return START_NOT_STICKY;}

    private void startRecording(){
        if(recorder.isRunning())return;
        if(!recorder.hasPermission(this)){setState(this,false,0);stopSelf();return;}
        try{
            Notification n=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(NOTIFICATION_ID,n);
            recorder.start(this);setState(this,true,System.currentTimeMillis());
        }catch(Throwable e){setState(this,false,0);try{stopForeground(true);}catch(Throwable ignored){}stopSelf();}
    }

    private void stopAndPersist(){
        if(stopping)return;stopping=true;File f=null;
        try{if(recorder.isRunning())f=recorder.stop();}catch(Throwable ignored){}
        setState(this,false,0);try{stopForeground(true);}catch(Throwable ignored){}
        if(f!=null&&f.exists()&&f.length()>44)persistAsync(f);else if(f!=null)try{f.delete();}catch(Throwable ignored){}
        stopSelf();
    }

    private void persistAsync(File file){final File f=file;new Thread(()->{VaultDb db=null;try{JSONObject m=new JSONObject();m.put("mime","audio/wav");m.put("bytes",f.length());m.put("recorded_at",System.currentTimeMillis());m.put("source","home_record_control");db=new VaultDb(getApplicationContext());long id=db.insert("AUDIO","manual_recording","Voice recording","","Voice & Audio","voice,audio,transcript",f.getAbsolutePath(),Fingerprint.file(f.getAbsolutePath()),m.toString());if(id>0)AnalysisQueue.kick(getApplicationContext(),null,null);else if(id<0)f.delete();}catch(Throwable ignored){}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}},"cortex-widget-record-save").start();}

    private Notification notification(){
        Intent stop=new Intent(this,CortexRecordService.class).setAction(ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(this,41025,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,InputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent openPi=PendingIntent.getActivity(this,41026,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(R.drawable.ic_cortex_record).setContentTitle("Cortex is recording").setContentText("Tap Stop when the memory is complete").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openPi).addAction(new Notification.Action.Builder(null,"Stop",stopPi).build());if(Build.VERSION.SDK_INT>=21)b.setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE);return b.build();
    }
    private void ensureChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=new NotificationChannel(CHANNEL,"Cortex voice recording",NotificationManager.IMPORTANCE_LOW);c.setDescription("Visible while Cortex records from the home-screen control");c.setSound(null,null);nm.createNotificationChannel(c);}}

    @Override public void onDestroy(){if(recorder.isRunning()&&!stopping)stopAndPersist();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
