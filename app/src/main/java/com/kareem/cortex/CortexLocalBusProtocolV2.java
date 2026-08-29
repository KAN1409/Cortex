package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

/** Optional Cortex <-> Relay V2 contract layered on top of the stable Local Bus V1 transport. */
public final class CortexLocalBusProtocolV2 {
    public static final String SIGNAL_PROTOCOL = "CORTEX_SIGNAL_V2";
    public static final String SIGNAL_SCHEMA = "CORTEX_RELAY_SIGNAL_V2";
    public static final String ACTION_BRIDGE = "ACTION_BRIDGE_V1";
    public static final String POLICY_FEEDBACK = "POLICY_FEEDBACK_V1";
    public static final String REPLAY_DIAGNOSTICS = "REPLAY_DIAGNOSTICS_V1";

    public static final int MSG_INGEST_V2 = 20;
    public static final int MSG_ACTION_REQUEST = 200;
    public static final int MSG_POLICY_UPDATE = 201;
    public static final int MSG_ACTION_RESULT = 202;
    public static final int MSG_POLICY_RESULT = 203;

    public static final String KEY_RELAY_CAPABILITIES_JSON = "relay_capabilities_json";
    public static final String KEY_SELECTED_PROTOCOL = "selected_protocol";
    public static final String KEY_REQUEST_JSON = "request_json";
    public static final String KEY_RESULT_JSON = "result_json";

    private static final int MAX_EVENT_CHARS = 256_000;

    private CortexLocalBusProtocolV2() {}

    public static boolean relayAdvertisesSignalV2(String rawCapabilities) {
        try {
            JSONArray a = new JSONArray(rawCapabilities == null ? "[]" : rawCapabilities);
            for (int i = 0; i < a.length(); i++) {
                if (SIGNAL_PROTOCOL.equals(clean(a.optString(i, "")))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Event parseEvent(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("event_json required");
        if (raw.length() > MAX_EVENT_CHARS) throw new IllegalArgumentException("event_json too large");
        try {
            JSONObject root = new JSONObject(raw);
            if (!SIGNAL_PROTOCOL.equals(clean(root.optString("protocol", "")))) throw new IllegalArgumentException("unsupported V2 protocol");
            if (!SIGNAL_SCHEMA.equals(clean(root.optString("schema", "")))) throw new IllegalArgumentException("unsupported V2 schema");
            String eventId = bounded(root.optString("event_id", ""), 180, "event_id");
            String connectorId = bounded(root.optString("connector_id", ""), 80, "connector_id");
            long occurredAt = root.optLong("occurred_at", 0L);
            if (occurredAt <= 0L) throw new IllegalArgumentException("occurred_at required");
            if (occurredAt > System.currentTimeMillis() + 24L * 60L * 60L * 1000L) throw new IllegalArgumentException("occurred_at is implausibly future");
            JSONObject source = root.optJSONObject("source");
            if (source == null) throw new IllegalArgumentException("source required");
            String sourceType = bounded(source.optString("type", ""), 80, "source.type").toUpperCase();
            String sourcePackage = bounded(source.optString("package", ""), 220, "source.package");
            JSONObject semantic = root.optJSONObject("semantic");
            if (semantic == null) throw new IllegalArgumentException("semantic required");
            return new Event(eventId, connectorId, sourceType, sourcePackage, occurredAt, root, source, semantic);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid V2 connector event JSON", e);
        }
    }

    public static CortexLocalBusProtocolV1.Event toCanonicalV1(Event event) {
        if (event == null) throw new IllegalArgumentException("V2 event required");
        try {
            JSONObject content = event.semantic.optJSONObject("content");
            if (content == null) content = new JSONObject();
            JSONObject androidContext = event.semantic.optJSONObject("android_context");
            if (androidContext == null) androidContext = new JSONObject();
            JSONObject v1 = new JSONObject();
            v1.put("protocol", CortexLocalBusProtocolV1.PROTOCOL);
            v1.put("event_id", event.eventId);
            v1.put("connector_id", event.connectorId);
            v1.put("source_type", event.sourceType);
            v1.put("source_package", event.sourcePackage);
            v1.put("occurred_at", event.occurredAt);
            v1.put("notification_key", clean(event.source.optString("notification_key", "")));
            v1.put("title", nullableString(content, "title"));
            v1.put("text", nullableString(content, "text"));
            v1.put("expanded_text", nullableString(content, "expanded_text"));
            v1.put("conversation_title", nullableString(content, "conversation_title"));
            v1.put("messages", content.optJSONArray("messages") != null ? content.optJSONArray("messages") : new JSONArray());
            v1.put("ongoing", androidContext.optBoolean("isOngoing", false));
            JSONObject metadata = new JSONObject();
            metadata.put("relay_semantic_v2", new JSONObject(event.semantic.toString()));
            JSONArray actions = event.root.optJSONArray("action_capabilities");
            metadata.put("relay_action_capabilities_v1", actions != null ? new JSONArray(actions.toString()) : new JSONArray());
            metadata.put("local_bus_signal_v2", new JSONObject().put("protocol", SIGNAL_PROTOCOL).put("schema", SIGNAL_SCHEMA).put("received_as_v2", true));
            JSONObject compatibility = event.root.optJSONObject("compatibility");
            if (compatibility != null) metadata.put("relay_v2_compatibility", new JSONObject(compatibility.toString()));
            v1.put("metadata", metadata);
            return CortexLocalBusProtocolV1.parseEvent(v1.toString());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("could not adapt V2 event into canonical ingest", e);
        }
    }

    public static JSONObject actionRequest(String requestId, String logicalSignalId, String capabilityId, String inputText) {
        JSONObject o = new JSONObject();
        try {
            o.put("request_id", bounded(requestId, 180, "request_id"));
            o.put("logical_signal_id", bounded(logicalSignalId, 220, "logical_signal_id"));
            o.put("capability_id", bounded(capabilityId, 220, "capability_id"));
            if (inputText != null) o.put("input_text", inputText);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid action request", e);
        }
        return o;
    }

    public static JSONObject mechanicalPolicy(long version, int retentionHours, JSONArray disabledNoiseRules) {
        if (version <= 0L) throw new IllegalArgumentException("policy version must be positive");
        if (retentionHours < 24 || retentionHours > 72) throw new IllegalArgumentException("retention must be 24..72 hours");
        JSONObject o = new JSONObject();
        try {
            o.put("schema", "CORTEX_RELAY_MECHANICAL_POLICY_V1");
            o.put("version", version);
            o.put("forensic_retention_hours", retentionHours);
            o.put("disabled_noise_rules", disabledNoiseRules == null ? new JSONArray() : new JSONArray(disabledNoiseRules.toString()));
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid mechanical policy", e);
        }
        return o;
    }

    private static String nullableString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        return clean(o.optString(key, ""));
    }

    private static String bounded(String value, int max, String field) {
        String x = clean(value);
        if (x.isEmpty()) throw new IllegalArgumentException(field + " required");
        if (x.length() > max) throw new IllegalArgumentException(field + " too long");
        return x;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public static final class Event {
        public final String eventId, connectorId, sourceType, sourcePackage;
        public final long occurredAt;
        public final JSONObject root, source, semantic;
        Event(String eventId, String connectorId, String sourceType, String sourcePackage, long occurredAt, JSONObject root, JSONObject source, JSONObject semantic) {
            this.eventId = eventId;
            this.connectorId = connectorId;
            this.sourceType = sourceType;
            this.sourcePackage = sourcePackage;
            this.occurredAt = occurredAt;
            this.root = root;
            this.source = source;
            this.semantic = semantic;
        }
    }
}
