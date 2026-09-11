package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Compact policy distilled by ChatGPT and applied locally by Cortex.
 * Raw evidence never changes; this policy only affects user-facing relevance and ranking.
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
                JSONObject o=new JSONObject(raw);long generated=o.optLong("generatedAt",0),ttl=Math.max(60_000L,o.optLong("ttlMs",DEFAULT_TTL));
                if(generated>0&&System.currentTimeMillis()-generated<=ttl)return o;
            }
        }catch(Throwable ignored){}
        return bootstrap();
    }
    private static JSONObject bootstrap(){
        try{JSONObject o=new JSONObject();o.put("version","local-bootstrap-3");o.put("generatedAt",System.currentTimeMillis());o.put("ttlMs",DEFAULT_TTL);o.put("attentionThreshold",.72);o.put("maxNowItems",7);o.put("suppressPhrases",new JSONArray().put("cortex is processing").put("ask brain to interpret the intent").put("notification hints are evidence").put("resolved downstream").put("understanding...").put("check important info"));o.put("boosts",new JSONArray());return o;}catch(Exception e){return new JSONObject();}
    }
    public static void save(Context c,JSONObject policy){
        if(policy==null)return;
        try{
            JSONObject normalized=new JSONObject(policy.toString());long now=System.currentTimeMillis();
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

    public static boolean suppress(Context c,PrimeBriefStore.Item item){
        if(item==null)return true;String hay=norm((item.source==null?"":item.source)+" "+(item.title==null?"":item.title)+" "+(item.body==null?"":item.body));if(hay.isEmpty())return true;
        JSONArray a=current(c).optJSONArray("suppressPhrases");if(a!=null)for(int i=0;i<a.length();i++){String q=norm(a.optString(i,""));if(q.length()>=2&&hay.contains(q))return true;}
        return false;
    }

    /** Returns a display-ranking score; positive/negative learned boosts are bounded. */
    public static double score(Context c,PrimeBriefStore.Item item){
        if(item==null)return -999;double base=Math.max(0,Math.min(100,item.importance))/100.0;String source=norm(item.source);String hay=norm((item.source==null?"":item.source)+" "+(item.title==null?"":item.title)+" "+(item.body==null?"":item.body));
        if(source.contains("picbrain")||source.contains("knowledge_v2")||source.contains("screenshot"))base-=.18;
        JSONArray a=current(c).optJSONArray("boosts");double delta=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b==null)continue;String q=norm(b.optString("match",""));if(q.length()<2||!hay.contains(q))continue;delta+=Math.max(-1,Math.min(1,b.optDouble("weight",0)));}
        return Math.max(0,Math.min(1.5,base+Math.max(-.75,Math.min(.75,delta))));
    }

    public static boolean belowThreshold(Context c,PrimeBriefStore.Item item){return score(c,item)<attentionThreshold(c);}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
