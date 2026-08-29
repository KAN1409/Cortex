package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local authenticated control channel back to Relay.
 *
 * A session can only be registered by CortexLocalBusService after Android UID authentication.
 * Cortex may then request an action that Relay actually advertised, or send bounded mechanical
 * policy. Relay remains the executor; Cortex remains the decision maker.
 */
public final class CortexRelayBridgeV2 {
    private static final String PREFS = "cortex_relay_bridge_v2";
    private static final String SECOND_BRAIN = "second_brain";
    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    private CortexRelayBridgeV2() {}

    static void registerAuthenticatedSession(
            Context context,
            CortexConnectorRegistryV1.Identity identity,
            Messenger relay,
            Messenger cortexReply,
            String capabilitiesJson,
            String selectedProtocol) {
        if (context == null || identity == null || relay == null || cortexReply == null) return;
        Session session = new Session(identity.connectorId, identity.packageName, relay, cortexReply,
                capabilitiesJson == null ? "[]" : capabilitiesJson,
                selectedProtocol == null ? CortexLocalBusProtocolV1.PROTOCOL : selectedProtocol,
                System.currentTimeMillis());
        sessions.put(identity.connectorId, session);
        persistSession(context, session);
    }

    static void clearAuthenticatedSession(Context context, String connectorId) {
        if (connectorId == null) return;
        sessions.remove(connectorId);
        if (context != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("connected", false)
                    .putLong("disconnected_at", System.currentTimeMillis())
                    .apply();
        }
    }

    public static boolean isRelayV2Connected() {
        Session session = sessions.get(SECOND_BRAIN);
        return session != null && CortexLocalBusProtocolV2.SIGNAL_PROTOCOL.equals(session.selectedProtocol);
    }

    public static boolean requestAction(
            Context context,
            String requestId,
            String logicalSignalId,
            String capabilityId,
            String inputText) {
        JSONObject request = CortexLocalBusProtocolV2.actionRequest(requestId, logicalSignalId, capabilityId, inputText);
        return send(context, SECOND_BRAIN, CortexLocalBusProtocolV2.MSG_ACTION_REQUEST, request, "action");
    }

    public static boolean updateMechanicalPolicy(
            Context context,
            long version,
            int retentionHours,
            JSONArray disabledNoiseRules) {
        JSONObject request = CortexLocalBusProtocolV2.mechanicalPolicy(version, retentionHours, disabledNoiseRules);
        return send(context, SECOND_BRAIN, CortexLocalBusProtocolV2.MSG_POLICY_UPDATE, request, "policy");
    }

    private static boolean send(Context context, String connectorId, int what, JSONObject request, String kind) {
        Session session = sessions.get(connectorId);
        if (session == null || !CortexLocalBusProtocolV2.SIGNAL_PROTOCOL.equals(session.selectedProtocol)) {
            persistSendFailure(context, kind, "NO_V2_SESSION");
            return false;
        }
        try {
            Message out = Message.obtain(null, what);
            out.replyTo = session.cortexReply;
            Bundle b = new Bundle();
            b.putString(CortexLocalBusProtocolV2.KEY_REQUEST_JSON, request.toString());
            out.setData(b);
            session.relay.send(out);
            persistRequest(context, kind, request);
            return true;
        } catch (Throwable e) {
            sessions.remove(connectorId);
            persistSendFailure(context, kind, e.getClass().getSimpleName());
            return false;
        }
    }

    static void recordControlResult(Context context, CortexConnectorRegistryV1.Identity identity, int what, String raw) {
        if (context == null || identity == null) return;
        String kind = what == CortexLocalBusProtocolV2.MSG_ACTION_RESULT ? "action" :
                what == CortexLocalBusProtocolV2.MSG_POLICY_RESULT ? "policy" : "unknown";
        JSONObject result;
        try { result = new JSONObject(raw == null || raw.trim().isEmpty() ? "{}" : raw); }
        catch (Throwable e) { result = new JSONObject(); try { result.put("status", "INVALID_RESULT_JSON"); } catch (Throwable ignored) {} }
        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        edit.putString("last_" + kind + "_result", result.toString());
        edit.putLong("last_" + kind + "_result_at", System.currentTimeMillis());
        edit.apply();
        logControl(context, identity, "result_" + kind, result);
    }

