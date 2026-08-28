package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit migration switch so V2 can be observed before the legacy path is retired. */
public final class CognitiveFeatureFlags {
    public enum Mode { SHADOW, AUTHORITATIVE }
    private static final String PREF="cortex_cognitive_v2_flags";
    private static final String K_ENABLED="enabled",K_MODE="mode";
    private CognitiveFeatureFlags(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return c!=null&&p(c).getBoolean(K_ENABLED,true);}
    public static Mode mode(Context c){if(c==null)return Mode.SHADOW;String x=p(c).getString(K_MODE,Mode.SHADOW.name());try{return Mode.valueOf(x);}catch(Throwable ignored){return Mode.SHADOW;}}
    public static boolean authoritative(Context c){return enabled(c)&&mode(c)==Mode.AUTHORITATIVE;}
    public static boolean shadow(Context c){return enabled(c)&&mode(c)==Mode.SHADOW;}

    /** Internal migration tooling only; normal UI does not expose this as a casual preference. */
    public static void set(Context c,boolean enabled,Mode mode){if(c==null)return;p(c).edit().putBoolean(K_ENABLED,enabled).putString(K_MODE,(mode==null?Mode.SHADOW:mode).name()).apply();}
}
