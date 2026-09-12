package com.kareem.cortex;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class CortexDiagnosticExporterTest {
    @Test public void reportsMissingCanonicalIngressAsDegraded() throws Exception {JSONObject h=CortexDiagnosticExporter.pipelineHealth(0,0,1232,0);assertEquals("DEGRADED_NO_CANONICAL_INGEST",h.getString("state"));}
    @Test public void reportsSemanticStallSeparately() throws Exception {JSONObject h=CortexDiagnosticExporter.pipelineHealth(12,0,1232,12);assertEquals("DEGRADED_SEMANTIC_STALLED",h.getString("state"));}
    @Test public void reportsObservedCanonicalFlowHealthy() throws Exception {JSONObject h=CortexDiagnosticExporter.pipelineHealth(12,9,1232,30);assertEquals("HEALTHY",h.getString("state"));}
}
