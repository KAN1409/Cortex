package com.kareem.cortex;

public final class BrainNode {
    public final String type,label;
    public final int mentions;
    public final long latestItemId,latestAt;
    public BrainNode(String type,String label,int mentions,long latestItemId,long latestAt){this.type=type;this.label=label;this.mentions=mentions;this.latestItemId=latestItemId;this.latestAt=latestAt;}
}
