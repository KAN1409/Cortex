package com.kareem.cortex;

/** Release-blocking grounding checks shared by persistence and future Think projections. */
public final class CognitiveGroundingV4 {
    private CognitiveGroundingV4() {}

    public static void requireGrounded(CognitiveDomainV4.Fact fact) {
        if (fact == null) throw new IllegalArgumentException("fact required");
        if (fact.evidenceIds.isEmpty() && fact.memoryIds.isEmpty()) {
            throw new IllegalArgumentException("Fact requires Evidence or Memory provenance");
        }
    }

    public static void requireGrounded(CognitiveDomainV4.Relation relation) {
        if (relation == null) throw new IllegalArgumentException("relation required");
        if (relation.evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("Relation requires Evidence provenance");
        }
    }

    public static void requireGrounded(CognitiveDomainV4.Situation situation) {
        if (situation == null) throw new IllegalArgumentException("situation required");
        if (situation.evidenceIds.isEmpty()
                && situation.memoryIds.isEmpty()
                && situation.factIds.isEmpty()) {
            throw new IllegalArgumentException("Situation requires canonical provenance");
        }
    }

    public static void requireGrounded(CognitiveDomainV4.ReasoningBlock block) {
        if (block == null) throw new IllegalArgumentException("block required");
        if (block.grounding == CognitiveDomainV4.StatementGrounding.SUGGESTED) return;
        if (block.evidenceIds.isEmpty() && block.memoryIds.isEmpty() && block.factIds.isEmpty()) {
            throw new IllegalArgumentException(block.grounding.name() + " reasoning requires citations");
        }
    }

    public static void requireGrounded(CognitiveDomainV4.ReasoningResult result) {
        if (result == null) throw new IllegalArgumentException("result required");
        for (CognitiveDomainV4.ReasoningBlock block : result.blocks) requireGrounded(block);
    }
}
