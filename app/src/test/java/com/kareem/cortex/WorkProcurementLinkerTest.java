package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkProcurementLinkerTest {
    @Test public void sameKnownProjectIsCompatible(){
        assertTrue(WorkProcurementLinker.projectsCompatible(12,12));
        assertEquals(.995,WorkProcurementLinker.sameReferenceConfidence(12,12),0.0001);
    }

    @Test public void knownDifferentProjectsAreRejected(){
        assertFalse(WorkProcurementLinker.projectsCompatible(12,18));
    }

    @Test public void unknownProjectAllowsOnlyWeakerProvisionalLink(){
        assertTrue(WorkProcurementLinker.projectsCompatible(0,18));
        assertTrue(WorkProcurementLinker.projectsCompatible(12,0));
        assertEquals(.93,WorkProcurementLinker.sameReferenceConfidence(0,18),0.0001);
    }
}
