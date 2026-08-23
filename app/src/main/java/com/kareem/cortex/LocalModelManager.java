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
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(MODEL_URL));
        r.setTitle("Cortex Local Brain");r.setDescription(MODEL_NAME+" • ~2.5 GB");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(false);r.setAllowedOverRoaming(false);
        r.setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,MODEL_FILE);
        long id=dm.enqueue(r);c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(KEY_ID,id).remove(KEY_VERIFIED).apply();return id;
    }

    public static Verification verify(Context c){
        File f=modelFile(c);if(!f.exists())return new Verification(false,"Model file is missing","");
        if(f.length()<2_000_000_000L)return new Verification(false,"Model file is incomplete","");
        try{
            if(!ggufHeader(f))return new Verification(false,"Invalid GGUF header","");
            String actual=sha256(f);
            if(!SHA256.equalsIgnoreCase(actual)){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY_VERIFIED).apply();return new Verification(false,"SHA-256 mismatch",actual);}
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_VERIFIED,SHA256).apply();return new Verification(true,"Model asset verified",actual);
        }catch(Exception e){return new Verification(false,"Verification failed: "+e.getClass().getSimpleName(),"");}
    }

    private static boolean ggufHeader(File f)throws Exception{try(InputStream in=new FileInputStream(f)){byte[] h=new byte[4];if(in.read(h)!=4)return false;return h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}}
    private static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f),4*1024*1024)){byte[] b=new byte[4*1024*1024];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}

    public static Status status(Context c){
        if(verified(c))return new Status("Verified model asset",100,size(c),size(c),true,false);
        long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);
        if(id<0){if(filePresent(c))return new Status("Download complete • verification required",100,size(c),size(c),false,false);return new Status("Not downloaded",0,size(c),0,false,false);}
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);Cursor q=dm.query(new DownloadManager.Query().setFilterById(id));
        if(q==null||!q.moveToFirst()){if(q!=null)q.close();return filePresent(c)?new Status("Download complete • verification required",100,size(c),size(c),false,false):new Status("Not downloaded",0,size(c),0,false,false);}
        int state=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));long done=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),total=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));q.close();
        String label=state==DownloadManager.STATUS_RUNNING?"Downloading":state==DownloadManager.STATUS_PAUSED?"Paused":state==DownloadManager.STATUS_PENDING?"Queued":state==DownloadManager.STATUS_SUCCESSFUL?"Download complete • verification required":state==DownloadManager.STATUS_FAILED?"Download failed":"Waiting";
        int pct=total>0?(int)Math.min(100,(done*100)/total):(filePresent(c)?100:0);return new Status(label,pct,done,total,false,state==DownloadManager.STATUS_FAILED);
    }

    public static void remove(Context c){long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);if(id>=0)((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);File f=modelFile(c);if(f.exists())f.delete();c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY_ID).remove(KEY_VERIFIED).apply();}
    public static String human(long b){if(b<=0)return "";double g=b/1073741824.0;if(g>=1)return String.format(Locale.US,"%.2f GB",g);return String.format(Locale.US,"%.0f MB",b/1048576.0);}

    public static final class Status{public final String label;public final int percent;public final long done,total;public final boolean verified,failed;Status(String l,int p,long d,long t,boolean v,boolean f){label=l;percent=p;done=d;total=t;verified=v;failed=f;}}
    public static final class Verification{public final boolean ok;public final String message,actualSha;Verification(boolean o,String m,String a){ok=o;message=m;actualSha=a;}}
}
