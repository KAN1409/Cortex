package com.kareem.cortex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void payloadHashSurvivesJsonRoundTripWithIntegralDoubles() throws Exception {
        JSONObject original = new JSONObject()
                .put("oneDouble", 1.0d)
                .put("zeroDouble", 0.0d)
                .put("fraction", 0.93d)
                .put("nested", new JSONArray()
                        .put(new JSONObject().put("score", 1.0d).put("threshold", 0.72d))
                        .put(0.0d));

        JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                "roundtrip-run", "roundtrip-test",
                original,
                new JSONObject().put("surfaceNow", true).put("score", 1.0d),
                new JSONObject().put("expected", 1.0d),
                ChatGptBridgeProtocol.defaultJudgingRules());

        assertTrue(ChatGptBridgeProtocol.validateEnvelope(request).ok);
        JSONObject reparsed = new JSONObject(request.toString());
        assertTrue("request must remain hash-valid after disk-style JSON round-trip",
                ChatGptBridgeProtocol.validateEnvelope(reparsed).ok);
    }

    @Test
    public void persistedLegacyRequestCanStillAcceptCorrelatedVerdict() throws Exception {
        JSONObject request = ChatGptBridgeProtocol.newTestRequest(
                "legacy-run", "legacy-test",
                new JSONObject().put("score", 1.0d),
                new JSONObject().put("decision", "NOW"),
                new JSONObject().put("expected", "NOW"),
                ChatGptBridgeProtocol.defaultJudgingRules());

        // Simulate a v112-era persisted request whose original wire hash no longer matches
        // the reparsed numeric representation. The stored hash remains the correlation fingerprint.
        request.put("payloadSha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertFalse(ChatGptBridgeProtocol.validateEnvelope(request).ok);

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
                request.getString("requestId"), request.getString("runId"), request.getString("testId"), verdictPayload);

        assertTrue(ChatGptBridgeProtocol.validateVerdictAgainstPersistedRequest(request, verdict).ok);
        assertFalse(ChatGptBridgeProtocol.validateVerdictAgainstRequest(request, verdict).ok);
    }
}
