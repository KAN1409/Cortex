package com.kareem.cortex;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-initiated long-running archive scan/index service.
 * Keeps indexing alive independently of WorkVaultActivity and never copies source originals.
 */
public final class WorkVaultIndexService extends Service {
    public static final String EXTRA_SOURCE_ID="source_id";
    public static final String EXTRA_TREE_URI="tree_uri";
    public static final String EXTRA_SOURCE_NAME="source_name";
    public static final String ACTION_START="com.kareem.cortex.workvault.INDEX";
    public static final String CHANNEL_ID="cortex_work_vault";
    private static final int NOTIFICATION_ID=8601;

    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-work-vault-index");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private final AtomicBoolean running=new AtomicBoolean(false);

    @Override public void onCreate(){super.onCreate();ensureChannel();}
    @Override public IBinder onBind(Intent intent){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||!ACTION_START.equals(intent.getAction()))return START_NOT_STICKY;
        final long sourceId=intent.getLongExtra(EXTRA_SOURCE_ID,0);
        final String tree=intent.getStringExtra(EXTRA_TREE_URI);
        final String sourceName=safe(intent.getStringExtra(EXTRA_SOURCE_NAME));
        if(sourceId<=0||tree==null||tree.trim().isEmpty()){stopSelf(startId);return START_NOT_STICKY;}

        startForeground(NOTIFICATION_ID,notification("Preparing archive index",sourceName,false));
        if(!running.compareAndSet(false,true)){
            notifyState("Archive indexing already running",sourceName,false);
            return START_NOT_STICKY;
        }

        worker.execute(()->runIndex(startId,sourceId,tree,sourceName));
        return START_NOT_STICKY;
    }

    private void runIndex(int startId,long sourceId,String tree,String sourceName){
        VaultDb db=null;
        try{
            db=new VaultDb(getApplicationContext());
            WorkVaultIndexSchema.ensure(db.getWritableDatabase());

            notifyState("Scanning archive for changes",sourceName,true);
            WorkVaultScanner.Result scan=WorkVaultScanner.scan(getApplicationContext(),db,sourceId,Uri.parse(tree));
            if(!scan.error.isEmpty()){
                notifyState("Archive scan failed",scan.error,false);
                return;
            }

            notifyState("Parsing changed files",scan.processed+" files inventoried",true);
            WorkVaultIndexer.Result indexed=WorkVaultIndexer.indexPending(getApplicationContext(),db,sourceId);

            String summary="Parsed "+indexed.indexed+" • OCR "+indexed.needsOcr+" • failed "+indexed.failed;
            notifyState(indexed.failed>0?"Work Vault indexed with warnings":"Work Vault index complete",summary,false);
        }catch(Throwable t){
            notifyState("Work Vault indexing failed",safe(t.getMessage()).isEmpty()?t.getClass().getSimpleName():safe(t.getMessage()),false);
        }finally{
            running.set(false);
            if(db!=null)try{db.close();}catch(Throwable ignored){}
            if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_DETACH);else stopForeground(false);
            stopSelf(startId);
        }
    }

    private Notification notification(String title,String detail,boolean ongoing){
        Intent open=new Intent(this,WorkVaultActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,8601,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(detail==null?"":detail)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if(ongoing)b.setProgress(0,0,true);
        return b.build();
    }

    private void notifyState(String title,String detail,boolean ongoing){
        try{((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,notification(title,detail,ongoing));}catch(Throwable ignored){}
    }

    private void ensureChannel(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Work Vault indexing",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Background indexing of user-selected work archive folders");ch.setShowBadge(false);nm.createNotificationChannel(ch);
    }

    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private static String safe(String s){return s==null?"":s.trim();}
}
