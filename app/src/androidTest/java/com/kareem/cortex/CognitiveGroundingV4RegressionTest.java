package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveGroundingV4RegressionTest {

    @Test(expected = IllegalArgumentException.class)
    public void inferredReasoningWithoutCitationIsRejected() {
        CognitiveDomainV4.ReasoningBlock block = new CognitiveDomainV4.ReasoningBlock(
                CognitiveDomainV4.ReasoningBlockType.INFERENCE,
                CognitiveDomainV4.StatementGrounding.INFERRED,
                "Ahmed is probably still waiting.",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null);
        CognitiveGroundingV4.requireGrounded(block);
    }

    @Test public void inferredReasoningWithMemoryCitationIsAccepted() {
        CognitiveDomainV4.ReasoningBlock block = new CognitiveDomainV4.ReasoningBlock(
                CognitiveDomainV4.ReasoningBlockType.INFERENCE,
                CognitiveDomainV4.StatementGrounding.INFERRED,
                "Ahmed is probably still waiting.",
                Collections.emptyList(),
                Arrays.asList("mem_1"),
                Collections.emptyList(),
                null);
        CognitiveGroundingV4.requireGrounded(block);
    }

    @Test public void suggestionMayExistWithoutHistoricalCitation() {
        CognitiveDomainV4.ReasoningBlock block = new CognitiveDomainV4.ReasoningBlock(
                CognitiveDomainV4.ReasoningBlockType.SUGGESTION,
                CognitiveDomainV4.StatementGrounding.SUGGESTED,
                "Send the latest revision now.",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null);
        CognitiveGroundingV4.requireGrounded(block);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedInferredFactIsRejectedByGroundingBoundary() {
        CognitiveDomainV4.Fact fact = new CognitiveDomainV4.Fact(
                "fact_1",
                "world_1",
                "status",
                "waiting",
                CognitiveDomainV4.GroundingKind.INFERRED,
                .80,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                CognitiveDomainV4.FactStatus.ACTIVE);
        CognitiveGroundingV4.requireGrounded(fact);
    }
}
