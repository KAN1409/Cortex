package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class CanonicalSemanticQualityGateTest {
    @Test public void visualCaptureRemainsEvidenceOnly(){CanonicalSemanticQualityGate.Result r=CanonicalSemanticQualityGate.evaluate("visual_evidence","ocr_evidence","captured_artifact","evidence","SPEAKS","SPEAKS",.92);assertFalse(r.eligible);assertEquals("rejected",r.canonicalState);assertTrue(r.confidence<.70);}
    @Test public void explicitRequestCanBecomeStateful(){CanonicalSemanticQualityGate.Result r=CanonicalSemanticQualityGate.evaluate("notification","conversation_notification","action_request","request","Elham","Please send the signed quotation today",.94);assertTrue(r.eligible);assertEquals("supported",r.canonicalState);assertTrue(r.confidence>=.78);}
    @Test public void genericUiChromeIsRejected(){CanonicalSemanticQualityGate.Result r=CanonicalSemanticQualityGate.evaluate("notification","generic","notification_event","notification","Cortex","Open Cancel Save Settings Download Search",.80);assertFalse(r.eligible);}
}
