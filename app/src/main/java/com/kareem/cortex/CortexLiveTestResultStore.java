package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Local-only result ledger for protected/autopilot live tests. No binary payloads or secrets. */
public final class CortexLiveTestResultStore {
    private static final String PREF="cortex_protected_live_test_results_v1";
    private CortexLiveTestResultStore(){}

    public static void record(Context c,String key,boolean pass,String detail){record(c,key,pass?"PASS":"FAIL",detail,null);}
    public static void record(Context c,String key,String status,String detail){record(c,key,status,detail,null);}
    public static void record(Context c,String key,String status,String detail,JSONObject evidence){if(c==null||key==null||key.trim().isEmpty())return;try{JSONObject o=new JSONObject().put("key",key).put("status",status==null?"":status).put("detail",detail==null?"":detail).put("at",System.currentTimeMillis());if(evidence!=null)o.put("evidence",evidence);prefs(c).edit().putString(key,o.toString()).apply();}catch(Exception ignored){}}
    public static JSONObject get(Context c,String key){try{String s=prefs(c).getString(key,"");return s==null||s.isEmpty()?null:new JSONObject(s);}catch(Exception e){return null;}}
    public static void clear(Context c,String key){if(c!=null&&key!=null)prefs(c).edit().remove(key).apply();}
    public static void clearAll(Context c){if(c!=null)prefs(c).edit().clear().apply();}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
}
