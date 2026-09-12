package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActionSpecificityGateTest {
    @Test public void rejectsExplicitNoActionEnglish(){
        assertFalse(ActionSpecificityGate.evaluate("action","follow_up","","No explicit follow-up or action was detected.",false,false).eligible);
        assertFalse(ActionSpecificityGate.allowLegacyExtraction("No action required."));
        assertFalse(ActionSpecificityGate.allowLegacyExtraction("Nothing required"));
    }
    @Test public void rejectsExplicitNoActionArabic(){
        assertFalse(ActionSpecificityGate.evaluate("action","follow_up","","لا يوجد إجراء مطلوب",false,false).eligible);
        assertFalse(ActionSpecificityGate.allowLegacyExtraction("لا توجد متابعة"));
    }
    @Test public void rejectsUiChromeAndContactLabels(){
        assertFalse(ActionSpecificityGate.allowLegacyExtraction("Open Cancel Save Settings Download"));
        assertFalse(ActionSpecificityGate.allowLegacyExtraction("Fix Phone: 0100 566 2800"));
    }
    @Test public void keepsGroundedExplicitRequestEligible(){
        ActionSpecificityGate.Result r=ActionSpecificityGate.evaluate("action_request","request","final quotation","Please send the final quotation by tomorrow.",true,false);
        assertTrue(r.eligible);assertTrue(r.specificity>=0.28);
    }
    @Test public void keepsGroundedArabicRequestEligible(){
        ActionSpecificityGate.Result r=ActionSpecificityGate.evaluate("action_request","request","العرض النهائي","لو سمحت ابعت العرض النهائي بكرة",true,false);
        assertTrue(r.eligible);
    }
}
