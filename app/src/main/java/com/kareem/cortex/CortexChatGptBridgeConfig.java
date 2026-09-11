package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Local-only configuration for the private Cortex <-> ChatGPT teaching bridge. */
public final class CortexChatGptBridgeConfig {
    private static final String PREF="cortex_chatgpt_bridge";
    private CortexChatGptBridgeConfig(){}

    private static SharedPreferences p(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return p(c).getBoolean("enabled",false)&&!endpoint(c).isEmpty();}
    public static String endpoint(Context c){return p(c).getString("endpoint","").trim().replaceAll("/+$","");}
    public static String token(Context c){return p(c).getString("token","").trim();}
    public static String deviceId(Context c){
        SharedPreferences p=p(c);String id=p.getString("device_id","");if(id!=null&&!id.trim().isEmpty())return id;
        id=UUID.randomUUID().toString();p.edit().putString("device_id",id).apply();return id;
    }
    public static void save(Context c,boolean enabled,String endpoint,String token){p(c).edit().putBoolean("enabled",enabled).putString("endpoint",endpoint==null?"":endpoint.trim().replaceAll("/+$","")).putString("token",token==null?"":token.trim()).apply();}
    public static long lastSyncAt(Context c){return p(c).getLong("last_sync_at",0);}
    public static String lastError(Context c){return p(c).getString("last_error","");}
    static void record(Context c,boolean ok,String error){p(c).edit().putLong("last_sync_at",System.currentTimeMillis()).putString("last_error",ok?"":(error==null?"Unknown bridge error":error)).apply();}
}
