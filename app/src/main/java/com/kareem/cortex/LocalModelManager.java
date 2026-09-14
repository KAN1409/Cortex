package com.kareem.cortex;

import android.app.DownloadManager;
import android.content.*;
import android.os.Build;
import android.os.Environment;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Cortex-owned local-model transfer state. The actual HTTP transfer runs in LocalModelDownloadService. */
public final class LocalModelManager {
    public static final String MODEL_NAME="Qwen3-30B-A3B-Thinking-2507 Q2_K · Extreme Local Brain";
    public static final String MODEL_FILE="Qwen3-30B-A3B-Thinking-2507-Q2_K.gguf";
    public static final String MODEL_URL="https://huggingface.co/tensorblock/Qwen_Qwen3-30B-A3B-Thinking-2507-GGUF/resolve/main/Qwen3-30B-A3B-Thinking-2507-Q2_K.gguf?download=true";
    public static final String SHA256="e6e29d06127f6773a4a3097b756c42fadfd9dc075101b7be55dd64d42e4585b6";
    static final String PREF="cortex_local_model";
    static final String K_STATE="transfer_state",K_DONE="transfer_done",K_TOTAL="transfer_total",K_SPEED="transfer_speed",K_ERROR="transfer_error",K_HTTP="transfer_http",K_URL="transfer_url",K_RANGE="transfer_range",K_RETRY="transfer_retry",K_PROGRESS_AT="transfer_progress_at",K_VERIFIED="verified_sha",K_LEGACY_ID="download_id";
    private LocalModelManager(){}

