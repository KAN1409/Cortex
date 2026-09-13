package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parsed external adjudication result. Cortex must keep its original evidence unchanged. */
public final class ChatGptTestVerdict {
    public enum Status { PASS, WARN, FAIL }

    public final Status status;
    public final double confidence;
    public final String summary;
    public final JSONArray mismatches;
    public final JSONArray recommendedFixes;

    public ChatGptTestVerdict(
            Status status,
            double confidence,
            String summary,
            JSONArray mismatches,
            JSONArray recommendedFixes) {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within 0..1");
        }
        this.status = status;
        this.confidence = confidence;
        this.summary = summary == null ? "" : summary;
        this.mismatches = mismatches == null ? new JSONArray() : mismatches;
        this.recommendedFixes = recommendedFixes == null ? new JSONArray() : recommendedFixes;
    }

    public static ChatGptTestVerdict fromPayload(JSONObject payload) throws JSONException {
        return new ChatGptTestVerdict(
                Status.valueOf(payload.getString("status")),
                payload.getDouble("confidence"),
                payload.optString("summary", ""),
                payload.optJSONArray("mismatches"),
                payload.optJSONArray("recommendedFixes"));
    }
}
