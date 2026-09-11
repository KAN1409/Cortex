package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Compact policy distilled by ChatGPT and applied locally by Cortex.
 *
 * v83 contract: this object stores bounded parameters for CortexAttentionJudge only.
 * It must never independently suppress, rank, or surface UI/read-model items.
 */
public final class CortexPersonalPolicy {
    private static final String PREF="cortex_personal_policy";
    private static final String KEY="policy_json";
    private static final long DEFAULT_TTL=7L*24L*60L*60L*1000L;
    private CortexPersonalPolicy(){}

    private static SharedPreferences p(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public static JSONObject current(Context c){
        try{
            String raw=p(c).getString(KEY,"");
            if(raw!=null&&!raw.trim().isEmpty()){
                JSONObject o=new JSONObject(raw);
                long generated=o.optLong("generatedAt",0);
                long ttl=Math.max(60_000L,o.optLong("ttlMs",DEFAULT_TTL));
                if(generated>0&&System.currentTimeMillis()-generated<=ttl)return o;
            }
        }catch(Throwable ignored){}
        return bootstrap();
    }

    private static JSONObject bootstrap(){
        try{
            JSONObject o=new JSONObject();
            o.put("version","local-bootstrap-4");
            o.put("generatedAt",System.currentTimeMillis());
            o.put("ttlMs",DEFAULT_TTL);
            o.put("attentionThreshold",.72);
            o.put("maxNowItems",7);
            o.put("suppressPhrases",new JSONArray());
            o.put("boosts",new JSONArray());
            return o;
        }catch(Exception e){return new JSONObject();}
    }

    public static void save(Context c,JSONObject policy){
        if(policy==null)return;
        try{
            JSONObject normalized=new JSONObject(policy.toString());
            long now=System.currentTimeMillis();
            if(normalized.optLong("generatedAt",0)<=0)normalized.put("generatedAt",now);
            normalized.put("ttlMs",Math.max(60_000L,Math.min(30L*24L*60L*60L*1000L,normalized.optLong("ttlMs",DEFAULT_TTL))));
            normalized.put("attentionThreshold",Math.max(0,Math.min(1,normalized.optDouble("attentionThreshold",.72))));
            normalized.put("maxNowItems",Math.max(1,Math.min(12,normalized.optInt("maxNowItems",7))));
            p(c).edit().putString(KEY,normalized.toString()).apply();
        }catch(Throwable ignored){}
    }

    public static void clear(Context c){p(c).edit().remove(KEY).apply();}
    public static String version(Context c){return current(c).optString("version","none");}
    public static int maxNowItems(Context c){return Math.max(1,Math.min(12,current(c).optInt("maxNowItems",7)));}
    public static double attentionThreshold(Context c){return Math.max(0,Math.min(1,current(c).optDouble("attentionThreshold",.72)));}

    /**
     * Legacy compatibility only.
     * Teacher policy is forbidden from suppressing presentation outside CortexAttentionJudge.
     */
    @Deprecated
    public static boolean suppress(Context c,PrimeBriefStore.Item item){return item==null;}

    /**
     * Legacy compatibility only.
     * Returns storage importance without applying teacher boosts. Final scoring belongs to the judge.
     */
    @Deprecated
    public static double score(Context c,PrimeBriefStore.Item item){
        if(item==null)return -999;
        return Math.max(0,Math.min(100,item.importance))/100.0;
    }

    /**
     * Legacy compatibility only.
     * Threshold application belongs exclusively to CortexAttentionJudge.
     */
    @Deprecated
    public static boolean belowThreshold(Context c,PrimeBriefStore.Item item){return false;}
}
