package com.kareem.cortex;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorkVaultIndexServiceStateTest {
    @After public void cleanup(){WorkVaultIndexService.clearActiveForTest();}

    @Test public void sourceRemainsActiveUntilAllQueuedRunsFinish(){
        WorkVaultIndexService.reserveSource(42L);
        WorkVaultIndexService.reserveSource(42L);
        assertTrue(WorkVaultIndexService.isIndexing(42L));
        assertEquals(2,WorkVaultIndexService.activeCountForTest(42L));
        WorkVaultIndexService.releaseSource(42L);
        assertTrue(WorkVaultIndexService.isIndexing(42L));
        WorkVaultIndexService.releaseSource(42L);
        assertFalse(WorkVaultIndexService.isIndexing(42L));
    }

    @Test public void invalidSourceIdsNeverBecomeActive(){
        WorkVaultIndexService.reserveSource(0L);
        WorkVaultIndexService.reserveSource(-7L);
        assertFalse(WorkVaultIndexService.isIndexing(0L));
        assertFalse(WorkVaultIndexService.isIndexing(-7L));
    }
}
