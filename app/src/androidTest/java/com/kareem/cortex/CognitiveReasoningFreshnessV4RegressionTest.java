package com.kareem.cortex;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CognitiveReasoningFreshnessV4RegressionTest {
    @Test public void situationAfterAppliedReasoningIsNewContext(){
        assertTrue(CognitiveReasoningFreshnessV4.isNew(2_000L,1_000L));
    }

    @Test public void situationAlreadyCoveredByAppliedReasoningIsNotNew(){
        assertFalse(CognitiveReasoningFreshnessV4.isNew(1_000L,1_000L));
        assertFalse(CognitiveReasoningFreshnessV4.isNew(900L,1_000L));
    }

    @Test public void firstReasoningPassTreatsExistingSituationAsUnreviewed(){
        assertTrue(CognitiveReasoningFreshnessV4.isNew(1_000L,0L));
    }
}
