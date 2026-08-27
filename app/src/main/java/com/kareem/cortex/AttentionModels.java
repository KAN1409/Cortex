package com.kareem.cortex;

/** Small immutable domain objects for attention scoring and feed decisions. */
public final class AttentionModels {
    private AttentionModels(){}

    public enum Level { NONE, LOW, MEDIUM, HIGH, CRITICAL }
    public enum Actionability { NONE, REVIEW, REPLY, CALL, OPEN, SEND, COMPLETE, DECIDE, SCHEDULE, REMIND, EXECUTE }
    public enum DeliveryMode { SILENT_MEMORY, FEED, HIGHLIGHT, NOTIFICATION, IMMEDIATE_INTERRUPT }

    public static final class Dimensions {
        public final double urgency,importance,actionRequired,commitmentStrength,unresolvedness,contextRelevance,recency,novelty,interruptionCost;
        public Dimensions(double urgency,double importance,double actionRequired,double commitmentStrength,double unresolvedness,double contextRelevance,double recency,double novelty,double interruptionCost){
            this.urgency=c(urgency);this.importance=c(importance);this.actionRequired=c(actionRequired);this.commitmentStrength=c(commitmentStrength);this.unresolvedness=c(unresolvedness);this.contextRelevance=c(contextRelevance);this.recency=c(recency);this.novelty=c(novelty);this.interruptionCost=c(interruptionCost);
        }
    }

    public static final class Assessment {
        public final double score,interruptScore,confidence;
        public final Level level;
        public final Dimensions dimensions;
        public final String primaryReason,suggestedAction;
        public final Actionability actionability;
        public final boolean timeSensitive;
        public final long evaluatedAt;
        Assessment(double score,double interruptScore,double confidence,Level level,Dimensions dimensions,String primaryReason,String suggestedAction,Actionability actionability,boolean timeSensitive,long evaluatedAt){this.score=score;this.interruptScore=interruptScore;this.confidence=c(confidence);this.level=level;this.dimensions=dimensions;this.primaryReason=n(primaryReason);this.suggestedAction=n(suggestedAction);this.actionability=actionability==null?Actionability.NONE:actionability;this.timeSensitive=timeSensitive;this.evaluatedAt=evaluatedAt;}
    }

    public static final class Decision {
        public final Assessment assessment;
        public final DeliveryMode deliveryMode;
        public final boolean surfaceNow;
        public final double rank;
        Decision(Assessment a,DeliveryMode d,boolean surface,double rank){assessment=a;deliveryMode=d;surfaceNow=surface;this.rank=rank;}
    }

    static double c(double x){return Math.max(0d,Math.min(1d,x));}
    static String n(String s){return s==null?"":s.trim();}
}
