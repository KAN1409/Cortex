package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent guardrails and observability for autonomous cloud reasoning. */
public final class CognitiveAutoReasoningSettingsV4 {
    private static final String PREF="cortex_auto_reasoning_v4";
    private static final String K_ENABLED="enabled",K_LAST_STARTED="last_started",K_LAST_SUCCESS="last_success",K_LAST_STARTED_FP="last_started_fp",K_LAST_SUCCESS_FP="last_success_fp",K_NEXT_ALLOWED="next_allowed",K_FAILURES="failures",K_DAY="day",K_DAY_CALLS="day_calls",K_LAST_ENQUEUE="last_enqueue";
    private static final String K_LAST_PIPELINE_STATE="last_pipeline_state",K_LAST_PIPELINE_REASON="last_pipeline_reason",K_LAST_PIPELINE_TRIGGER="last_pipeline_trigger",K_LAST_PIPELINE_AT="last_pipeline_at";
    static final long NORMAL_COOLDOWN_MS=4L*60L*1000L;
    static final long URGENT_COOLDOWN_MS=45L*1000L;
    static final long ENQUEUE_DEBOUNCE_MS=3500L;
    static final int MAX_CALLS_PER_DAY=24;
    private CognitiveAutoReasoningSettingsV4(){}

    public static boolean enabled(Context c){return c!=null&&c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(K_ENABLED,true);}
    public static void setEnabled(Context c,boolean enabled){if(c!=null){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(K_ENABLED,enabled).apply();markPipeline(c,enabled?"ENABLED":"DISABLED",enabled?"user_enabled":"user_disabled","settings");}}

    /**
     * Coalesce bursty canonical-change callbacks before they become WorkManager chain nodes. The
     * claim is synchronized in-process and persisted so a process restart during the short debounce
     * window still sees the slot. Forced stale-context recovery bypasses the normal debounce.
     */
    static synchronized boolean claimEnqueueSlot(Context c,long now,boolean force){
        if(c==null)return false;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);long last=p.getLong(K_LAST_ENQUEUE,0);if(!force&&!enqueueDebounceElapsed(last,now))return false;p.edit().putLong(K_LAST_ENQUEUE,now).apply();return true;
    }
    static synchronized void releaseEnqueueSlot(Context c,long claimedAt){
        if(c==null)return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);if(p.getLong(K_LAST_ENQUEUE,0)==claimedAt)p.edit().putLong(K_LAST_ENQUEUE,0).apply();
    }
    static boolean enqueueDebounceElapsed(long last,long now){return last<=0||now<last||now-last>=ENQUEUE_DEBOUNCE_MS;}

    static Gate canStart(Context c,String fingerprint,boolean urgent,long now){
        if(c==null)return new Gate(false,"no_context");
        if(!enabled(c))return new Gate(false,"disabled");
        if(!GeminiKeyStore.has(c))return new Gate(false,"gemini_key_missing");
        String fp=n(fingerprint);if(fp.isEmpty())return new Gate(false,"empty_fingerprint");
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);rollDay(p,now);
        if(fp.equals(n(p.getString(K_LAST_SUCCESS_FP,""))))return new Gate(false,"already_reasoned");
        if(now<p.getLong(K_NEXT_ALLOWED,0))return new Gate(false,"backoff");
        if(p.getInt(K_DAY_CALLS,0)>=MAX_CALLS_PER_DAY)return new Gate(false,"daily_budget");
        long last=p.getLong(K_LAST_STARTED,0),cooldown=urgent?URGENT_COOLDOWN_MS:NORMAL_COOLDOWN_MS;
        if(last>0&&now-last<cooldown)return new Gate(false,"cooldown");
        return new Gate(true,"ready");
    }

    static void markStarted(Context c,String fingerprint,long now){
        if(c==null)return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);rollDay(p,now);
        p.edit().putLong(K_LAST_STARTED,now).putString(K_LAST_STARTED_FP,n(fingerprint)).putInt(K_DAY_CALLS,p.getInt(K_DAY_CALLS,0)+1).apply();
    }
    static void markSuccess(Context c,String fingerprint,boolean urgent,long now){
        if(c==null)return;c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(K_LAST_SUCCESS,now).putString(K_LAST_SUCCESS_FP,n(fingerprint)).putInt(K_FAILURES,0).putLong(K_NEXT_ALLOWED,now+(urgent?URGENT_COOLDOWN_MS:NORMAL_COOLDOWN_MS)).apply();
    }
    static void markFailure(Context c,long now){
        if(c==null)return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);int failures=Math.min(6,p.getInt(K_FAILURES,0)+1);long delay=Math.min(60L*60L*1000L,(1L<<Math.max(0,failures-1))*60L*1000L);p.edit().putInt(K_FAILURES,failures).putLong(K_NEXT_ALLOWED,now+delay).apply();
    }
    /** A stale provider response is not a provider failure; allow immediate reasoning on fresh context. */
    static void clearTransientGateAfterStaleContext(Context c){
        if(c==null)return;c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(K_LAST_STARTED,0).putString(K_LAST_STARTED_FP,"").putLong(K_NEXT_ALLOWED,0).apply();
    }
    /** A real provider health PASS proves the transport recovered; clear only transient failure/cooldown state. */
    static void markProviderHealthy(Context c){
        if(c==null)return;c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt(K_FAILURES,0).putLong(K_NEXT_ALLOWED,0).putLong(K_LAST_STARTED,0).putString(K_LAST_STARTED_FP,"").apply();
    }
    static long lastSuccessAt(Context c){return c==null?0:c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(K_LAST_SUCCESS,0);}

    /** Persist the last autonomous-brain transition so a missing Gemini run is explainable on-device. */
    public static void markPipeline(Context c,String state,String reason,String trigger){
        if(c==null)return;c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(K_LAST_PIPELINE_STATE,n(state)).putString(K_LAST_PIPELINE_REASON,n(reason))
                .putString(K_LAST_PIPELINE_TRIGGER,n(trigger)).putLong(K_LAST_PIPELINE_AT,System.currentTimeMillis()).apply();
    }
    public static String lastPipelineState(Context c){return pref(c).getString(K_LAST_PIPELINE_STATE,"");}
    public static String lastPipelineReason(Context c){return pref(c).getString(K_LAST_PIPELINE_REASON,"");}
    public static String lastPipelineTrigger(Context c){return pref(c).getString(K_LAST_PIPELINE_TRIGGER,"");}
    public static long lastPipelineAt(Context c){return pref(c).getLong(K_LAST_PIPELINE_AT,0);}

    private static SharedPreferences pref(Context c){if(c==null)throw new IllegalArgumentException("context required");return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static void rollDay(SharedPreferences p,long now){String day=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date(now));if(!day.equals(p.getString(K_DAY,"")))p.edit().putString(K_DAY,day).putInt(K_DAY_CALLS,0).apply();}
    private static String n(String s){return s==null?"":s.trim();}
    static final class Gate{final boolean allowed;final String reason;Gate(boolean allowed,String reason){this.allowed=allowed;this.reason=reason;}}
}
