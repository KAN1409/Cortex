package com.kareem.cortex;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import java.io.File;

public final class LocalModelManager {
    public static final String MODEL_NAME="Qwen3-4B Q4_K_M";
    public static final String MODEL_FILE="Qwen3-4B-Q4_K_M.gguf";
    public static final String MODEL_URL="https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true";
    public static final String SHA256="ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328";
    private static final String PREF="cortex_local_model",KEY_ID="download_id";
    private LocalModelManager(){}

    public static File modelFile(Context c){File d=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);return d==null?new File(c.getFilesDir(),MODEL_FILE):new File(d,MODEL_FILE);}
    public static boolean installed(Context c){File f=modelFile(c);return f.exists()&&f.length()>2_000_000_000L;}
    public static long size(Context c){File f=modelFile(c);return f.exists()?f.length():0;}
    public static long startDownload(Context c){
        if(installed(c))return -1;
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(MODEL_URL));r.setTitle("Cortex Local Brain");r.setDescription(MODEL_NAME+" • ~2.5 GB");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setAllowedOverMetered(false);r.setAllowedOverRoaming(false);r.setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,MODEL_FILE);
        long id=dm.enqueue(r);c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(KEY_ID,id).apply();return id;
    }
    public static Status status(Context c){if(installed(c))return new Status("Installed",100,size(c),0);long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);if(id<0)return new Status("Not downloaded",0,size(c),0);DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);Cursor q=dm.query(new DownloadManager.Query().setFilterById(id));if(q==null||!q.moveToFirst()){if(q!=null)q.close();return new Status("Not downloaded",0,size(c),0);}int state=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));long done=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),total=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));q.close();String label=state==DownloadManager.STATUS_RUNNING?"Downloading":state==DownloadManager.STATUS_PAUSED?"Paused":state==DownloadManager.STATUS_PENDING?"Queued":state==DownloadManager.STATUS_SUCCESSFUL?"Downloaded":state==DownloadManager.STATUS_FAILED?"Download failed":"Waiting";int pct=total>0?(int)Math.min(100,(done*100)/total):0;return new Status(label,pct,done,total);}
    public static void remove(Context c){long id=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_ID,-1);if(id>=0)((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);File f=modelFile(c);if(f.exists())f.delete();c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();}
    public static String human(long b){if(b<=0)return "";double g=b/1073741824.0;if(g>=1)return String.format(java.util.Locale.US,"%.2f GB",g);return String.format(java.util.Locale.US,"%.0f MB",b/1048576.0);}
    public static final class Status{public final String label;public final int percent;public final long done,total;Status(String l,int p,long d,long t){label=l;percent=p;done=d;total=t;}}
}
