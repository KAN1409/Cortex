package com.kareem.cortex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.File;
import java.util.Locale;

/** Persistent Whisper runtime diagnostics plus one-shot whisper-only retry control. */
public final class WhisperRuntimeState {
    private static final String PREF="cortex_whisper_runtime";
    private WhisperRuntimeState(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public static void stage(Context c,String stage,String detail){
        p(c).edit().putString("stage",stage==null?"":stage)
                .putString("detail",detail==null?"":detail)
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void downloadProgress(Context c,long downloaded,long total){
        p(c).edit().putString("stage","downloading model")
                .putLong("downloaded",Math.max(0,downloaded))
                .putLong("total",Math.max(0,total))
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void error(Context c,Throwable e){
        String msg=e==null?"unknown":e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        p(c).edit().putString("stage","failed").putString("detail",msg)
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void forceWhisperOnly(Context c,long itemId){
        p(c).edit().putLong("force_item",itemId)
                .putString("stage","queued whisper")
                .putString("detail","Preparing Whisper small multilingual model")
                .putLong("downloaded",0)
                .putLong("total",488000000L)
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

    public static void setDownloadId(Context c,long id){p(c).edit().putLong("download_id",id).apply();}
    public static void clearDownloadId(Context c,long id){SharedPreferences s=p(c);if(s.getLong("download_id",-1)==id)s.edit().remove("download_id").apply();}

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
        if(t<=0)return String.format(Locale.US,"%.1f MB",d/1048576.0);
        return String.format(Locale.US,"%.1f / %.1f MB • %d%%",d/1048576.0,t/1048576.0,progressPercent(c));
    }

    public static String describe(Context c){
        SharedPreferences s=p(c);
        String stage=s.getString("stage","not started");
        String detail=s.getString("detail","");
        File model=new File(new File(c.getFilesDir(),"models"),"ggml-small.bin");
        String modelState=model.exists()?String.format(Locale.US,"%.1f MB",model.length()/1048576.0):"not downloaded";
        String progress="downloading model".equals(stage)?"\nProgress: "+progressText(c):"";
        return "Model: small multilingual • "+modelState+"\nStage: "+stage+progress+(detail==null||detail.isEmpty()?"":"\nDetail: "+detail);
    }
}
