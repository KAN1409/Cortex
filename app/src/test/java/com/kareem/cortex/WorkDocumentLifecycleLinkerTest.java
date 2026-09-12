package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkDocumentLifecycleLinkerTest {
    @Test public void invoicesAndDeliveriesPreferPo(){
        assertTrue(WorkDocumentLifecycleLinker.preferPoForType("INVOICE"));
        assertTrue(WorkDocumentLifecycleLinker.preferPoForType("DELIVERY"));
    }

    @Test public void preOrderDocumentsKeepPrPreference(){
        assertFalse(WorkDocumentLifecycleLinker.preferPoForType("QUOTATION"));
        assertFalse(WorkDocumentLifecycleLinker.preferPoForType("COMPARISON"));
        assertFalse(WorkDocumentLifecycleLinker.preferPoForType("APPROVAL"));
    }
}
