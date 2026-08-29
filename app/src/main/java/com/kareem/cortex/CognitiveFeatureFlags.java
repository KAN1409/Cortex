package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;

/** Migration switches for Cognitive Brain V2. All authority switches are instant kill-switches. */
public final class CognitiveFeatureFlags {
    private static final String PREFS="cortex_cognitive_flags";
    private static final String V2_SHADOW="cognitive_v2_shadow";
    private static final String V2_AUTHORITY_CANARY="cognitive_v2_authority_canary";
    private static final String V2_CANARY_PERCENT="cognitive_v2_canary_percent";

    private static final boolean DEFAULT_SHADOW=true;
    private static final boolean DEFAULT_AUTHORITY_CANARY=true;
    private static final int DEFAULT_CANARY_PERCENT=5;

    private CognitiveFeatureFlags(){}

    public static boolean shadowEnabled(Context context){
        SharedPreferences p=prefs(context);return p!=null&&p.getBoolean(V2_SHADOW,DEFAULT_SHADOW);
    }

    public static void setShadowEnabled(Context context,boolean enabled){
        SharedPreferences p=prefs(context);if(p!=null)p.edit().putBoolean(V2_SHADOW,enabled).apply();
    }

    public static boolean authorityCanaryEnabled(Context context){
        SharedPreferences p=prefs(context);return p!=null&&p.getBoolean(V2_AUTHORITY_CANARY,DEFAULT_AUTHORITY_CANARY);
    }

    public static void setAuthorityCanaryEnabled(Context context,boolean enabled){
        SharedPreferences p=prefs(context);if(p!=null)p.edit().putBoolean(V2_AUTHORITY_CANARY,enabled).apply();
    }

    public static int canaryPercent(Context context){
        SharedPreferences p=prefs(context);int value=p==null?0:p.getInt(V2_CANARY_PERCENT,DEFAULT_CANARY_PERCENT);return clampPercent(value);
    }

    public static void setCanaryPercent(Context context,int percent){
        SharedPreferences p=prefs(context);if(p!=null)p.edit().putInt(V2_CANARY_PERCENT,clampPercent(percent)).apply();
    }

    static int clampPercent(int value){return Math.max(0,Math.min(100,value));}

    private static SharedPreferences prefs(Context context){
        return context==null?null:context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
