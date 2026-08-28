package com.kareem.cortex;

public final class LocalBrainRun {

    public final CognitiveResult result;
    public final String rawOutput;
    public final long durationMs;
    public final long modelLoadMs;
    public final long generationMs;
    public final int tokensGenerated;
    public final float tokensPerSecond;
    public final boolean cacheHit;

    public LocalBrainRun(
            CognitiveResult result,
            String rawOutput,
            long durationMs,
            long modelLoadMs,
            long generationMs,
            int tokensGenerated,
            float tokensPerSecond,
            boolean cacheHit
    ) {
        this.result = result;
        this.rawOutput = rawOutput;
        this.durationMs = durationMs;
        this.modelLoadMs = modelLoadMs;
        this.generationMs = generationMs;
        this.tokensGenerated = tokensGenerated;
        this.tokensPerSecond = tokensPerSecond;
        this.cacheHit = cacheHit;
    }
}
