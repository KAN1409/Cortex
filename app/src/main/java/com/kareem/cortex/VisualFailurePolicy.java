package com.kareem.cortex;

import java.io.*;
import java.net.*;

/** Deterministic recovery policy for visual understanding. No failure retries forever. */
public final class VisualFailurePolicy {
    public static final int MAX_TRANSIENT_ATTEMPTS=3;

    public static final class Decision {
        public final String kind;
        public final boolean recoverable;
        public final boolean countsAttempt;
        public final long retryAfterMs;
        public final String nextAction;
        Decision(String kind,boolean recoverable,boolean countsAttempt,long retryAfterMs,String nextAction){this.kind=kind;this.recoverable=recoverable;this.countsAttempt=countsAttempt;this.retryAfterMs=Math.max(0,retryAfterMs);this.nextAction=nextAction==null?"":nextAction;}
    }

    private VisualFailurePolicy(){}

    public static Decision classify(Throwable error,int previousAttempts){
        if(error instanceof FileNotFoundException)return terminal("missing_attachment","The archived image is missing. Restore/re-import the original image to analyze it.");
        if(error instanceof SecurityException)return terminal("attachment_access","Cortex cannot read the archived image. Re-import it or restore file access.");
        if(error instanceof OutOfMemoryError)return bounded("memory_pressure",previousAttempts,90_000L,"Cortex will retry with a fresh worker. If it repeats, re-import a smaller image.");

        if(error instanceof GeminiVisionAnalyzer.VisionException){
            GeminiVisionAnalyzer.VisionException e=(GeminiVisionAnalyzer.VisionException)error;
            if(e.rateLimited())return new Decision("provider_rate_limit",true,false,Math.max(30_000L,e.retryAfterMs),"No data was lost. Cortex will retry after the provider cooldown.");
            if(e.httpCode==401||e.httpCode==403)return terminal("provider_auth","Check or replace the Gemini key, then retry visual understanding.");
            if(e.httpCode==400||e.httpCode==404)return terminal("provider_request","The provider rejected this request. Retry after updating the vision provider/model configuration.");
            if(e.retryable)return bounded(e.httpCode>=500?"provider_5xx":"provider_transient",previousAttempts,backoff(previousAttempts),"Cortex will retry automatically with bounded backoff.");
            if(e.httpCode==200)return terminal("model_response","The provider responded but did not return usable structured visual understanding. Explicit retry remains available.");
            return terminal("provider_rejected","The provider rejected this visual request. Check provider diagnostics before retrying.");
        }

        if(error instanceof SocketTimeoutException)return bounded("network_timeout",previousAttempts,backoff(previousAttempts),"Cortex will retry automatically when network/provider conditions recover.");
        if(error instanceof ConnectException||error instanceof UnknownHostException||error instanceof SocketException)return bounded("network_unavailable",previousAttempts,backoff(previousAttempts),"Cortex will retry automatically when the network is usable.");
        if(error instanceof InterruptedIOException)return bounded("io_interrupted",previousAttempts,backoff(previousAttempts),"Cortex will retry automatically in a fresh worker.");
        if(error instanceof IOException){String m=message(error).toLowerCase();if(m.contains("decode")||m.contains("payload size")||m.contains("compression"))return terminal("image_input","The archived image could not be prepared safely. Re-import a valid/smaller image to retry.");return bounded("io_transient",previousAttempts,backoff(previousAttempts),"Cortex will retry automatically with bounded backoff.");}
        if(error instanceof IllegalStateException&&message(error).toLowerCase().contains("key"))return terminal("provider_setup","Configure the vision provider, then retry.");
        return terminal("unexpected","Visual understanding stopped safely. Review diagnostics before an explicit retry.");
    }

    private static Decision bounded(String kind,int previous,long wait,String action){
        if(previous+1>=MAX_TRANSIENT_ATTEMPTS)return terminal(kind+"_exhausted","Automatic retries were exhausted. Use explicit Retry after checking the image/provider.");
        return new Decision(kind,true,true,wait,action);
    }
    private static Decision terminal(String kind,String action){return new Decision(kind,false,false,0,action);}
    private static long backoff(int previous){long base=30_000L;int n=Math.max(0,Math.min(3,previous));return Math.min(5L*60L*1000L,base*(1L<<n));}
    private static String message(Throwable e){return e==null||e.getMessage()==null?"":e.getMessage();}
}
