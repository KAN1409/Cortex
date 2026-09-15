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

    @Test public void proseBeginningWithPrIsNeverAReference(){
        assertEquals("",DiscoveryV3Policy.exactRef("privacy processing primary project provider"));
        assertEquals("",DiscoveryV3Policy.exactRef("PR IVACY and PR OJECT must never become procurement references"));
        assertEquals("",DiscoveryV3Policy.exactRef("please review PR ABC because it has no identifier structure"));
        assertEquals("PR-ABC-17",DiscoveryV3Policy.exactRef("please review PR ABC-17 ceiling"));
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

    @Test public void legacyMalformedProcurementDiscoveriesNeverSurface(){
        assertTrue(DiscoveryV3Feed.hasMalformedProcurementRef("PR-OJECTS status changed"));
        assertTrue(DiscoveryV3Feed.hasMalformedProcurementRef("PR-OTECTED/SKIPPED pending"));
        assertTrue(DiscoveryV3Feed.hasMalformedProcurementRef("PO-ROCESSING conflict"));
        assertFalse(DiscoveryV3Feed.hasMalformedProcurementRef("PR-0262 status changed"));
        assertFalse(DiscoveryV3Feed.hasMalformedProcurementRef("PO-ABC-17 approved"));
    }

    @Test public void discoveryFeedRejectsConnectivityAsProductOutput(){
        assertFalse(DiscoveryV3Feed.userWorthy("CROSS_SOURCE_CONNECTION","Cortex connected evidence","Negma appears in two apps","Open combined history",2,.92,.91));
    }

    @Test public void councilDiscoveryNeedsAConsequence(){
        assertFalse(DiscoveryV3Feed.userWorthy("COUNCIL_DISCOVERY","Negma evidence is related","The same project appears across email and a document","Open the evidence",3,.91,.88));
        assertTrue(DiscoveryV3Feed.userWorthy("COUNCIL_DISCOVERY","Negma quotation may still be pending","The latest project evidence still shows a missing quotation after the approval reference","Verify the quotation status before issuing the next PO",3,.91,.88));
    }

    @Test public void councilUsesThreeDistinctHeavyBrains(){
        LocalCouncilModelRegistry.Model a=LocalCouncilModelRegistry.primary();
        LocalCouncilModelRegistry.Model b=LocalCouncilModelRegistry.analyst();
        LocalCouncilModelRegistry.Model d=LocalCouncilModelRegistry.critic();
        assertNotEquals(a.id,b.id);assertNotEquals(a.id,d.id);assertNotEquals(b.id,d.id);
        assertTrue(a.maxTokens>=700);assertTrue(b.maxTokens>=600);assertTrue(d.maxTokens>=700);
        assertTrue(a.role.contains("primary"));assertTrue(b.role.contains("analyst"));assertTrue(d.role.contains("critic"));
        assertTrue(b.url.startsWith("https://huggingface.co/"));
        assertTrue(d.url.startsWith("https://huggingface.co/"));
    }

    @Test public void fullCouncilOrderIsPrimaryAnalystCritic(){
        java.util.List<LocalCouncilModelRegistry.Model> models=LocalCouncilModelRegistry.council();
        assertEquals(3,models.size());
        assertEquals(LocalCouncilModelRegistry.PRIMARY,models.get(0).id);
        assertEquals(LocalCouncilModelRegistry.ANALYST,models.get(1).id);
        assertEquals(LocalCouncilModelRegistry.CRITIC,models.get(2).id);
    }

}
