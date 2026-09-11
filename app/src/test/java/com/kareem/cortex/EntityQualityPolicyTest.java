package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class EntityQualityPolicyTest {
    @Test public void personBoundaryDropsCurrencyTail(){
        assertEquals("Mohamed Moustafa",EntityQualityPolicy.cleanPersonName("Mohamed Moustafa at EGP"));
        assertTrue(EntityQualityPolicy.plausibleEntity("PERSON","Mohamed Moustafa at EGP"));
    }

    @Test public void actionTitleIsNotAProject(){
        assertFalse(EntityQualityPolicy.plausibleEntity("PROJECT","Fix Face Resolution"));
        assertFalse(EntityQualityPolicy.plausibleEntity("PROJECT","Make sure you add your photo"));
        assertTrue(EntityQualityPolicy.plausibleEntity("PROJECT","Negma Reception"));
    }

    @Test public void technicalTokensNeverBecomeIdentities(){
        assertFalse(EntityQualityPolicy.plausibleEntity("PROJECT","paige.prompts"));
        assertFalse(EntityQualityPolicy.plausibleEntity("PERSON","Automations:Failed in 1 minute"));
        assertFalse(EntityQualityPolicy.plausibleEntity("ORGANIZATION","https://example.com/path"));
    }

    @Test public void categoryVariantsCollapseToOneCanonicalLabel(){
        assertEquals("Links & references",KnowledgeV2Maintenance.canonicalCategory("links"));
        assertEquals("Links & references",KnowledgeV2Maintenance.canonicalCategory("Links & Research"));
        assertEquals("Links & references",KnowledgeV2Maintenance.canonicalCategory("Links & references"));
        assertEquals("Money & purchases",KnowledgeV2Maintenance.canonicalCategory("money"));
        assertEquals("Actions & commitments",KnowledgeV2Maintenance.canonicalCategory("Actions"));
    }
}
