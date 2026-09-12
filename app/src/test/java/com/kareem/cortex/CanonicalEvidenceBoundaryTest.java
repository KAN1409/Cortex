package com.kareem.cortex;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CanonicalEvidenceBoundaryTest {
    @Test public void negativeActionsStayOutOfAttentionBoundary(){assertFalse(ActionSpecificityGate.allowLegacyExtraction("No explicit follow-up or action was detected."));assertFalse(ActionSpecificityGate.allowLegacyExtraction("لا يوجد إجراء مطلوب"));}
    @Test public void genericUiEvidenceStaysNonActionable(){assertTrue(ActionSpecificityGate.isUiChromeLike("Open Cancel Save Settings Download"));}
    @Test public void diagnosticHealthFlagsHistoricalBypass() throws Exception {JSONObject h=CortexDiagnosticExporter.pipelineHealth(0,0,1232,0);assertEquals("DEGRADED_NO_CANONICAL_INGEST",h.getString("state"));}
}
