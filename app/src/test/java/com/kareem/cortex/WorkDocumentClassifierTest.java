package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkDocumentClassifierTest {
    @Test public void classifiesComparisonWithoutCollapsingIntoQuotation(){
        WorkDocumentClassifier.Result r=WorkDocumentClassifier.classifyText(
                "Negma Commercial Comparison PR 0262.xlsx",
                "Vendor 1 Vendor 2 Item Description Unit Price Total Comparison Sheet");
        assertEquals("COMPARISON",r.type);
        assertTrue(r.confidence>.70);
    }

    @Test public void classifiesPurchaseOrderFromFilenameToken(){
        WorkDocumentClassifier.Result r=WorkDocumentClassifier.classifyText(
                "PO 1047 Life Style.pdf",
                "Supply and installation of ceiling works");
        assertEquals("PURCHASE_ORDER",r.type);
    }

    @Test public void doesNotTreatProjectAsPrToken(){
        WorkDocumentClassifier.Result r=WorkDocumentClassifier.classifyText(
                "Project Negma Presentation.pptx",
                "Project Negma owner presentation ceiling choice");
        assertNotEquals("PURCHASE_REQUEST",r.type);
    }

    @Test public void classifiesFollowUpSheet(){
        WorkDocumentClassifier.Result r=WorkDocumentClassifier.classifyText(
                "Procurement Follow-up.xlsx",
                "PR Status Pending Action Responsible Expected Date Supplier PO");
        assertEquals("FOLLOW_UP",r.type);
        assertTrue(r.confidence>.75);
    }
}
