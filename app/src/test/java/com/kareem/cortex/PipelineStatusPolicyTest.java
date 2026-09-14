package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PipelineStatusPolicyTest {
    @Test public void unavailableSemanticCapabilityIsPausedNotProcessing(){
        PipelineStatusPolicy.Counts c=PipelineStatusPolicy.classify(70,0,0,false);
        assertEquals(0,c.processing);
        assertEquals(70,c.paused);
    }

    @Test public void readySemanticCapabilityCountsWaitingAsProcessing(){
        PipelineStatusPolicy.Counts c=PipelineStatusPolicy.classify(70,0,0,true);
        assertEquals(70,c.processing);
        assertEquals(0,c.paused);
    }

    @Test public void blockedSemanticWorkIsNeverReportedAsProcessing(){
        PipelineStatusPolicy.Counts c=PipelineStatusPolicy.classify(0,9,0,true);
        assertEquals(0,c.processing);
        assertEquals(9,c.paused);
    }

    @Test public void activeMediaRemainsProcessingWhenSemanticCapabilityIsUnavailable(){
        PipelineStatusPolicy.Counts c=PipelineStatusPolicy.classify(7,3,2,false);
        assertEquals(2,c.processing);
        assertEquals(10,c.paused);
    }

    @Test public void negativeInputsCannotCreateNegativeStatusCounts(){
        PipelineStatusPolicy.Counts c=PipelineStatusPolicy.classify(-1,-2,-3,false);
        assertEquals(0,c.processing);
        assertEquals(0,c.paused);
    }
}
