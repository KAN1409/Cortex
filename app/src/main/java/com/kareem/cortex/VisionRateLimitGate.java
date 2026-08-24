package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Global quota/cooldown guard shared by every Gemini Vision call. */
public final class VisionRateLimitGate {
    private static final String PREF="cortex_vision_rate_v1";
    private static final long WINDOW_MS=60_000L;
    private static final int SAFE_REQUESTS_PER_WINDOW=4;
    private static final long MIN_PROVIDER_COOLDOWN_MS=30_000L;
    private static final long DEFAULT_PROVIDER_COOLDOWN_MS=65_000L;
    private static final long MAX_PROVIDER_COOLDOWN_MS=15L*60L*1000L;
    private static final Pattern RETRY_SECONDS=Pattern.compile("(?i)retry\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)s");
    private VisionRateLimitGate(){}

    /** Returns 0 and reserves a request slot, or the milliseconds the caller should wait. */
    public static synchronized long beforeRequest(Context c){
        long now=System.currentTimeMillis();SharedPreferences p=p(c);long until=p.getLong("provider_cooldown_until",0);
        if(until>now)return until-now;
        long start=p.getLong("window_start",0);int count=p.getInt("window_count",0);
        if(start<=0||now-start>=WINDOW_MS){start=now;count=0;}
        if(count>=SAFE_REQUESTS_PER_WINDOW){long localUntil=Math.max(now+5_000L,start+WINDOW_MS+5_000L);p.edit().putLong("provider_cooldown_until",localUntil).putLong("window_start",start).putInt("window_count",count).apply();return localUntil-now;}
        p.edit().putLong("window_start",start).putInt("window_count",count+1).apply();return 0;
    }

    public static synchronized long markProviderRateLimited(Context c,String error){
        long retry=parseRetryMs(error);long wait=Math.max(MIN_PROVIDER_COOLDOWN_MS,retry>0?retry+5_000L:DEFAULT_PROVIDER_COOLDOWN_MS);wait=Math.min(MAX_PROVIDER_COOLDOWN_MS,wait);long until=System.currentTimeMillis()+wait;SharedPreferences p=p(c);long old=p.getLong("provider_cooldown_until",0);p.edit().putLong("provider_cooldown_until",Math.max(old,until)).apply();return Math.max(old,until)-System.currentTimeMillis();
    }

    public static synchronized long remainingMs(Context c){return Math.max(0,p(c).getLong("provider_cooldown_until",0)-System.currentTimeMillis());}
    public static synchronized void noteSuccess(Context c){SharedPreferences p=p(c);long until=p.getLong("provider_cooldown_until",0);if(until>0&&until<=System.currentTimeMillis())p.edit().remove("provider_cooldown_until").apply();}

    private static long parseRetryMs(String s){if(s==null)return 0;Matcher m=RETRY_SECONDS.matcher(s);if(!m.find())return 0;try{return (long)Math.ceil(Double.parseDouble(m.group(1))*1000.0);}catch(Exception ignored){return 0;}}
    private static SharedPreferences p(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
}
