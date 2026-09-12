package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkVaultVersionGcTest {
    @Test public void keepsTwoPreviousVersionsByDefault(){
        assertFalse(WorkVaultVersionGc.shouldPrunePreviousOrdinal(0,2));
        assertFalse(WorkVaultVersionGc.shouldPrunePreviousOrdinal(1,2));
        assertTrue(WorkVaultVersionGc.shouldPrunePreviousOrdinal(2,2));
        assertTrue(WorkVaultVersionGc.shouldPrunePreviousOrdinal(8,2));
    }

    @Test public void zeroRetentionPrunesEveryNonActiveVersion(){
        assertTrue(WorkVaultVersionGc.shouldPrunePreviousOrdinal(0,0));
    }

    @Test public void negativeRetentionIsClampedToZero(){
        assertTrue(WorkVaultVersionGc.shouldPrunePreviousOrdinal(0,-4));
    }

    @Test public void staleLinkPredicatesRequireActiveVersionForDerivedEndpoints(){
        String from=WorkVaultVersionGc.staleEndpointWhere("from");
        String to=WorkVaultVersionGc.staleEndpointWhere("to");
        assertTrue(from.contains("r.version_id=f.active_version_id"));
        assertTrue(from.contains("u.version_id=f.active_version_id"));
        assertTrue(from.contains("p.version_id=f.active_version_id"));
        assertTrue(from.contains("from_kind='FILE'"));
        assertTrue(to.contains("to_kind='REF'"));
        assertTrue(to.contains("to_id"));
    }
}
