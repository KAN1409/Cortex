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
        // Outstanding control requests do not survive the authenticated session that minted them.
        CortexRelayControlCorrelatorV2.reset();
        if (context != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("connected", false)
                    .putLong("disconnected_at", System.currentTimeMillis())
                    .apply();
        }
    }

    static boolean hasAuthenticatedV2Session(String connectorId) {
        Session session = sessions.get(connectorId);
        return session != null && CortexLocalBusProtocolV2.SIGNAL_PROTOCOL.equals(session.selectedProtocol)
                && supports(session, CortexLocalBusProtocolV2.SIGNAL_PROTOCOL);
    }

    public static boolean isRelayV2Connected() {
        return hasAuthenticatedV2Session(SECOND_BRAIN);
    }

    public static boolean requestAction(
            Context context,
            String requestId,
            String logicalSignalId,
            String capabilityId,
            String inputText) {
        JSONObject request = CortexLocalBusProtocolV2.actionRequest(requestId, logicalSignalId, capabilityId, inputText);
        return send(context, SECOND_BRAIN, CortexLocalBusProtocolV2.MSG_ACTION_REQUEST, request,
                "action", CortexLocalBusProtocolV2.ACTION_BRIDGE);
    }

    public static boolean updateMechanicalPolicy(
            Context context,
            long version,
            int retentionHours,
            JSONArray disabledNoiseRules) {
        JSONObject request = CortexLocalBusProtocolV2.mechanicalPolicy(version, retentionHours, disabledNoiseRules);
        try {
            request.put("request_id", "cortex_policy_" + java.util.UUID.randomUUID());
        } catch (Throwable e) {
            return false;
        }
        return send(context, SECOND_BRAIN, CortexLocalBusProtocolV2.MSG_POLICY_UPDATE, request,
                "policy", CortexLocalBusProtocolV2.POLICY_FEEDBACK);
    }

    private static boolean send(Context context, String connectorId, int what, JSONObject request,
                                String kind, String requiredCapability) {
        Session session = sessions.get(connectorId);
        if (session == null || !CortexLocalBusProtocolV2.SIGNAL_PROTOCOL.equals(session.selectedProtocol)) {
            persistSendFailure(context, kind, "NO_V2_SESSION");
            return false;
        }
        if (!supports(session, requiredCapability)) {
            persistSendFailure(context, kind, "CAPABILITY_NOT_ADVERTISED:" + requiredCapability);
            return false;
        }
        // Every outbound control request is registered as outstanding BEFORE it is sent, so a
        // result cannot be correlated unless Cortex genuinely minted the request id it names.
        String requestId = request.optString("request_id", "");
        if (!CortexRelayControlCorrelatorV2.registerOutstanding(requestId, kind, System.currentTimeMillis())) {
            persistSendFailure(context, kind, "REQUEST_ID_NOT_CORRELATABLE");
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

    private static boolean supports(Session session, String capability) {
        if (session == null || capability == null || capability.trim().isEmpty()) return false;
        try {
            JSONArray caps = new JSONArray(session.capabilitiesJson);
            for (int i = 0; i < caps.length(); i++) {
                if (capability.equals(caps.optString(i, "").trim())) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static void recordControlResult(Context context, CortexConnectorRegistryV1.Identity identity, int what, String raw) {
        if (context == null || identity == null) return;
        String kind = what == CortexLocalBusProtocolV2.MSG_ACTION_RESULT ? "action" :
                what == CortexLocalBusProtocolV2.MSG_POLICY_RESULT ? "policy" : "unknown";
        JSONObject result;
        try { result = new JSONObject(raw == null || raw.trim().isEmpty() ? "{}" : raw); }
        catch (Throwable e) {
            result = new JSONObject();
            try { result.put("status", "INVALID_RESULT_JSON"); } catch (Throwable ignored) {}
        }

        // CORRELATION GATE. An inbound control result is a claim, not a fact. It may only reach
        // the authoritative "last_<kind>_result" slot when it names an outstanding request id
        // Cortex itself minted, of this same kind, within the correlator's TTL, and not already
        // answered. Everything else is retained under a distinct, explicitly-diagnostic key and
        // must never influence authoritative state.
        String requestId = result.optString("request_id", "");
        CortexRelayControlCorrelatorV2.Verdict verdict =
                CortexRelayControlCorrelatorV2.correlate(requestId, kind, System.currentTimeMillis());
        try {
            result.put("correlation_verdict", verdict.name());
            result.put("authoritative", verdict.authoritative());
        } catch (Throwable ignored) {}

        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (verdict.authoritative()) {
            edit.putString("last_" + kind + "_result", result.toString());
            edit.putLong("last_" + kind + "_result_at", System.currentTimeMillis());
        } else {
            edit.putString("last_" + kind + "_uncorrelated_result", result.toString());
            edit.putLong("last_" + kind + "_uncorrelated_result_at", System.currentTimeMillis());
        }
        edit.apply();
        logControl(context, identity, "result_" + kind + "_" + verdict.name().toLowerCase(), result);
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
            out.put("last_send_failure", p.getString("last_send_failure", ""));
            out.put("last_action_result", parseOrString(p.getString("last_action_result", "")));
            out.put("last_policy_result", parseOrString(p.getString("last_policy_result", "")));
            out.put("last_action_uncorrelated_result",
                    parseOrString(p.getString("last_action_uncorrelated_result", "")));
            out.put("last_policy_uncorrelated_result",
                    parseOrString(p.getString("last_policy_uncorrelated_result", "")));
            out.put("outstanding_requests",
                    CortexRelayControlCorrelatorV2.outstandingCount(System.currentTimeMillis()));
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
                .putString("last_send_failure", "")
                .putLong("connected_at", session.connectedAt)
                .apply();
    }

    private static void persistRequest(Context context, String kind, JSONObject request) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_request_kind", kind)
                .putString("last_request_json", request.toString())
                .putString("last_send_failure", "")
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

    private static void logControl(Context context, CortexConnectorRegistryV1.Identity identity,
                                   String stage, JSONObject payload) {
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
