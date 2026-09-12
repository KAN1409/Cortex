package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkProcurementCaseEngineTest {
    @Test public void knownProjectScopeRejectsOtherAndUnknownProjects(){
        assertTrue(WorkProcurementCaseEngine.sameCaseScope(12,0,12,100));
        assertFalse(WorkProcurementCaseEngine.sameCaseScope(12,0,18,101));
        assertFalse(WorkProcurementCaseEngine.sameCaseScope(12,0,0,102));
    }

    @Test public void unknownProjectScopeIsIsolatedToAnchorFile(){
        assertTrue(WorkProcurementCaseEngine.sameCaseScope(0,55,0,55));
        assertFalse(WorkProcurementCaseEngine.sameCaseScope(0,55,0,56));
        assertFalse(WorkProcurementCaseEngine.sameCaseScope(0,55,9,55));
    }

    @Test public void unknownProjectWithoutAnchorNeverMatches(){
        assertFalse(WorkProcurementCaseEngine.sameCaseScope(0,0,0,55));
    }
}
