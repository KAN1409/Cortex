package com.kareem.cortex;

import org.json.JSONException;
import org.json.JSONObject;

/** Immutable wire envelope used by the Cortex <-> ChatGPT Gmail bridge. */
public final class ChatGptBridgeEnvelope {
    public static final int SCHEMA_VERSION = 1;

    public enum Type {
        TEST_REQUEST,
        TEST_VERDICT,
        TEACH_REQUEST,
        TEACH_RESPONSE,
        HANDSHAKE,
        HANDSHAKE_ACK
    }

    public final int schemaVersion;
    public final Type type;
    public final String runId;
    public final String testId;
    public final String requestId;
    public final long createdAtMs;
    public final JSONObject payload;

    public ChatGptBridgeEnvelope(
            Type type,
            String runId,
            String testId,
            String requestId,
            long createdAtMs,
            JSONObject payload) {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (runId == null || runId.trim().isEmpty()) throw new IllegalArgumentException("runId is required");
        if (requestId == null || requestId.trim().isEmpty()) throw new IllegalArgumentException("requestId is required");
        this.schemaVersion = SCHEMA_VERSION;
        this.type = type;
        this.runId = runId;
        this.testId = testId == null ? "" : testId;
        this.requestId = requestId;
        this.createdAtMs = createdAtMs;
        this.payload = payload == null ? new JSONObject() : payload;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);
        root.put("type", type.name());
        root.put("runId", runId);
        root.put("testId", testId);
        root.put("requestId", requestId);
        root.put("createdAtMs", createdAtMs);
        root.put("payload", payload);
        return root;
    }

    public static ChatGptBridgeEnvelope fromJson(JSONObject root) throws JSONException {
        int schema = root.getInt("schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new JSONException("Unsupported ChatGPT bridge schemaVersion=" + schema);
        }
        return new ChatGptBridgeEnvelope(
                Type.valueOf(root.getString("type")),
                root.getString("runId"),
                root.optString("testId", ""),
                root.getString("requestId"),
                root.getLong("createdAtMs"),
                root.optJSONObject("payload"));
    }
}
