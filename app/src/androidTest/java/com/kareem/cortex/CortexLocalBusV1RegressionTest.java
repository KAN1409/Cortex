package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class CortexLocalBusV1RegressionTest {
    @Test public void parsesGroundedNotificationEnvelope() throws Exception {
        JSONObject o = new JSONObject();
        o.put("protocol", "CORTEX_INGEST_V1");
        o.put("event_id", "sb_evt_1");
        o.put("connector_id", "second_brain");
        o.put("source_type", "NOTIFICATION");
        o.put("source_package", "com.whatsapp");
        o.put("occurred_at", System.currentTimeMillis());
        o.put("notification_key", "0|com.whatsapp|42|null|123");
        CortexLocalBusProtocolV1.Event e = CortexLocalBusProtocolV1.parseEvent(o.toString());
        assertEquals("sb_evt_1", e.eventId);
        assertEquals("second_brain", e.connectorId);
        assertEquals("NOTIFICATION", e.sourceType);
        assertEquals("com.whatsapp", e.sourcePackage);
    }

    @Test public void rejectsUnsupportedProtocol() throws Exception {
        JSONObject o = new JSONObject();
        o.put("protocol", "OTHER");
        o.put("event_id", "x");
        o.put("connector_id", "second_brain");
        o.put("source_type", "NOTIFICATION");
        o.put("source_package", "com.whatsapp");
        o.put("occurred_at", System.currentTimeMillis());
        boolean failed = false;
        try { CortexLocalBusProtocolV1.parseEvent(o.toString()); }
        catch (IllegalArgumentException expected) { failed = true; }
        assertTrue(failed);
    }

    @Test public void extractsCanonicalNotificationKeyForCrossSensorDedupe() throws Exception {
        JSONObject meta = new JSONObject().put("notification_key", "same-key");
        assertEquals("same-key", NotificationSignalIngressV1.notificationKey(meta.toString()));
    }
}
