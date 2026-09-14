package com.kareem.cortex;

/** Pure classification for user-facing pipeline status. */
final class PipelineStatusPolicy {
    private PipelineStatusPolicy(){}

    static Counts classify(long semanticWaiting,long semanticBlocked,long mediaQueued,boolean semanticCapabilityReady){
        long waiting=Math.max(0,semanticWaiting);
        long blocked=Math.max(0,semanticBlocked);
        long media=Math.max(0,mediaQueued);
        long activeSemantic=semanticCapabilityReady?waiting:0;
        long paused=blocked+(semanticCapabilityReady?0:waiting);
        return new Counts(activeSemantic+media,paused);
    }

    static final class Counts{
        final long processing,paused;
        Counts(long processing,long paused){this.processing=processing;this.paused=paused;}
    }
}
