package com.kareem.cortex;

import org.json.JSONObject;
import java.util.List;

/** Transport-only boundary for Cortex ↔ ChatGPT adjudication mail. */
public interface BridgeMailTransport {
    GmailBridgeTransport.SendResult sendEnvelope(JSONObject envelope);
    List<JSONObject> fetchCandidateVerdicts(long newerThanEpochMs, int maxResults) throws Exception;
    String name();
}
