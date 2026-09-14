package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiscoveryCoreTest {
    private KnowledgeItem item(String category,String title,String metadata){
        return new KnowledgeItem(1,"TEXT","manual",title,"","",title,category,"","","analyzed","","",metadata,1000,1000);
    }

    @Test public void explicitSpaceWins(){
        assertEquals("WORK",DiscoveryPolicy.inferSpace(item("Health","anything","{\"space\":\"WORK\"}")));
    }

    @Test public void exactProcurementReferenceIsStable(){
        assertEquals("PR-0262",DiscoveryPolicy.exactReference("Please review PR 0262 ceiling scope"));
        assertEquals("PO-123/7",DiscoveryPolicy.exactReference("PO#123/7 approved"));
    }

    @Test public void contradictionPolicyFindsMaterialStatusConflict(){
        assertTrue(DiscoveryPolicy.contradictory("approved","revision"));
        assertTrue(DiscoveryPolicy.contradictory("completed","pending"));
        assertFalse(DiscoveryPolicy.contradictory("approved","approved"));
    }

    @Test public void rawCountsAreRejectedAsNoise(){
        assertTrue(DiscoveryPolicy.isTrivial("Work statistics","17 records observed"));
        DiscoveryCritic.Verdict v=DiscoveryCritic.judge("MEANINGFUL_CHANGE","Work statistics","17 records observed",2,.9,.9);
        assertFalse(v.keep);
    }

    @Test public void usefulCandidateCanPassCritic(){
        DiscoveryCritic.Verdict v=DiscoveryCritic.judge("CONTRADICTION","Conflicting approval state",
                "One source says approved while a later source requests revision.",2,.86,.80);
        assertTrue(v.keep);
    }
}
