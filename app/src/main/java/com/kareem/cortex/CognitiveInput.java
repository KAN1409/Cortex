package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, provider-neutral input passed to a Cortex cognitive brain. */
public final class CognitiveInput {
    public final long signalId;
    public final CognitiveSignalV2.SignalFamily signalFamily;
    public final String sourcePackage;
    public final String sourceApp;
    public final String sender;
    public final String latestText;
    public final List<String> recentContext;
    public final long occurredAt;
    public final String timezone;
    public final String baselineDecision;

    public CognitiveInput(long signalId,CognitiveSignalV2.SignalFamily signalFamily,String sourcePackage,
                          String sourceApp,String sender,String latestText,List<String> recentContext,
                          long occurredAt,String timezone,String baselineDecision){
        this.signalId=Math.max(0,signalId);
        this.signalFamily=signalFamily==null?CognitiveSignalV2.SignalFamily.UNKNOWN:signalFamily;
        this.sourcePackage=n(sourcePackage);this.sourceApp=n(sourceApp);this.sender=n(sender);this.latestText=n(latestText);
        ArrayList<String> bounded=new ArrayList<>();if(recentContext!=null)for(String x:recentContext){if(x==null||x.trim().isEmpty())continue;bounded.add(clip(x,1200));if(bounded.size()>=LocalBrainConfig.MAX_THREAD_HISTORY)break;}
        this.recentContext=Collections.unmodifiableList(bounded);
        this.occurredAt=Math.max(0,occurredAt);this.timezone=n(timezone);this.baselineDecision=n(baselineDecision);
    }

    private static String n(String s){return s==null?"":s.trim();}
    private static String clip(String s,int max){String x=n(s);return x.length()<=max?x:x.substring(0,max);}
}
