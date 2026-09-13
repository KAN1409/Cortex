package com.kareem.cortex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ChatGptBridgeProtocolTest {
    @Test
    public void verdictMustMatchOriginalRequest() throws Exception {
        JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                "run-1",
                "test-1",
                new JSONObject().put("input", "x"),
                new JSONObject().put("decision", "NOW"),
                new JSONObject().put("expected", "NOW"),
                ChatGptBridgeProtocol.defaultJudgingRules());

        JSONObject verdictPayload = ChatGptBridgeProtocol.buildVerdictPayload(
                ChatGptBridgeProtocol.VerdictStatus.PASS,
                ChatGptBridgeProtocol.Severity.NONE,
                request.getString("payloadSha256"),
                new JSONObject().put("decision", "NOW"),
                new JSONObject().put("correct", true),
                new JSONObject().put("grounded", true),
                new JSONObject().put("decisionCorrect", true),
                new JSONArray(), new JSONArray(), new JSONArray(), null);

        JSONObject verdict = ChatGptBridgeProtocol.envelope(
                ChatGptBridgeProtocol.MessageType.CHATGPT_TEST_VERDICT,
                request.getString("requestId"),
                request.getString("runId"),
                request.getString("testId"),
                verdictPayload);

        assertTrue(ChatGptBridgeProtocol.validateVerdictAgainstRequest(request, verdict).ok);

        verdict.put("testId", "wrong-test");
        assertFalse(ChatGptBridgeProtocol.validateVerdictAgainstRequest(request, verdict).ok);
    }

    @Test
    public void payloadTamperingIsRejected() throws Exception {
        JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                "run-2", "test-2", new JSONObject().put("a", 1),
                new JSONObject().put("b", 2), new JSONObject().put("c", 3),
                ChatGptBridgeProtocol.defaultJudgingRules());
        request.getJSONObject("payload").put("aNewField", "tampered");
        assertFalse(ChatGptBridgeProtocol.validateEnvelope(request).ok);
    }
}
