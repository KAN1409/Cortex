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

    @Test public void mapsDocumentTypesToDistinctLifecycleRoles(){
        assertEquals("quotation_document",WorkDocumentLifecycleLinker.relationForType("QUOTATION"));
        assertEquals("comparison_document",WorkDocumentLifecycleLinker.relationForType("COMPARISON"));
        assertEquals("approval_document",WorkDocumentLifecycleLinker.relationForType("APPROVAL"));
        assertEquals("purchase_order_document",WorkDocumentLifecycleLinker.relationForType("PURCHASE_ORDER"));
        assertEquals("",WorkDocumentLifecycleLinker.relationForType("OTHER"));
    }

    @Test public void procurementTimelineReportsFirstMissingGroundedStage(){
        assertEquals("QUOTATION_EVIDENCE_NOT_FOUND",WorkProcurementCaseEngine.issue(false,false,false,false));
        assertEquals("COMPARISON_EVIDENCE_NOT_FOUND",WorkProcurementCaseEngine.issue(true,false,false,false));
        assertEquals("APPROVAL_EVIDENCE_NOT_FOUND",WorkProcurementCaseEngine.issue(true,true,false,false));
        assertEquals("PO_EVIDENCE_NOT_FOUND",WorkProcurementCaseEngine.issue(true,true,true,false));
        assertEquals("PO_EVIDENCE_FOUND",WorkProcurementCaseEngine.issue(true,true,true,true));
    }

    @Test public void understandsEnglishAndArabicMissingStepQuestions(){
        assertEquals(WorkProcurementCaseSearch.Intent.MISSING_PO,
                WorkProcurementCaseSearch.intent("Which PRs still have no PO?"));
        assertEquals(WorkProcurementCaseSearch.Intent.MISSING_PO,
                WorkProcurementCaseSearch.intent("إيه الـPRs اللي مفيش لها PO؟"));
        assertEquals(WorkProcurementCaseSearch.Intent.MISSING_APPROVAL,
                WorkProcurementCaseSearch.intent("إيه الـPRs اللي مفيش لها اعتماد؟"));
        assertEquals(WorkProcurementCaseSearch.Intent.MISSING_COMPARISON,
                WorkProcurementCaseSearch.intent("Which PRs are missing comparison?"));
        assertEquals(WorkProcurementCaseSearch.Intent.MISSING_QUOTATION,
                WorkProcurementCaseSearch.intent("إيه الـPRs اللي مفيش لها عرض سعر؟"));
        assertEquals(WorkProcurementCaseSearch.Intent.NONE,
                WorkProcurementCaseSearch.intent("Compare Galala marble prices"));
    }

    @Test public void priceComparisonRequiresSameUnitAndCurrency(){
        WorkPriceComparisonEngine.Price oldPrice=WorkPriceComparisonEngine.Price.of("Galala marble","m²",2500,"EGP");
        WorkPriceComparisonEngine.Price newPrice=WorkPriceComparisonEngine.Price.of("Galala marble","sqm",2650,"L.E.");
        WorkPriceComparisonEngine.Comparison ok=WorkPriceComparisonEngine.compare(newPrice,oldPrice);
        assertTrue(ok.comparable);
        assertEquals("INCREASE",ok.direction());
        assertEquals(150.0,ok.delta,.001);
        assertEquals(6.0,ok.percent,.001);

        WorkPriceComparisonEngine.Price usd=WorkPriceComparisonEngine.Price.of("Galala marble","m2",52,"USD");
        WorkPriceComparisonEngine.Comparison currencyMismatch=WorkPriceComparisonEngine.compare(usd,oldPrice);
        assertFalse(currencyMismatch.comparable);
        assertEquals("CURRENCY_MISMATCH",currencyMismatch.reason);

        WorkPriceComparisonEngine.Price linear=WorkPriceComparisonEngine.Price.of("Galala marble","lm",2650,"EGP");
        WorkPriceComparisonEngine.Comparison unitMismatch=WorkPriceComparisonEngine.compare(linear,oldPrice);
        assertFalse(unitMismatch.comparable);
        assertEquals("UNIT_MISMATCH",unitMismatch.reason);
    }
}
