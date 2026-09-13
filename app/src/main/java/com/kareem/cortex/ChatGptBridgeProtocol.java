package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * Versioned wire protocol for Cortex <-> ChatGPT bridge messages.
 * Transport-agnostic by design: Gmail is only a carrier.
 */
public final class ChatGptBridgeProtocol {
    public static final int SCHEMA_VERSION = 1;

    public enum MessageType {
        CORTEX_TEST_REQUEST,
        CHATGPT_TEST_VERDICT,
        CORTEX_TEACH_REQUEST,
        CHATGPT_TEACH_RESPONSE
    }

    public enum VerdictStatus { PASS, WARN, FAIL }
    public enum Severity { NONE, P3, P2, P1, P0 }

    private ChatGptBridgeProtocol() {}

    public static JSONObject newTestRequest(
            String runId,
            String testId,
            JSONObject originalInput,
            JSONObject cortexOutput,
            JSONObject referenceEvidence,
            JSONObject judgingRules) throws JSONException {
        String requestId = UUID.randomUUID().toString();
        JSONObject payload = new JSONObject()
                .put("originalInput", safe(originalInput))
                .put("cortexOutput", safe(cortexOutput))
                .put("referenceEvidence", safe(referenceEvidence))
                .put("judgingRules", safe(judgingRules));
        return envelope(MessageType.CORTEX_TEST_REQUEST, requestId, runId, testId, payload);
    }

    public static JSONObject envelope(
            MessageType type,
            String requestId,
            String runId,
            String testId,
            JSONObject payload) throws JSONException {
        requireNonBlank("requestId", requestId);
        requireNonBlank("runId", runId);
        requireNonBlank("testId", testId);
        JSONObject normalizedPayload = safe(payload);
        String payloadJson = canonicalJson(normalizedPayload);
        return new JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("messageType", type.name())
                .put("requestId", requestId)
                .put("runId", runId)
                .put("testId", testId)
                .put("createdAtEpochMs", System.currentTimeMillis())
                .put("payloadSha256", sha256(payloadJson))
                .put("payload", normalizedPayload);
    }

    public static Validation validateEnvelope(JSONObject envelope) {
        try {
            if (envelope == null) return Validation.fail("null envelope");
            if (envelope.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                return Validation.fail("unsupported schemaVersion");
            }
            String type = envelope.optString("messageType", "");
            try { MessageType.valueOf(type); } catch (Exception e) {
                return Validation.fail("invalid messageType");
            }
            String requestId = envelope.optString("requestId", "");
            String runId = envelope.optString("runId", "");
            String testId = envelope.optString("testId", "");
            if (requestId.trim().isEmpty() || runId.trim().isEmpty() || testId.trim().isEmpty()) {
                return Validation.fail("missing correlation id");
            }
            JSONObject payload = envelope.optJSONObject("payload");
            if (payload == null) return Validation.fail("missing payload");
            String expected = envelope.optString("payloadSha256", "");
            String actual = sha256(canonicalJson(payload));
            if (!constantTimeEquals(expected, actual)) return Validation.fail("payload hash mismatch");
            return Validation.ok();
        } catch (Throwable t) {
            return Validation.fail(t.getClass().getSimpleName() + ": " + safeMessage(t));
        }
    }

    public static Validation validateVerdictAgainstRequest(JSONObject request, JSONObject verdict) {
        Validation requestValidation = validateEnvelope(request);
        if (!requestValidation.ok) return Validation.fail("request invalid: " + requestValidation.reason);
        Validation verdictValidation = validateEnvelope(verdict);
        if (!verdictValidation.ok) return Validation.fail("verdict invalid: " + verdictValidation.reason);
        if (!MessageType.CORTEX_TEST_REQUEST.name().equals(request.optString("messageType"))) {
            return Validation.fail("request has wrong messageType");
        }
        if (!MessageType.CHATGPT_TEST_VERDICT.name().equals(verdict.optString("messageType"))) {
            return Validation.fail("response is not a test verdict");
        }
        for (String key : new String[]{"requestId", "runId", "testId"}) {
            if (!constantTimeEquals(request.optString(key, ""), verdict.optString(key, ""))) {
                return Validation.fail(key + " mismatch");
            }
        }
        JSONObject requestPayload = request.optJSONObject("payload");
        JSONObject verdictPayload = verdict.optJSONObject("payload");
        if (verdictPayload == null) return Validation.fail("missing verdict payload");
        String requestPayloadSha = request.optString("payloadSha256", "");
        String echoed = verdictPayload.optString("requestPayloadSha256", "");
        if (!constantTimeEquals(requestPayloadSha, echoed)) {
            return Validation.fail("requestPayloadSha256 mismatch");
        }
        try {
            VerdictStatus.valueOf(verdictPayload.optString("status", ""));
            Severity.valueOf(verdictPayload.optString("severity", ""));
        } catch (Exception e) {
            return Validation.fail("invalid verdict status/severity");
        }
        return Validation.ok();
    }

