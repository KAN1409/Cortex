package com.kareem.cortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Chooses when a new canonical Situation deserves an autonomous model pass. */
public final class CognitiveAutoReasoningPolicyV4 {
    private static final long TWO_HOURS_MS=2L*60L*60L*1000L;
    private static final long DAY_MS=24L*60L*60L*1000L;
    private CognitiveAutoReasoningPolicyV4(){}

    static Decision evaluate(CognitivePulseProjectionV4.Snapshot pulse,long now){
        if(pulse==null||pulse.items.isEmpty())return Decision.none("no_pulse");
        ArrayList<CognitivePulseProjectionV4.Item> fresh=new ArrayList<>();boolean urgent=false;double max=0;
        for(CognitivePulseProjectionV4.Item x:pulse.items){
            if(x==null||!x.newSinceDeepBrain)continue;String kind=n(x.kind).toUpperCase(Locale.ROOT);double score=x.attentionScore;long deadlineDelta=x.relevantUntil-now;boolean deadlineSoon="DEADLINE".equals(kind)&&x.relevantUntil>0&&deadlineDelta>=-DAY_MS&&deadlineDelta<=TWO_HOURS_MS;boolean highRisk="RISK".equals(kind);boolean hardMeaningful=highRisk||"DEADLINE".equals(kind)||"UPCOMING_EVENT".equals(kind)||"COMMITMENT".equals(kind)||"WAITING".equals(kind);boolean qualifies=hardMeaningful||score>=.62||("FOLLOW_UP".equals(kind)&&score>=.58);if(!qualifies)continue;fresh.add(x);max=Math.max(max,score);if(highRisk||deadlineSoon||score>=.82)urgent=true;
        }
        if(fresh.isEmpty())return Decision.none("no_meaningful_change");
        sort(fresh);
        return decision("auto-reasoning-v4|",fresh,urgent,max,"urgent_fresh_context","meaningful_fresh_context");
    }

    /**
     * Explicit provider-recovery/onboarding pass. It deliberately ignores newSinceDeepBrain so a
     * phone upgraded from the old ChatGPT/manual path gets one Gemini ranking of the CURRENT Pulse
     * instead of waiting indefinitely for a future Situation mutation. The distinct fingerprint
     * makes the baseline idempotent for an unchanged Pulse.
     */
    static Decision evaluateCurrentBaseline(CognitivePulseProjectionV4.Snapshot pulse,long now){
        if(pulse==null||pulse.items.isEmpty())return Decision.none("no_pulse");
        ArrayList<CognitivePulseProjectionV4.Item> current=new ArrayList<>();boolean urgent=false;double max=0;
        for(CognitivePulseProjectionV4.Item x:pulse.items){
            if(x==null||n(x.situationId).isEmpty())continue;
            current.add(x);max=Math.max(max,x.attentionScore);
            String kind=n(x.kind).toUpperCase(Locale.ROOT);long deadlineDelta=x.relevantUntil-now;
            boolean deadlineSoon="DEADLINE".equals(kind)&&x.relevantUntil>0&&deadlineDelta>=-DAY_MS&&deadlineDelta<=TWO_HOURS_MS;
            if("RISK".equals(kind)||deadlineSoon||x.attentionScore>=.82)urgent=true;
        }
        if(current.isEmpty())return Decision.none("no_pulse");
        sort(current);
        return decision("auto-reasoning-v4-baseline|",current,urgent,max,"baseline_reconsider_current","baseline_reconsider_current");
    }

    private static Decision decision(String prefix,List<CognitivePulseProjectionV4.Item> items,boolean urgent,double max,String urgentReason,String normalReason){
        StringBuilder fp=new StringBuilder(prefix);for(int i=0;i<Math.min(8,items.size());i++){CognitivePulseProjectionV4.Item x=items.get(i);fp.append(x.situationId).append('|').append(x.changedAt).append('|').append(Math.round(x.attentionScore*1000)).append(';');}
        return new Decision(true,urgent,Fingerprint.text(fp.toString()),items.size(),max,urgent?urgentReason:normalReason,items);
    }
    private static void sort(ArrayList<CognitivePulseProjectionV4.Item> items){Collections.sort(items,new Comparator<CognitivePulseProjectionV4.Item>(){@Override public int compare(CognitivePulseProjectionV4.Item a,CognitivePulseProjectionV4.Item b){int s=Double.compare(b.attentionScore,a.attentionScore);if(s!=0)return s;return Long.compare(b.changedAt,a.changedAt);}});}
    private static String n(String s){return s==null?"":s.trim();}

    static final class Decision{
        final boolean shouldRun,urgent;final String fingerprint,reason;final int freshCount;final double maxAttention;final List<CognitivePulseProjectionV4.Item> freshItems;
        Decision(boolean run,boolean urgent,String fp,int count,double max,String reason,List<CognitivePulseProjectionV4.Item>items){shouldRun=run;this.urgent=urgent;fingerprint=fp==null?"":fp;freshCount=count;maxAttention=max;this.reason=reason==null?"":reason;freshItems=Collections.unmodifiableList(new ArrayList<>(items));}
        static Decision none(String reason){return new Decision(false,false,"",0,0,reason,Collections.<CognitivePulseProjectionV4.Item>emptyList());}
    }
}
