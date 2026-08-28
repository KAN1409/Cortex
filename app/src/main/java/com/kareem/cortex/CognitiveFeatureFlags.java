package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;

/** Migration switches for Cognitive Brain V2. Shadow mode never owns production decisions. */
public final class CognitiveFeatureFlags {
    private static final String PREFS="cortex_cognitive_flags";
    private static final String V2_SHADOW="cognitive_v2_shadow";

    private CognitiveFeatureFlags(){}

    public static boolean shadowEnabled(Context context){
        if(context==null)return false;
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(V2_SHADOW,BuildConfig.DEBUG);
    }

    public static void setShadowEnabled(Context context,boolean enabled){
        if(context==null)return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean(V2_SHADOW,enabled).apply();
    }
}
