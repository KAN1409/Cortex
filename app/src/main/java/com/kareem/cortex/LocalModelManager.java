package com.kareem.cortex;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

public final class LocalModelManager {
    public static final String MODEL_NAME="Qwen3-4B Q4_K_M";
    public static final String MODEL_FILE="Qwen3-4B-Q4_K_M.gguf";
    public static final String MODEL_URL="https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true";
    public static final String SHA256="ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328";
    private static final String PREF="cortex_local_model",KEY_ID="download_id",KEY_VERIFIED="verified_sha";
    private LocalModelManager(){}

    public static File modelFile(Context c){File d=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);return d==null?new File(c.getFilesDir(),MODEL_FILE):new File(d,MODEL_FILE);}
    public static boolean filePresent(Context c){File f=modelFile(c);return f.exists()&&f.length()>2_000_000_000L;}
    public static boolean verified(Context c){return SHA256.equalsIgnoreCase(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_VERIFIED,""))&&filePresent(c);}
    public static boolean runtimeInstalled(Context c){return false;}
    public static boolean installed(Context c){return verified(c)&&runtimeInstalled(c);}
    public static long size(Context c){File f=modelFile(c);return f.exists()?f.length():0;}

    public static long startDownload(Context c){
        if(filePresent(c))return -1;
        cancelTracked(c,false);
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(MODEL_URL));
        r.setTitle("Cortex Local Brain");r.setDescription(MODEL_NAME+" • direct verified download");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        // User asked for a real one-tap download. Do not silently stall waiting for unmetered Wi-Fi.
        r.setAllowedOverMetered(true);r.setAllowedOverRoaming(false);
        r.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);
        r.setMimeType("application/octet-stream");
        r.addRequestHeader("User-Agent","Cortex/1.0 Android");
        r.setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,MODEL_FILE);
        long id=dm.enqueue(r);c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(KEY_ID,id).remove(KEY_VERIFIED).apply();return id;
    }

    public static long retryDownload(Context c){File f=modelFile(c);if(f.exists()&&!verified(c))f.delete();return startDownload(c);}

    public static Verification verify(Context c){
        File f=modelFile(c);if(!f.exists())return new Verification(false,"Model file is missing","");
        if(f.length()<2_000_000_000L)return new Verification(false,"Model file is incomplete","");
        try{if(!ggufHeader(f))return new Verification(false,"Invalid GGUF header","");String actual=sha256(f);if(!SHA256.equalsIgnoreCase(actual)){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY_VERIFIED).apply();return new Verification(false,"SHA-256 mismatch",actual);}c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_VERIFIED,SHA256).apply();return new Verification(true,"Model asset verified",actual);}catch(Exception e){return new Verification(false,"Verification failed: "+e.getClass().getSimpleName(),"");}
    }

    private static boolean ggufHeader(File f)throws Exception{try(InputStream in=new FileInputStream(f)){byte[] h=new byte[4];if(in.read(h)!=4)return false;return h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}}
    private static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f),4*1024*1024)){byte[] b=new byte[4*1024*1024];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}

    public static Status status(Context c){
        if(verified(c))return new Status("Verified model asset",100,size(c),size(c),true,false,0,"SHA-256 + GGUF valid");
        long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);
        if(id<0){if(filePresent(c))return new Status("Download complete • verification required",100,size(c),size(c),false,false,0,"File exists on storage");return new Status("Not downloaded",0,size(c),0,false,false,0,"Tap download to start");}
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);Cursor q=dm.query(new DownloadManager.Query().setFilterById(id));
        if(q==null||!q.moveToFirst()){if(q!=null)q.close();return filePresent(c)?new Status("Download complete • verification required",100,size(c),size(c),false,false,0,"DownloadManager record ended"):new Status("Download record missing",0,size(c),0,false,true,-1,"Retry the download");}
        int state=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));long done=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),total=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));int reason=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));q.close();
        String detail=reasonText(state,reason);String label=state==DownloadManager.STATUS_RUNNING?"Downloading":state==DownloadManager.STATUS_PAUSED?"Paused":state==DownloadManager.STATUS_PENDING?"Queued":state==DownloadManager.STATUS_SUCCESSFUL?"Download complete • verification required":state==DownloadManager.STATUS_FAILED?"Download failed":"Waiting";
        int pct=total>0?(int)Math.min(100,(done*100)/total):(filePresent(c)?100:0);return new Status(label,pct,done,total,false,state==DownloadManager.STATUS_FAILED,reason,detail);
    }

    private static String reasonText(int state,int reason){
        if(state==DownloadManager.STATUS_PENDING)return "Waiting for Android Download Manager to start";
        if(state==DownloadManager.STATUS_RUNNING)return "Downloading in background";
        if(state==DownloadManager.STATUS_SUCCESSFUL)return "Download finished; verification is next";
        if(state==DownloadManager.STATUS_PAUSED){if(reason==DownloadManager.PAUSED_WAITING_FOR_NETWORK)return "Paused: waiting for network";if(reason==DownloadManager.PAUSED_QUEUED_FOR_WIFI)return "Paused: Android queued it for Wi-Fi";if(reason==DownloadManager.PAUSED_WAITING_TO_RETRY)return "Paused: Android will retry";return "Paused by Android (reason "+reason+")";}
        if(state==DownloadManager.STATUS_FAILED){switch(reason){case DownloadManager.ERROR_CANNOT_RESUME:return"Failed: cannot resume";case DownloadManager.ERROR_DEVICE_NOT_FOUND:return"Failed: storage unavailable";case DownloadManager.ERROR_FILE_ALREADY_EXISTS:return"Failed: destination already exists";case DownloadManager.ERROR_HTTP_DATA_ERROR:return"Failed: HTTP data error";case DownloadManager.ERROR_INSUFFICIENT_SPACE:return"Failed: insufficient storage";case DownloadManager.ERROR_TOO_MANY_REDIRECTS:return"Failed: too many redirects";case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:return"Failed: server HTTP response";case DownloadManager.ERROR_UNKNOWN:return"Failed: unknown DownloadManager error";default:return"Failed: HTTP/error code "+reason;}}
        return "Download state "+state+" • reason "+reason;
    }

    private static void cancelTracked(Context c,boolean deleteFile){long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);if(id>=0)try{((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);}catch(Exception ignored){}if(deleteFile){File f=modelFile(c);if(f.exists())f.delete();}c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY_ID).remove(KEY_VERIFIED).apply();}
    public static void remove(Context c){cancelTracked(c,true);}
    public static String human(long b){if(b<=0)return "";double g=b/1073741824.0;if(g>=1)return String.format(Locale.US,"%.2f GB",g);return String.format(Locale.US,"%.0f MB",b/1048576.0);}

    public static final class Status{public final String label;public final int percent;public final long done,total;public final boolean verified,failed;public final int reason;public final String detail;Status(String l,int p,long d,long t,boolean v,boolean f,int r,String x){label=l;percent=p;done=d;total=t;verified=v;failed=f;reason=r;detail=x==null?"":x;}}
    public static final class Verification{public final boolean ok;public final String message,actualSha;Verification(boolean o,String m,String a){ok=o;message=m;actualSha=a;}}
}
