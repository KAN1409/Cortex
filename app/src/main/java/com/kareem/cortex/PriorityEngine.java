package com.kareem.cortex;

/** Final local baseline priority is deterministic; model estimates are inputs, never ordering authority. */
public final class PriorityEngine {
    private PriorityEngine(){}

    public static int score(int importance,int urgency,CognitiveKind kind,boolean requiresUserAction,
                            boolean requiresFollowUp,boolean requiresContentExtraction,long dueAt,
                            long occurredAt,int relationshipWeight,boolean securityCritical,long now){
        CognitiveSignalV2.Kind legacy=kind==null?CognitiveSignalV2.Kind.MEMORY:CognitiveSignalV2.Kind.valueOf(kind.name());
        return CognitiveSignalV2.priorityScore(importance,urgency,legacy,requiresUserAction,requiresFollowUp,
                requiresContentExtraction,dueAt,occurredAt,relationshipWeight,securityCritical,now);
    }

    public static boolean pulseEligible(CognitiveKind kind,int score,boolean requiresUserAction,
                                        boolean requiresFollowUp,boolean requiresContentExtraction){
        CognitiveSignalV2.Kind legacy=kind==null?CognitiveSignalV2.Kind.MEMORY:CognitiveSignalV2.Kind.valueOf(kind.name());
        return CognitiveSignalV2.pulseEligible(legacy,score,requiresUserAction,requiresFollowUp,requiresContentExtraction);
    }
}
