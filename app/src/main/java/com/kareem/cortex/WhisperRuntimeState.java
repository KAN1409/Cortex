package com.kareem.cortex;

import android.content.Context;
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

    public static void error(Context c,Throwable e){
        String msg=e==null?"unknown":e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        p(c).edit().putString("stage","failed").putString("detail",msg)
                .putLong("updated",System.currentTimeMillis()).apply();
    }

    public static void forceWhisperOnly(Context c,long itemId){p(c).edit().putLong("force_item",itemId).apply();}

    public static boolean consumeWhisperOnly(Context c,long itemId){
        SharedPreferences s=p(c);long id=s.getLong("force_item",-1);
        if(id!=itemId)return false;
        s.edit().remove("force_item").apply();
        return true;
    }

    public static String describe(Context c){
        SharedPreferences s=p(c);
        String stage=s.getString("stage","not started");
        String detail=s.getString("detail","");
        File model=new File(new File(c.getFilesDir(),"models"),"ggml-small.bin");
        String modelState=model.exists()?String.format(Locale.US,"%.1f MB",model.length()/1048576.0):"not downloaded";
        return "Model: small multilingual • "+modelState+"\nStage: "+stage+(detail==null||detail.isEmpty()?"":"\nDetail: "+detail);
    }
}