    public static File modelFile(Context c){File d=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);return d==null?new File(c.getFilesDir(),MODEL_FILE):new File(d,MODEL_FILE);}
    public static File partFile(Context c){File f=modelFile(c);return new File(f.getParentFile(),MODEL_FILE+".part");}
    public static boolean filePresent(Context c){File f=modelFile(c);return f.exists()&&f.length()>10_000_000_000L;}
    public static boolean verified(Context c){return SHA256.equalsIgnoreCase(p(c).getString(K_VERIFIED,""))&&filePresent(c);}
    public static boolean runtimeInstalled(Context c){return LocalLlmRuntime.ready(c);}
    public static boolean installed(Context c){return verified(c)&&runtimeInstalled(c);}
    public static long size(Context c){File f=modelFile(c);return f.exists()?f.length():0;}
    public static long partialSize(Context c){File f=partFile(c);return f.exists()?f.length():0;}

    public static void startDownload(Context c){
        clearLegacyDownload(c);
        if(verified(c))return;
        File finalFile=modelFile(c);if(finalFile.exists()&&finalFile.length()<10_000_000_000L)finalFile.delete();
        LocalLlmRuntime.invalidate(c);
        setState(c,"connecting",partialSize(c),Math.max(0,p(c).getLong(K_TOTAL,0)),0,"",0,MODEL_URL,false,0);
        startTransferService(c,LocalModelDownloadService.ACTION_START);
    }
    public static void resumeDownload(Context c){startTransferService(c,LocalModelDownloadService.ACTION_RESUME);}
    public static void pauseDownload(Context c){c.startService(new Intent(c,LocalModelDownloadService.class).setAction(LocalModelDownloadService.ACTION_PAUSE));}
    public static void cancelDownload(Context c){c.startService(new Intent(c,LocalModelDownloadService.class).setAction(LocalModelDownloadService.ACTION_CANCEL));}

    private static void startTransferService(Context c,String action){
        Intent i=new Intent(c,LocalModelDownloadService.class).setAction(action);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }

    /**
     * Repairs transient transfer state left behind by process death. Call only after Cortex safe-core
     * startup has released the startup gate (the app is visibly foregrounded at that point).
     * Returns true when an interrupted transfer was detected.
     */
    public static boolean recoverInterruptedTransfer(Context c,boolean autoResume){
        if(c==null||verified(c)||LocalModelDownloadService.isRunning())return false;
        Status s=status(c);String st=s.state;
        boolean transientState="connecting".equals(st)||"downloading".equals(st)||"retrying".equals(st)||"verifying".equals(st);
        if(!transientState)return false;
        long done=Math.max(partialSize(c),s.done);
        setState(c,"paused",done,s.total,0,"Recovered interrupted transfer state after process restart",s.httpStatus,s.currentUrl,s.rangeSupported,s.retryCount);
        if(autoResume&&!StartupSafetyGate.active()){
            try{resumeDownload(c);}catch(Throwable t){setState(c,"paused",done,s.total,0,"Resume deferred: "+t.getClass().getSimpleName()+": "+safe(t.getMessage()),s.httpStatus,s.currentUrl,s.rangeSupported,s.retryCount);}
        }
        return true;
    }

    public static Verification verify(Context c){
        File f=modelFile(c);if(!f.exists())return new Verification(false,"Model file is missing","");
        if(f.length()<10_000_000_000L)return new Verification(false,"Extreme 30B model file is incomplete","");
        try{
            if(!ggufHeader(f))return failVerify(c,"Invalid GGUF header","");
            String actual=sha256(f);
            if(!SHA256.equalsIgnoreCase(actual))return failVerify(c,"SHA-256 mismatch",actual);
            p(c).edit().putString(K_VERIFIED,SHA256).putString(K_STATE,"verified").putLong(K_DONE,f.length()).putLong(K_TOTAL,f.length()).putString(K_ERROR,"").apply();
            return new Verification(true,"Model asset verified",actual);
        }catch(Exception e){return failVerify(c,"Verification failed: "+e.getClass().getSimpleName(),"");}
    }
    private static Verification failVerify(Context c,String m,String sha){LocalLlmRuntime.invalidate(c);p(c).edit().remove(K_VERIFIED).putString(K_STATE,"verification_failed").putString(K_ERROR,m).apply();return new Verification(false,m,sha);}
    private static boolean ggufHeader(File f)throws Exception{try(InputStream in=new FileInputStream(f)){byte[] h=new byte[4];return in.read(h)==4&&h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}}
    private static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f),4*1024*1024)){byte[] b=new byte[4*1024*1024];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}

    public static Status status(Context c){
        if(verified(c))return new Status("Verified model asset",100,size(c),size(c),true,false,"verified","SHA-256 + GGUF valid",0,"",0,false,0,0,partialSize(c));
        SharedPreferences p=p(c);String state=p.getString(K_STATE,"");long part=partialSize(c);long done=Math.max(part,p.getLong(K_DONE,0)),total=p.getLong(K_TOTAL,0),speed=p.getLong(K_SPEED,0),progressAt=p.getLong(K_PROGRESS_AT,0);int http=p.getInt(K_HTTP,0),retry=p.getInt(K_RETRY,0);boolean ranges=p.getBoolean(K_RANGE,false);String err=p.getString(K_ERROR,""),url=p.getString(K_URL,MODEL_URL);
        if(state.isEmpty()){if(filePresent(c))state="downloaded";else if(part>0)state="paused";else state="not_downloaded";}
        boolean failed=state.equals("failed")||state.equals("verification_failed");int pct=total>0?(int)Math.min(100,(done*100)/total):(filePresent(c)?100:0);String label=label(state);String detail=detail(state,err,http,retry,speed,ranges,progressAt);
        return new Status(label,pct,done,total,false,failed,state,detail,http,url,retry,ranges,speed,progressAt,part);
    }
    private static String label(String s){switch(s){case"connecting":return"Connecting";case"downloading":return"Downloading";case"retrying":return"Retrying";case"paused":return"Paused";case"canceled":return"Canceled";case"downloaded":return"Download complete";case"verifying":return"Verifying SHA-256 + GGUF";case"verification_failed":return"Verification failed";case"failed":return"Download failed";case"verified":return"Verified model asset";default:return"Not downloaded";}}
    private static String detail(String state,String err,int http,int retry,long speed,boolean range,long progressAt){StringBuilder s=new StringBuilder();if(!err.isEmpty())s.append(err);if(http>0){if(s.length()>0)s.append(" • ");s.append("HTTP ").append(http);}if(retry>0){if(s.length()>0)s.append(" • ");s.append("retry ").append(retry);}if(speed>0){if(s.length()>0)s.append("\n");s.append(human(speed)).append("/s");}if("downloading".equals(state)||"retrying".equals(state)){if(s.length()>0)s.append(" • ");s.append(range?"resume supported":"range support pending");}if(progressAt>0&&(state.equals("downloading")||state.equals("retrying")||state.equals("connecting"))){if(s.length()>0)s.append(" • ");long age=Math.max(0,System.currentTimeMillis()-progressAt);if(age>60_000L)s.append("last progress ").append(age/1000L).append("s ago");}return s.toString();}

    static void setState(Context c,String state,long done,long total,long speed,String error,int http,String url,boolean ranges,int retry){SharedPreferences.Editor e=p(c).edit().putString(K_STATE,state).putLong(K_DONE,done).putLong(K_TOTAL,total).putLong(K_SPEED,speed).putString(K_ERROR,error==null?"":error).putInt(K_HTTP,http).putString(K_URL,url==null?MODEL_URL:url).putBoolean(K_RANGE,ranges).putInt(K_RETRY,retry);if(done>0)e.putLong(K_PROGRESS_AT,System.currentTimeMillis());e.apply();}
    static String transferState(Context c){return p(c).getString(K_STATE,"");}
    static void clearVerified(Context c){p(c).edit().remove(K_VERIFIED).apply();LocalLlmRuntime.invalidate(c);}
    static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    static void clearLegacyDownload(Context c){long id=p(c).getLong(K_LEGACY_ID,-1);if(id>=0)try{((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);}catch(Exception ignored){}p(c).edit().remove(K_LEGACY_ID).apply();}
    public static void remove(Context c){cancelDownload(c);File f=modelFile(c),part=partFile(c);if(f.exists())f.delete();if(part.exists())part.delete();p(c).edit().clear().apply();LocalLlmRuntime.invalidate(c);}
    public static String human(long b){if(b<=0)return"0 B";double g=b/1073741824.0;if(g>=1)return String.format(Locale.US,"%.2f GB",g);double m=b/1048576.0;if(m>=1)return String.format(Locale.US,"%.1f MB",m);return String.format(Locale.US,"%.0f KB",b/1024.0);}
    private static String safe(String s){return s==null?"":s.length()>180?s.substring(0,180):s;}

    public static final class Status{public final String label,state,detail,currentUrl;public final int percent,httpStatus,retryCount;public final long done,total,speedBytesPerSec,lastProgressAt,partialBytes;public final boolean verified,failed,rangeSupported;Status(String l,int p,long d,long t,boolean v,boolean f,String st,String x,int http,String url,int retry,boolean range,long speed,long at,long part){label=l;percent=p;done=d;total=t;verified=v;failed=f;state=st;detail=x==null?"":x;httpStatus=http;currentUrl=url==null?"":url;retryCount=retry;rangeSupported=range;speedBytesPerSec=speed;lastProgressAt=at;partialBytes=part;}}
    public static final class Verification{public final boolean ok;public final String message,actualSha;Verification(boolean o,String m,String a){ok=o;message=m;actualSha=a;}}
}
