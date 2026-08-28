package com.kareem.cortex;

/** Provider-neutral completion telemetry returned by any CortexBrain. */
public final class BrainCompletion {
    public final String text,provider,model;
    public final long latencyMs;
    public final int tokensGenerated;
    public final float tokensPerSecond;
    public final boolean cacheHit;

    public BrainCompletion(String text,String provider,String model,long latencyMs,int tokensGenerated,float tokensPerSecond,boolean cacheHit){
        this.text=text==null?"":text.trim();this.provider=provider==null?"":provider;this.model=model==null?"":model;
        this.latencyMs=Math.max(0,latencyMs);this.tokensGenerated=Math.max(0,tokensGenerated);this.tokensPerSecond=Math.max(0,tokensPerSecond);this.cacheHit=cacheHit;
    }
}