    static void recordSignal(Context context, CortexConnectorRegistryV1.Identity identity,
                             CortexLocalBusProtocolV2.Event event, long signalId) {
        if (context == null || identity == null || event == null) return;
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("event_id", event.eventId);
            snapshot.put("signal_id", signalId);
            snapshot.put("logical_signal_id", event.semantic.optString("logical_signal_id", ""));
            snapshot.put("signal_type", event.semantic.optString("signal_type", ""));
            snapshot.put("source_package", event.sourcePackage);
            JSONArray actions = event.root.optJSONArray("action_capabilities");
            snapshot.put("action_capabilities", actions == null ? new JSONArray() : new JSONArray(actions.toString()));
        } catch (Throwable ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_v2_signal", snapshot.toString())
                .putLong("last_v2_signal_at", System.currentTimeMillis())
                .apply();
    }

    public static JSONObject diagnosticSnapshot(Context context) {
        JSONObject out = new JSONObject();
        if (context == null) return out;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            out.put("connected", isRelayV2Connected());
            out.put("selected_protocol", p.getString("selected_protocol", CortexLocalBusProtocolV1.PROTOCOL));
            out.put("connector_id", p.getString("connector_id", ""));
            out.put("connector_package", p.getString("connector_package", ""));
            out.put("capabilities_json", p.getString("capabilities_json", "[]"));
            out.put("connected_at", p.getLong("connected_at", 0L));
            out.put("last_request_kind", p.getString("last_request_kind", ""));
            out.put("last_request_json", parseOrString(p.getString("last_request_json", "")));
            out.put("last_action_result", parseOrString(p.getString("last_action_result", "")));
            out.put("last_policy_result", parseOrString(p.getString("last_policy_result", "")));
            out.put("last_v2_signal", parseOrString(p.getString("last_v2_signal", "")));
        } catch (Throwable ignored) {}
        return out;
    }

    private static Object parseOrString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return JSONObject.NULL;
        try { return new JSONObject(raw); } catch (Throwable ignored) { return raw; }
    }

    private static void persistSession(Context context, Session session) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("connected", true)
                .putString("connector_id", session.connectorId)
                .putString("connector_package", session.packageName)
                .putString("capabilities_json", session.capabilitiesJson)
                .putString("selected_protocol", session.selectedProtocol)
                .putLong("connected_at", session.connectedAt)
                .apply();
    }

    private static void persistRequest(Context context, String kind, JSONObject request) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_request_kind", kind)
                .putString("last_request_json", request.toString())
                .putLong("last_request_at", System.currentTimeMillis())
                .apply();
    }

    private static void persistSendFailure(Context context, String kind, String reason) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_request_kind", kind)
                .putString("last_send_failure", reason)
                .putLong("last_request_at", System.currentTimeMillis())
                .apply();
    }

    private static void logControl(Context context, CortexConnectorRegistryV1.Identity identity, String stage, JSONObject payload) {
        VaultDb db = null;
        try {
            db = new VaultDb(context.getApplicationContext());
            JSONObject meta = new JSONObject();
            meta.put("connector_id", identity.connectorId);
            meta.put("connector_package", identity.packageName);
            meta.put("payload", payload);
            DiagnosticsLog.info(db, "local_bus_v2", stage, "accepted", 0, 0, 0, 0, 0, 0, meta);
        } catch (Throwable ignored) {
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    private static final class Session {
        final String connectorId, packageName, capabilitiesJson, selectedProtocol;
        final Messenger relay, cortexReply;
        final long connectedAt;

        Session(String connectorId, String packageName, Messenger relay, Messenger cortexReply,
                String capabilitiesJson, String selectedProtocol, long connectedAt) {
            this.connectorId = connectorId;
            this.packageName = packageName;
            this.relay = relay;
            this.cortexReply = cortexReply;
            this.capabilitiesJson = capabilitiesJson;
            this.selectedProtocol = selectedProtocol;
            this.connectedAt = connectedAt;
        }
    }
}
