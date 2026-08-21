package com.kareem.cortex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.File;
import java.util.Locale;

/** Persistent diagnostics plus one-shot local-ASR retry control. */
public final class WhisperRuntimeState {
    private static final String PREF="cortex_whisper_runtime";
    private static final String MODEL="ggml-egyptian-codeswitch-small-q5_1.bin";
    private WhisperRuntimeState(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public static void stage(Context c,String stage,String detail){
        p(c).edit().putString("stage",stage==null?"":stage)
                .putString("detail",detail==null?"":detail)
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void copyProgress(Context c,long copied,long total){
        p(c).edit().putString("stage","preparing local model")
                .putLong("downloaded",Math.max(0,copied))
                .putLong("total",Math.max(0,total))
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    // Kept for compatibility with older code paths/screens.
    public static void downloadProgress(Context c,long downloaded,long total){copyProgress(c,downloaded,total);}

    public static void error(Context c,Throwable e){
        String msg=e==null?"unknown":e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        p(c).edit().putString("stage","failed").putString("detail",msg)
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void forceWhisperOnly(Context c,long itemId){
        p(c).edit().putLong("force_item",itemId)
                .putString("stage","queued local ASR")
                .putString("detail","Preparing Egyptian Arabic + English code-switch model")
                .putLong("downloaded",0)
                .putLong("total",0)
                .putLong("updated",System.currentTimeMillis()).apply();
        try{
            Intent i=new Intent(c,WhisperProgressActivity.class).putExtra("item_id",itemId);
            if(!(c instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }catch(Exception ignored){}
    }

    public static boolean consumeWhisperOnly(Context c,long itemId){
        SharedPreferences s=p(c);long id=s.getLong("force_item",-1);
        if(id!=itemId)return false;
        s.edit().remove("force_item").apply();
        return true;
    }

    public static void setDownloadId(Context c,long id){}
    public static void clearDownloadId(Context c,long id){}

    public static String stageName(Context c){return p(c).getString("stage","not started");}
    public static String detailText(Context c){return p(c).getString("detail","");}
    public static long downloadedBytes(Context c){return p(c).getLong("downloaded",0);}
    public static long totalBytes(Context c){return p(c).getLong("total",0);}
    public static int progressPercent(Context c){
        long d=downloadedBytes(c),t=totalBytes(c);if(t<=0)return 0;
        return (int)Math.max(0,Math.min(100,(d*100L)/t));
    }
    public static String progressText(Context c){
        long d=downloadedBytes(c),t=totalBytes(c);
        if(t<=0)return d<=0?"Bundled model — no download required":String.format(Locale.US,"%.1f MB copied",d/1048576.0);
        return String.format(Locale.US,"%.1f / %.1f MB • %d%%",d/1048576.0,t/1048576.0,progressPercent(c));
    }

    public static String describe(Context c){
        SharedPreferences s=p(c);
        String stage=s.getString("stage","not started");
        String detail=s.getString("detail","");
        File model=new File(new File(c.getFilesDir(),"models"),MODEL);
        String modelState=model.exists()?String.format(Locale.US,"%.1f MB ready",model.length()/1048576.0):"bundled in app";
        String progress="preparing local model".equals(stage)?"\nProgress: "+progressText(c):"";
        return "Model: Egyptian Arabic + English code-switch • "+modelState+"\nStage: "+stage+progress+(detail==null||detail.isEmpty()?"":"\nDetail: "+detail);
    }
}
