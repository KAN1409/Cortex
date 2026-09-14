package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import androidx.core.app.ServiceCompat;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resumable foreground downloader for secondary/critic council models. */
public final class LocalCouncilModelDownloadService extends Service {
    public static final String ACTION_START="com.kareem.cortex.COUNCIL_MODEL_START";
    public static final String EXTRA_MODEL_ID="model_id";
    private static final String CHANNEL="cortex_council_model_download";
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    private volatile boolean stop=false;

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Cortex council model downloads",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String id=intent==null?"":intent.getStringExtra(EXTRA_MODEL_ID);
        LocalCouncilModelRegistry.Model model=model(id);
        if(model==null||LocalCouncilModelRegistry.PRIMARY.equals(model.id)){stopSelf(startId);return START_NOT_STICKY;}
        try{ServiceCompat.startForeground(this,43000+Math.abs(model.id.hashCode()%500),notification(model,"Preparing",0,0,0),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);}
        catch(Throwable t){stopSelf(startId);return START_NOT_STICKY;}
        if(RUNNING.compareAndSet(false,true)){
            Thread th=new Thread(()->run(model),"cortex-council-download-"+model.id);th.setPriority(Thread.NORM_PRIORITY-1);th.start();
        }else stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stop=true;RUNNING.set(false);super.onDestroy();}

    private void run(LocalCouncilModelRegistry.Model model){
        PowerManager.WakeLock wake=null;
        try{
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Cortex:councilDownload");wake.acquire(6L*60*60*1000);
            File part=LocalCouncilModelRegistry.partFile(this,model),finalFile=LocalCouncilModelRegistry.file(this,model);
            long offset=part.exists()?part.length():0;
            HttpURLConnection c=open(model.url,offset);int code=c.getResponseCode();
            boolean append=offset>0&&code==206;if(offset>0&&code==200){part.delete();offset=0;append=false;}
            if(code!=200&&code!=206)throw new IOException("HTTP "+code);
            long total=total(c,offset,code),done=offset,last=offset,lastAt=System.currentTimeMillis();
            byte[] buf=new byte[256*1024];
            try(InputStream in=new BufferedInputStream(c.getInputStream(),512*1024);OutputStream out=new BufferedOutputStream(new FileOutputStream(part,append),512*1024)){
                for(int n;(n=in.read(buf))!=-1&&!stop;){out.write(buf,0,n);done+=n;long now=System.currentTimeMillis();if(now-lastAt>900||done-last>4L*1024*1024){out.flush();notify(model,"Downloading",pct(done,total),done,total);last=done;lastAt=now;}}
                out.flush();
            }finally{c.disconnect();}
            if(stop)return;
            if(total>0&&part.length()!=total)throw new EOFException("ended at "+part.length()+" / "+total);
            if(part.length()<model.minBytes)throw new EOFException("model file too small");
            if(finalFile.exists()&&!finalFile.delete())throw new IOException("cannot replace model");
            if(!part.renameTo(finalFile)){copy(part,finalFile);part.delete();}
            if(!LocalCouncilModelRegistry.ready(this,model))throw new IOException("GGUF validation failed");
            notify(model,"Ready",100,finalFile.length(),finalFile.length());
        }catch(Throwable t){
            notify(model,"Download failed: "+t.getClass().getSimpleName(),0,LocalCouncilModelRegistry.partFile(this,model).length(),0);
        }finally{
            if(wake!=null&&wake.isHeld())try{wake.release();}catch(Throwable ignored){}
            RUNNING.set(false);stopForeground(false);stopSelf();
        }
    }

    public static void start(Context c,String modelId){
        Intent i=new Intent(c,LocalCouncilModelDownloadService.class).setAction(ACTION_START).putExtra(EXTRA_MODEL_ID,modelId);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }

    private HttpURLConnection open(String start,long offset)throws Exception{
        String u=start;
        for(int i=0;i<10;i++){
            HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(45000);
            c.setRequestProperty("User-Agent","Cortex/1.0 Android");c.setRequestProperty("Accept-Encoding","identity");if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            int code=c.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IOException("redirect without location");u=new URL(new URL(u),loc).toString();continue;}return c;
        }throw new IOException("too many redirects");
    }
    private long total(HttpURLConnection c,long offset,int code){String cr=c.getHeaderField("Content-Range");if(cr!=null){int slash=cr.lastIndexOf('/');if(slash>=0)try{return Long.parseLong(cr.substring(slash+1).trim());}catch(Throwable ignored){}}long len=c.getContentLengthLong();return len>0?(code==206?offset+len:len):0;}
    private int pct(long done,long total){return total>0?(int)Math.min(100,done*100/total):0;}
    private void notify(LocalCouncilModelRegistry.Model m,String title,int pct,long done,long total){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(43000+Math.abs(m.id.hashCode()%500),notification(m,title,pct,done,total));}
    private Notification notification(LocalCouncilModelRegistry.Model m,String title,int pct,long done,long total){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(m.name+" · "+title).setOnlyAlertOnce(true).setOngoing(!"Ready".equals(title));
        b.setContentText(LocalModelManager.human(done)+(total>0?" / "+LocalModelManager.human(total):""));if(total>0)b.setProgress(100,pct,false);else b.setProgress(0,0,true);return b.build();
    }
    private static void copy(File a,File b)throws Exception{try(InputStream in=new BufferedInputStream(new FileInputStream(a));OutputStream out=new BufferedOutputStream(new FileOutputStream(b))){byte[] x=new byte[1024*1024];for(int n;(n=in.read(x))!=-1;)out.write(x,0,n);}}
    private static LocalCouncilModelRegistry.Model model(String id){for(LocalCouncilModelRegistry.Model m:LocalCouncilModelRegistry.council())if(m.id.equals(id))return m;return null;}
}
