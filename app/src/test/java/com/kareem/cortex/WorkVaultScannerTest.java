package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkVaultScannerTest {
    @Test public void completeCleanScanMayReconcileMissing(){
        assertTrue(WorkVaultScanner.shouldReconcileMissing(0,""));
    }

    @Test public void partialOrErroredScanNeverMarksFilesMissing(){
        assertFalse(WorkVaultScanner.shouldReconcileMissing(1,""));
        assertFalse(WorkVaultScanner.shouldReconcileMissing(0,"tree read failed"));
    }
}
