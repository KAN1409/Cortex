package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/** Wire contract for the user-triggered Cortex <-> ChatGPT Deep Brain bridge. */
public final class CognitiveDeepBrainProtocolV4 {
    public static final String CONTEXT_MARKER = "CORTEX_CONTEXT_V2";
    public static final String RESPONSE_MARKER = "CORTEX_RESPONSE_V1";
    private CognitiveDeepBrainProtocolV4() {}

    public static String buildShareText(String requestId, String question, JSONObject context) {
        StringBuilder b = new StringBuilder();
        b.append(CONTEXT_MARKER).append('\n');
        b.append("You are Cortex Deep Brain. Use only the grounded Cortex context below for claims about the user's history. ");
        b.append("Reason deeply about priority, meaning, planning and next actions. Do not invent events or mark anything resolved.\n\n");
        b.append("QUESTION:\n").append(question == null ? "" : question.trim()).append("\n\n");
        b.append("RETURN FORMAT (important):\n");
        b.append("Return a short natural-language answer inside the JSON field 'answer', but the complete response must contain the marker ")
                .append(RESPONSE_MARKER).append(" followed by one JSON object.\n");
        b.append("Only reference IDs present in CONTEXT_JSON. Unknown IDs must be omitted.\n");
        b.append("Allowed priority states: DETECTED, RELEVANT, SURFACED, DEFERRED, WAITING. Never RESOLVED/CANCELLED/DISMISSED.\n");
        b.append("Suggested actions are proposals only; do not claim they were executed.\n\n");
        b.append("Example schema:\n");
        b.append(RESPONSE_MARKER).append('\n');
        b.append("{\"request_id\":\"").append(escape(requestId)).append("\",\"answer\":\"...\",\"priority_updates\":[{\"situation_id\":\"si_...\",\"attention_score\":0.9,\"interruption_score\":0.5,\"state\":\"RELEVANT\",\"reason\":\"...\"}],\"suggested_actions\":[{\"situation_id\":\"si_...\",\"world_id\":\"wo_...\",\"type\":\"REPLY\",\"label\":\"...\",\"risk\":\"CONFIRMATION_REQUIRED\",\"payload\":{}}],\"reasoning_blocks\":[{\"type\":\"INFERENCE\",\"grounding\":\"INFERRED\",\"text\":\"...\",\"evidence_ids\":[],\"memory_ids\":[],\"fact_ids\":[]}]}\n\n");
        b.append("CONTEXT_JSON:\n").append(context == null ? "{}" : context.toString());
        return b.toString();
    }

    public static ParsedResponse parseResponse(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Empty ChatGPT response");
        int marker = raw.indexOf(RESPONSE_MARKER);
        if (marker < 0) throw new IllegalArgumentException("Missing " + RESPONSE_MARKER);
        String jsonText = firstJsonObject(raw.substring(marker + RESPONSE_MARKER.length()));
        if (jsonText.isEmpty()) throw new IllegalArgumentException("Missing response JSON");
        try {
            JSONObject json = new JSONObject(jsonText);
            String requestId = json.optString("request_id", "").trim();
            if (requestId.isEmpty()) throw new IllegalArgumentException("response request_id required");
            return new ParsedResponse(requestId, json.optString("answer", "").trim(), json, raw);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Invalid response JSON", e);
        }
    }

    static JSONArray array(JSONObject o, String key) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        return a == null ? new JSONArray() : a;
    }

    static String firstJsonObject(String text) {
        if (text == null) return "";
        int start = text.indexOf('{');
        if (start < 0) return "";
        boolean inString = false, escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return "";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class ParsedResponse {
        public final String requestId;
        public final String answer;
        public final JSONObject json;
        public final String raw;
        ParsedResponse(String requestId, String answer, JSONObject json, String raw) {
            this.requestId = requestId;
            this.answer = answer == null ? "" : answer;
            this.json = json;
            this.raw = raw == null ? "" : raw;
        }
    }
}
