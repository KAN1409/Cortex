package com.kareem.cortex;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stable conversation-level routing for the guarded Cognitive V2 authority canary. */
public final class CognitiveAuthorityRouter {
    public enum Route { HARD_GATE, LEGACY, V2_CANARY }

    public enum RoutingReason {
        HARD_NOISE,
        CANARY_DISABLED,
        VALIDATION_OVERRIDE,
        HASH_CANARY,
        HASH_LEGACY
    }

    public static final class Decision {
        public final Route route;
        public final RoutingReason reason;
        public final int bucket;

        Decision(Route route, RoutingReason reason, int bucket) {
            this.route = route;
            this.reason = reason;
            this.bucket = bucket;
        }
    }

    private CognitiveAuthorityRouter(){}

    public static Route route(Context context,long threadId,String source,String sender,boolean hardNoise){
        return routeDetailed(context,threadId,source,sender,hardNoise).route;
    }

    public static Decision routeDetailed(
            Context context,
            long threadId,
            String source,
            String sender,
            boolean hardNoise
    ){
        return routeInternal(context,threadId,source,sender,hardNoise,BuildConfig.DEBUG);
    }

    static Decision routeInternal(
            Context context,
            long threadId,
            String source,
            String sender,
            boolean hardNoise,
            boolean debugBuild
    ){
        String key=threadId>0?"thread:"+threadId:"signal-family:"+clean(source)+"|"+clean(sender);
        int bucket=stableBucket(key);

        if(hardNoise){
            return new Decision(Route.HARD_GATE,RoutingReason.HARD_NOISE,bucket);
        }
        if(context==null||!CognitiveFeatureFlags.authorityCanaryEnabled(context)){
            return new Decision(Route.LEGACY,RoutingReason.CANARY_DISABLED,bucket);
        }
        if(debugBuild
                &&threadId>0
                &&CognitiveFeatureFlags.validationOverrideEnabled(context,debugBuild)
                &&CognitiveFeatureFlags.validationThreadIds(context).contains(threadId)){
            return new Decision(Route.V2_CANARY,RoutingReason.VALIDATION_OVERRIDE,bucket);
        }

        int percent=CognitiveFeatureFlags.canaryPercent(context);
        return percent>0&&bucket<percent
                ?new Decision(Route.V2_CANARY,RoutingReason.HASH_CANARY,bucket)
                :new Decision(Route.LEGACY,RoutingReason.HASH_LEGACY,bucket);
    }

    static int stableBucket(String value){
        String key=value==null?"":value;
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            int n=((digest[0]&0xff)<<24)|((digest[1]&0xff)<<16)|((digest[2]&0xff)<<8)|(digest[3]&0xff);
            return (n&0x7fffffff)%100;
        }catch(Throwable ignored){
            int n=key.hashCode()%100;return n<0?-n:n;
        }
    }

    private static String clean(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
}
