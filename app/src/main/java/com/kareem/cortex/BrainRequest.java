package com.kareem.cortex;

/** Provider-neutral bounded prompt passed through CortexBrain implementations. */
public final class BrainRequest {
    public final String systemPrompt;
    public final String userPrompt;
    public final int maxTokens;

    public BrainRequest(String systemPrompt,String userPrompt,int maxTokens){
        this.systemPrompt=systemPrompt==null?"":systemPrompt;
        this.userPrompt=userPrompt==null?"":userPrompt;
        this.maxTokens=Math.max(1,Math.min(512,maxTokens));
    }
}
