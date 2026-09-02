package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Small persistent circuit-breaker state for external Brain providers. */
public final class ExternalProviderHealthStore {
    private static final String PREF="cortex_external_provider_health_v1";
    private static final long RATE_LIMIT_MS=65_000L;
    private static final long SERVICE_ERROR_MS=25_000L;
    private static final long NETWORK_ERROR_MS=15_000L;
    private ExternalProviderHealthStore(){}

    public static synchronized void noteSuccess(Context c,String provider,long latencyMs){
        long now=System.currentTimeMillis();String p=key(provider);prefs(c).edit()
            .putString(p+"_status","healthy")
            .putInt(p+"_http",200)
            .putString(p+"_error","")
            .putLong(p+"_latency",Math.max(0,latencyMs))
            .putLong(p+"_last_success",now)
            .remove(p+"_cooldown_until")
            .apply();
    }

    public static synchronized long noteFailure(Context c,String provider,Throwable error){
        String p=key(provider);long now=System.currentTimeMillis();int http=0;long latency=0;String detail=error==null?"unknown":safe(error.getMessage());String status="failed";long wait=0;
        if(error instanceof ExternalBrainProvider.ProviderException){ExternalBrainProvider.ProviderException e=(ExternalBrainProvider.ProviderException)error;http=e.httpCode;latency=e.latencyMs;if(e.rateLimited()){status="rate_limited";wait=RATE_LIMIT_MS;}else if(e.retryable()){status="temporary_failure";wait=SERVICE_ERROR_MS;}else status="request_failed";}
        else if(error instanceof java.io.IOException){status="network_failure";wait=NETWORK_ERROR_MS;}
        long until=wait>0?now+wait:0;SharedPreferences.Editor edit=prefs(c).edit().putString(p+"_status",status).putInt(p+"_http",http).putString(p+"_error",clip(detail,500)).putLong(p+"_latency",Math.max(0,latency)).putLong(p+"_last_failure",now);
        if(until>0)edit.putLong(p+"_cooldown_until",Math.max(until,prefs(c).getLong(p+"_cooldown_until",0)));else edit.remove(p+"_cooldown_until");edit.apply();return remainingMs(c,provider);
    }

    public static synchronized long noteHealth(Context c,ExternalBrainProvider.HealthReport h){
        if(h==null)return 0;if(h.ok){noteSuccess(c,h.provider,h.latencyMs);return 0;}
        ExternalBrainProvider.ProviderException e=new ExternalBrainProvider.ProviderException(h.error.isEmpty()?h.status:h.error,h.httpCode,h.model,h.provider,h.latencyMs);return noteFailure(c,h.provider,e);
    }

    public static synchronized long remainingMs(Context c,String provider){return Math.max(0,prefs(c).getLong(key(provider)+"_cooldown_until",0)-System.currentTimeMillis());}
    public static synchronized boolean cooling(Context c,String provider){return remainingMs(c,provider)>0;}

    public static synchronized Snapshot snapshot(Context c,String provider){String p=key(provider);SharedPreferences s=prefs(c);return new Snapshot(provider,s.getString(p+"_status","unknown"),s.getInt(p+"_http",0),s.getString(p+"_error",""),s.getLong(p+"_latency",0),s.getLong(p+"_cooldown_until",0),s.getLong(p+"_last_success",0),s.getLong(p+"_last_failure",0));}

    public static synchronized JSONObject json(Context c,String provider){Snapshot s=snapshot(c,provider);JSONObject o=new JSONObject();try{o.put("provider",s.provider).put("status",s.status).put("http",s.httpCode).put("error",s.error).put("latency_ms",s.latencyMs).put("cooldown_until",s.cooldownUntil).put("cooldown_remaining_ms",Math.max(0,s.cooldownUntil-System.currentTimeMillis())).put("last_success",s.lastSuccess).put("last_failure",s.lastFailure);}catch(Exception ignored){}return o;}

    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static String key(String p){String x=safe(p).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","_");return x.isEmpty()?"provider":x;}
    private static String safe(String s){return s==null?"":s;}
    private static String clip(String s,int n){String x=safe(s);return x.length()<=n?x:x.substring(0,n)+"…";}

    public static final class Snapshot{
        public final String provider,status,error;public final int httpCode;public final long latencyMs,cooldownUntil,lastSuccess,lastFailure;
        Snapshot(String provider,String status,int http,String error,long latency,long cooldown,long success,long failure){this.provider=provider;this.status=status;this.httpCode=http;this.error=error;this.latencyMs=latency;this.cooldownUntil=cooldown;this.lastSuccess=success;this.lastFailure=failure;}
    }
}
