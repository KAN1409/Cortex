package com.kareem.cortex;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stable conversation-level routing for the guarded Cognitive V2 authority canary. */
public final class CognitiveAuthorityRouter {
    public enum Route { HARD_GATE, LEGACY, V2_CANARY }

    private CognitiveAuthorityRouter(){}

    public static Route route(Context context,long threadId,String source,String sender,boolean hardNoise){
        if(hardNoise)return Route.HARD_GATE;
        if(context==null||!CognitiveFeatureFlags.authorityCanaryEnabled(context))return Route.LEGACY;
        int percent=CognitiveFeatureFlags.canaryPercent(context);
        if(percent<=0)return Route.LEGACY;
        String key=threadId>0?"thread:"+threadId:"signal-family:"+clean(source)+"|"+clean(sender);
        return stableBucket(key)<percent?Route.V2_CANARY:Route.LEGACY;
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
