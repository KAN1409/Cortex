package com.kareem.cortex;

import android.content.*;

public final class PrivacyPolicy {
    public static final String AI_ALLOWED="ai_allowed",LOCAL_ONLY="local_only",NEVER="never";
    private static final String PREF="cortex_privacy_v1";
    private PrivacyPolicy(){}
    public static String mode(Context c,String source){String def=("contacts".equals(source)||"calendar".equals(source))?LOCAL_ONLY:AI_ALLOWED;return c.getSharedPreferences(PREF,0).getString(source,def);}
    public static void set(Context c,String source,String mode){c.getSharedPreferences(PREF,0).edit().putString(source,mode).apply();}
    public static boolean canCollect(Context c,String source){return !NEVER.equals(mode(c,source));}
    public static boolean canUseCloud(Context c,String source){return AI_ALLOWED.equals(mode(c,source));}
    public static String label(String m){if(LOCAL_ONLY.equals(m))return "Local only";if(NEVER.equals(m))return "Never collect";return "AI allowed";}
}
