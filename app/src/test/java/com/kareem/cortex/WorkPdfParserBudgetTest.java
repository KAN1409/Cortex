package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkPdfParserBudgetTest {
    @Test public void pageBudgetIncludesRenderAndAllOcrStages(){
        assertEquals(40_000L,WorkPdfParser.remainingBudgetMs(1_000L,1_000L));
        assertEquals(16_000L,WorkPdfParser.remainingBudgetMs(1_000L,25_000L));
        assertEquals(0L,WorkPdfParser.remainingBudgetMs(1_000L,41_500L));
    }

    @Test public void latinStageCannotConsumeWholePageBudget(){
        assertEquals(20_000L,WorkPdfParser.latinStageBudgetMs(40_000L));
        assertEquals(8_000L,WorkPdfParser.latinStageBudgetMs(8_000L));
        assertEquals(0L,WorkPdfParser.latinStageBudgetMs(0L));
    }

    @Test public void onlyActualEngineFailuresAreBreakerSignals(){
        assertTrue(WorkPdfParser.shouldRecordEngineFailure(new IllegalStateException("mlkit failed")));
        assertFalse(WorkPdfParser.shouldRecordEngineFailure(new InterruptedException("cancelled")));
        assertFalse(WorkPdfParser.shouldRecordEngineFailure(null));
    }
}
