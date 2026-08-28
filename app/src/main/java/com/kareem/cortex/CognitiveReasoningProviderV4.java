package com.kareem.cortex;

import android.content.Context;

/** Provider contract for Cortex autonomous cognitive reasoning. */
public interface CognitiveReasoningProviderV4 {
    boolean configured(Context context);
    String id();
    String model(Context context);
    Result reason(Context context,CognitiveDeepBrainPacketBuilderV4.Packet packet)throws Exception;

    final class Result{
        public final String rawResponse,provider,model;
        public final long durationMs;
        public Result(String rawResponse,String provider,String model,long durationMs){this.rawResponse=rawResponse==null?"":rawResponse;this.provider=provider==null?"":provider;this.model=model==null?"":model;this.durationMs=Math.max(0,durationMs);}
    }
}