    public static JSONObject defaultJudgingRules() throws JSONException {
        return new JSONObject()
                .put("solveIndependentlyBeforeComparison", true)
                .put("treatOriginalInputAsGroundTruthInput", true)
                .put("doNotInventMissingEvidence", true)
                .put("checkGrounding", true)
                .put("checkDecisionQuality", true)
                .put("checkContinuity", true)
                .put("checkFileFlowWhenPresent", true)
                .put("teachingDisabled", true);
    }

    public static JSONObject buildVerdictPayload(
            VerdictStatus status,
            Severity severity,
            String requestPayloadSha256,
            Object independentAnswer,
            Object cortexAssessment,
            Object groundingAssessment,
            Object decisionAssessment,
            JSONArray mismatches,
            JSONArray unsupportedClaims,
            JSONArray missingExpectedFacts,
            JSONObject teachingCandidate) throws JSONException {
        JSONObject out = new JSONObject()
                .put("status", status.name())
                .put("severity", severity.name())
                .put("requestPayloadSha256", requestPayloadSha256)
                .put("independentAnswer", independentAnswer == null ? JSONObject.NULL : independentAnswer)
                .put("cortexAssessment", cortexAssessment == null ? JSONObject.NULL : cortexAssessment)
                .put("groundingAssessment", groundingAssessment == null ? JSONObject.NULL : groundingAssessment)
                .put("decisionAssessment", decisionAssessment == null ? JSONObject.NULL : decisionAssessment)
                .put("mismatches", mismatches == null ? new JSONArray() : mismatches)
                .put("unsupportedClaims", unsupportedClaims == null ? new JSONArray() : unsupportedClaims)
                .put("missingExpectedFacts", missingExpectedFacts == null ? new JSONArray() : missingExpectedFacts)
                .put("teachingCandidate", teachingCandidate == null ? JSONObject.NULL : teachingCandidate);
        return out;
    }

    public static String subjectFor(JSONObject envelope) {
        return String.format(Locale.US, "[CORTEX-BRIDGE][v%d][%s][%s][%s]",
                SCHEMA_VERSION,
                envelope.optString("messageType", "UNKNOWN"),
                envelope.optString("runId", "no-run"),
                envelope.optString("testId", "no-test"));
    }

    public static String canonicalJson(JSONObject json) {
        // org.json keeps insertion order on Android in practice, but hash validation must not depend on it.
        // Rebuild recursively with sorted keys.
        return CanonicalJson.stringify(json == null ? new JSONObject() : json);
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static JSONObject safe(JSONObject input) {
        return input == null ? new JSONObject() : input;
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aa = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aa, bb);
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "" : m;
    }

    public static final class Validation {
        public final boolean ok;
        public final String reason;
        private Validation(boolean ok, String reason) { this.ok = ok; this.reason = reason; }
        public static Validation ok() { return new Validation(true, ""); }
        public static Validation fail(String reason) { return new Validation(false, reason == null ? "invalid" : reason); }
    }

    private static final class CanonicalJson {
        static String stringify(Object value) {
            if (value == null || value == JSONObject.NULL) return "null";
            if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                java.util.Iterator<String> it = object.keys();
                while (it.hasNext()) keys.add(it.next());
                java.util.Collections.sort(keys);
                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sb.append(',');
                    String key = keys.get(i);
                    sb.append(JSONObject.quote(key)).append(':').append(stringify(object.opt(key)));
                }
                return sb.append('}').toString();
            }
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < array.length(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(stringify(array.opt(i)));
                }
                return sb.append(']').toString();
            }
            if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
            return JSONObject.quote(String.valueOf(value));
        }
    }
}
