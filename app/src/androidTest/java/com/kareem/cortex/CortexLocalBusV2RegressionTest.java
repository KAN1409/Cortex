package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CortexLocalBusV2RegressionTest {
    @Test public void negotiatesOnlyWhenSignalV2IsAdvertised() throws Exception {
        assertTrue(CortexLocalBusProtocolV2.relayAdvertisesSignalV2(
                new JSONArray().put("NOTIFICATIONS").put("CORTEX_SIGNAL_V2").toString()));
        assertFalse(CortexLocalBusProtocolV2.relayAdvertisesSignalV2(
                new JSONArray().put("NOTIFICATIONS").toString()));
        assertFalse(CortexLocalBusProtocolV2.relayAdvertisesSignalV2("not-json"));
    }

    @Test public void adaptsV2ToCanonicalV1WithoutChangingEventIdentity() throws Exception {
        long now = System.currentTimeMillis();
        JSONObject semantic = new JSONObject();
        semantic.put("schema", "CORTEX_RELAY_SEMANTIC_V2");
        semantic.put("source_type", "NOTIFICATION");
        semantic.put("source_package", "com.whatsapp");
        semantic.put("signal_type", "HUMAN_MESSAGE");
        semantic.put("logical_signal_id", "signal-message-delta_abc");
        semantic.put("conversation_identity", "conversation_123");
        semantic.put("android_context", new JSONObject().put("isOngoing", false));
        semantic.put("content", new JSONObject()
                .put("title", "Alice")
                .put("text", "hello")
                .put("expanded_text", JSONObject.NULL)
                .put("conversation_title", "Alice")
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("sender", "Alice")
                        .put("text", "hello")
                        .put("timestamp", now))));

        JSONArray actions = new JSONArray().put(new JSONObject()
                .put("capability_id", "action_1")
                .put("kind", "REPLY")
                .put("requires_text_input", true));

        JSONObject root = new JSONObject();
        root.put("protocol", "CORTEX_SIGNAL_V2");
        root.put("schema", "CORTEX_RELAY_SIGNAL_V2");
        root.put("event_id", "sb_evt_v2_1");
        root.put("connector_id", "second_brain");
        root.put("occurred_at", now);
        root.put("source", new JSONObject()
                .put("type", "NOTIFICATION")
                .put("package", "com.whatsapp")
                .put("notification_key", "0|com.whatsapp|42|null|123"));
        root.put("semantic", semantic);
        root.put("action_capabilities", actions);
        root.put("compatibility", new JSONObject()
                .put("v1_protocol", "CORTEX_INGEST_V1")
                .put("v1_event_id", "sb_evt_v2_1"));

        CortexLocalBusProtocolV2.Event v2 = CortexLocalBusProtocolV2.parseEvent(root.toString());
        CortexLocalBusProtocolV1.Event v1 = CortexLocalBusProtocolV2.toCanonicalV1(v2);

        assertEquals("sb_evt_v2_1", v1.eventId);
        assertEquals("second_brain", v1.connectorId);
        assertEquals("NOTIFICATION", v1.sourceType);
        assertEquals("com.whatsapp", v1.sourcePackage);
        assertEquals("Alice", v1.json.getString("title"));
        assertEquals("hello", v1.json.getString("text"));
        assertEquals("0|com.whatsapp|42|null|123", v1.json.getString("notification_key"));

        JSONObject meta = v1.json.getJSONObject("metadata");
        assertEquals("signal-message-delta_abc",
                meta.getJSONObject("relay_semantic_v2").getString("logical_signal_id"));
        assertEquals("action_1",
                meta.getJSONArray("relay_action_capabilities_v1").getJSONObject(0).getString("capability_id"));
        assertTrue(meta.getJSONObject("local_bus_signal_v2").getBoolean("received_as_v2"));
    }

    @Test public void buildsBoundedActionAndMechanicalPolicyRequests() throws Exception {
        JSONObject action = CortexLocalBusProtocolV2.actionRequest(
                "request-1", "signal-1", "action-1", "hello");
        assertEquals("request-1", action.getString("request_id"));
        assertEquals("hello", action.getString("input_text"));

        JSONObject policy = CortexLocalBusProtocolV2.mechanicalPolicy(
                7L, 48, new JSONArray().put("GROUP_SUMMARY"));
        assertEquals("CORTEX_RELAY_MECHANICAL_POLICY_V1", policy.getString("schema"));
        assertEquals(7L, policy.getLong("version"));
        assertEquals(48, policy.getInt("forensic_retention_hours"));
        assertEquals("GROUP_SUMMARY", policy.getJSONArray("disabled_noise_rules").getString(0));
    }

    @Test public void rejectsWrongV2Schema() throws Exception {
        JSONObject root = new JSONObject();
        root.put("protocol", "CORTEX_SIGNAL_V2");
        root.put("schema", "WRONG");
        root.put("event_id", "sb_evt");
        root.put("connector_id", "second_brain");
        root.put("occurred_at", System.currentTimeMillis());
        root.put("source", new JSONObject().put("type", "NOTIFICATION").put("package", "com.whatsapp"));
        root.put("semantic", new JSONObject());
        boolean failed = false;
        try { CortexLocalBusProtocolV2.parseEvent(root.toString()); }
        catch (IllegalArgumentException expected) { failed = true; }
        assertTrue(failed);
    }
}
