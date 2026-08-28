package com.kareem.cortex;

import org.json.JSONObject;

/** Stable, transport-light contract used by local Android connector apps. */
public final class CortexLocalBusProtocolV1 {
    public static final String ACTION_BIND = "com.kareem.cortex.LOCAL_BUS_V1";
    public static final String PROTOCOL = "CORTEX_INGEST_V1";

    public static final int MSG_HELLO = 1;
    public static final int MSG_INGEST = 2;
    public static final int MSG_PING = 3;
    public static final int MSG_ACK = 100;
    public static final int MSG_ERROR = 101;

    public static final String KEY_CONNECTOR_ID = "connector_id";
    public static final String KEY_CAPABILITIES_JSON = "capabilities_json";
    public static final String KEY_EVENT_JSON = "event_json";
    public static final String KEY_EVENT_ID = "event_id";
    public static final String KEY_STATUS = "status";
    public static final String KEY_DETAIL = "detail";
    public static final String KEY_SIGNAL_ID = "signal_id";

    private CortexLocalBusProtocolV1() {}

    public static Event parseEvent(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("event_json required");
        if (raw.length() > 256_000) throw new IllegalArgumentException("event_json too large");
        try {
            JSONObject o = new JSONObject(raw);
            String protocol = clean(o.optString("protocol", ""));
            if (!PROTOCOL.equals(protocol)) throw new IllegalArgumentException("unsupported protocol");
            String eventId = bounded(o.optString("event_id", ""), 180, "event_id");
            String connectorId = bounded(o.optString("connector_id", ""), 80, "connector_id");
            String sourceType = bounded(o.optString("source_type", ""), 80, "source_type").toUpperCase();
            String sourcePackage = bounded(o.optString("source_package", ""), 220, "source_package");
            long occurredAt = o.optLong("occurred_at", 0L);
            if (occurredAt <= 0L) throw new IllegalArgumentException("occurred_at required");
            long now = System.currentTimeMillis();
            if (occurredAt > now + 24L * 60L * 60L * 1000L) throw new IllegalArgumentException("occurred_at is implausibly future");
            return new Event(eventId, connectorId, sourceType, sourcePackage, occurredAt, o);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid connector event JSON", e);
        }
    }

    private static String bounded(String value, int max, String field) {
        String x = clean(value);
        if (x.isEmpty()) throw new IllegalArgumentException(field + " required");
        if (x.length() > max) throw new IllegalArgumentException(field + " too long");
        return x;
    }

    static String clean(String value) { return value == null ? "" : value.trim(); }

    public static final class Event {
        public final String eventId, connectorId, sourceType, sourcePackage;
        public final long occurredAt;
        public final JSONObject json;
        Event(String eventId, String connectorId, String sourceType, String sourcePackage, long occurredAt, JSONObject json) {
            this.eventId = eventId;
            this.connectorId = connectorId;
            this.sourceType = sourceType;
            this.sourcePackage = sourcePackage;
            this.occurredAt = occurredAt;
            this.json = json;
        }
    }
}
