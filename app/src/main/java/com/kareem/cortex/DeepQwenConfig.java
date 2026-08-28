package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;

/** Optional self-hosted Qwen3.5-4B vLLM endpoint. Disabled until explicitly configured. */
public final class DeepQwenConfig {
    public static final String MODEL="Qwen/Qwen3.5-4B";
    private static final String PREF="cortex_deep_qwen";
    private static final String K_ENABLED="enabled",K_BASE_URL="base_url",K_TOKEN="bearer_token";
    private DeepQwenConfig(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return c!=null&&p(c).getBoolean(K_ENABLED,false)&&!baseUrl(c).isEmpty();}
    public static String baseUrl(Context c){return c==null?"":clean(p(c).getString(K_BASE_URL,""));}
    public static String bearerToken(Context c){return c==null?"":clean(p(c).getString(K_TOKEN,""));}

    /** Settings surface can call this later; no server is enabled by default. */
    public static void save(Context c,boolean enabled,String baseUrl,String bearerToken){
        if(c==null)return;p(c).edit().putBoolean(K_ENABLED,enabled).putString(K_BASE_URL,clean(baseUrl)).putString(K_TOKEN,clean(bearerToken)).apply();
    }

    private static String clean(String s){String x=s==null?"":s.trim();while(x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}
}
