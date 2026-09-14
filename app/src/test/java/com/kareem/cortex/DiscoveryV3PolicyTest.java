package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiscoveryV3PolicyTest {
    private KnowledgeItem item(String category,String title,String metadata){
        return new KnowledgeItem(1,"TEXT","manual",title,"","","",category,"","","analyzed","","",metadata,1000,1000);
    }

    @Test public void exactRefsAreStable(){
        assertEquals("PR-0262",DiscoveryV3Policy.exactRef("please review PR 0262 ceiling"));
        assertEquals("PO-123/7",DiscoveryV3Policy.exactRef("PO#123/7 approved"));
    }

    @Test public void explicitSpaceOverridesWeakCategory(){
        assertEquals("WORK",DiscoveryV3Policy.space(item("Health","anything","{\"space\":\"WORK\"}")));
    }

    @Test public void incompatibleStatusRequiresSamePredicatePair(){
        assertTrue(DiscoveryV3Policy.incompatible("approved","revision"));
        assertTrue(DiscoveryV3Policy.incompatible("pending","completed"));
        assertFalse(DiscoveryV3Policy.incompatible("approved","approved"));
    }

    @Test public void genericCountsCannotPublish(){
        assertFalse(DiscoveryV3Policy.publishable("CHANGE","17 records observed this week","Review evidence",2,.9,.9));
    }

    @Test public void groundedContradictionCanPublish(){
        assertTrue(DiscoveryV3Policy.publishable("CONTRADICTION","PR-0262 has conflicting status evidence: approved versus revision.","Open both evidence items and resolve which status is current.",2,.86,.82));
    }

    @Test public void contradictionNeedsTwoEvidenceItems(){
        assertFalse(DiscoveryV3Policy.publishable("CONTRADICTION","PR-0262 has conflicting status evidence.","Open evidence",1,.9,.9));
    }
}
