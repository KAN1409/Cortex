package com.kareem.cortex;

/**
 * Explicit v83 boundary between cheap TRIAGE and expensive SELECTIVE REASONING.
 *
 * This class never surfaces an item and never executes an action. It only decides whether a
 * grounded candidate deserves deeper reasoning before CortexAttentionJudge makes final judgment.
 */
public final class CortexReasoningGate {
    public static final String VERSION = "cortex_reasoning_gate_001";

    public static final class Assessment {
        public final boolean escalate;
        public final String reason;
        Assessment(boolean escalate,String reason){
            this.escalate=escalate;
            this.reason=reason==null?"":reason;
        }
    }

    private CortexReasoningGate(){}

    public static Assessment assess(
            AttentionDecisionEngine.Candidate c,
            boolean conflictingSituations,
            boolean planningRequired) {
        if(c==null)return new Assessment(false,"no candidate");
        if(!c.unresolved)return new Assessment(false,"resolved state requires no reasoning");
        if(c.confidence<.55)return new Assessment(false,"grounding confidence is below reasoning floor");

        boolean ambiguous=c.confidence<.82;
        boolean highValue=c.risk>=.70||c.urgency>=.78||c.actionability>=.82
                ||c.linkedOpenCommitment||c.explicitRequest||c.severeContextImpact;
        boolean contextSensitive=c.materialChange&&(c.personalRelevance>=.60||c.novelty>=.65);

        if(planningRequired)return new Assessment(true,"planning required before action");
        if(conflictingSituations)return new Assessment(true,"multiple situations conflict");
        if(ambiguous&&(highValue||contextSensitive))
            return new Assessment(true,"ambiguity could materially change judgment");
        if(highValue&&c.confidence<.90)
            return new Assessment(true,"high-value case deserves selective verification");
        return new Assessment(false,"deterministic local path is sufficient");
    }
}
