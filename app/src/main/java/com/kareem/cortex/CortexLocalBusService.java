package com.kareem.cortex;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import org.json.JSONObject;

/**
 * Cortex local hub endpoint. Exported intentionally, but every Messenger call is authenticated
 * from Android's sender UID -> installed package registry before payload data is trusted.
 *
 * Local Bus V1 remains the compatibility baseline. Relay V2 is selected only when an authenticated
 * Second Brain HELLO explicitly advertises CORTEX_SIGNAL_V2. V2 payloads are adapted into the same
 * canonical V1 ingest boundary so event-id dedupe, evidence admission and correlated ACK semantics
 * remain single-source-of-truth.
 */
public final class CortexLocalBusService extends Service {
    private HandlerThread thread;
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("cortex-local-bus");
        thread.start();
        messenger = new Messenger(new Handler(thread.getLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        if (messenger == null) return null;
        return messenger.getBinder();
    }

    @Override public void onDestroy() {
        CortexRelayBridgeV2.clearAuthenticatedSession(getApplicationContext(), "second_brain");
        if (thread != null) try { thread.quitSafely(); } catch (Throwable ignored) {}
        thread = null; messenger = null;
        super.onDestroy();
    }

    private boolean handle(Message msg) {
        CortexConnectorRegistryV1.Identity identity = CortexConnectorRegistryV1.resolve(this, msg.sendingUid);
        if (identity == null) {
            logUnauthorised(msg.sendingUid);
            reply(msg, false, "UNAUTHORIZED_CALLER", "Caller package is not registered", "", 0);
            return true;
        }
        if (msg.what == CortexLocalBusProtocolV1.MSG_PING) {
            reply(msg, true, "READY", "Cortex Local Bus V1", "", 0);
            return true;
        }

        Bundle data = msg.getData();
        if (data == null) data = Bundle.EMPTY;

        if (msg.what == CortexLocalBusProtocolV1.MSG_HELLO) {
            return handleHello(msg, data, identity);
        }

        if (msg.what == CortexLocalBusProtocolV2.MSG_ACTION_RESULT ||
                msg.what == CortexLocalBusProtocolV2.MSG_POLICY_RESULT) {
            if (!"second_brain".equals(identity.connectorId) ||
                    !CortexRelayBridgeV2.hasAuthenticatedV2Session(identity.connectorId)) {
                reply(msg, false, "V2_SESSION_REQUIRED", "Control result arrived outside negotiated Relay V2 session", "", 0);
                return true;
            }
            String rawResult = data.getString(CortexLocalBusProtocolV2.KEY_RESULT_JSON, "");
            CortexRelayBridgeV2.recordControlResult(getApplicationContext(), identity, msg.what, rawResult);
            return true;
        }

        if (msg.what == CortexLocalBusProtocolV2.MSG_INGEST_V2) {
            return handleV2Ingest(msg, data, identity);
        }

        if (msg.what == CortexLocalBusProtocolV1.MSG_INGEST) {
            return handleV1Ingest(msg, data, identity);
        }

        reply(msg, false, "UNKNOWN_MESSAGE", "Unsupported message type", "", 0);
        return true;
    }

    private boolean handleHello(Message msg, Bundle data, CortexConnectorRegistryV1.Identity identity) {
        String claimed = clean(data.getString(CortexLocalBusProtocolV1.KEY_CONNECTOR_ID, ""));
        if (!claimed.isEmpty() && !identity.connectorId.equals(claimed)) {
            reply(msg, false, "IDENTITY_MISMATCH", "connector_id does not match Android caller UID", "", 0);
            return true;
        }

        String relayCapabilities = data.getString(CortexLocalBusProtocolV2.KEY_RELAY_CAPABILITIES_JSON, "[]");
        boolean selectV2 = "second_brain".equals(identity.connectorId) &&
                CortexLocalBusProtocolV2.relayAdvertisesSignalV2(relayCapabilities);
        String selectedProtocol = selectV2 ? CortexLocalBusProtocolV2.SIGNAL_PROTOCOL : CortexLocalBusProtocolV1.PROTOCOL;

        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            // Preserve the exact stable V1 capability storage contract.
            CortexLocalBusStoreV1.hello(db, identity,
                    data.getString(CortexLocalBusProtocolV1.KEY_CAPABILITIES_JSON, "[]"));
            JSONObject meta = new JSONObject();
            meta.put("connector_id", identity.connectorId);
            meta.put("package", identity.packageName);
            meta.put("sending_uid", msg.sendingUid);
            meta.put("selected_protocol", selectedProtocol);
            meta.put("relay_capabilities_json", relayCapabilities);
            DiagnosticsLog.info(db, "local_bus", "hello", "accepted", 0, 0, 0, 0, 0, 0, meta);

            if (selectV2 && msg.replyTo != null && messenger != null) {
                CortexRelayBridgeV2.registerAuthenticatedSession(
                        getApplicationContext(), identity, msg.replyTo, messenger,
                        relayCapabilities, selectedProtocol);
            } else {
                CortexRelayBridgeV2.clearAuthenticatedSession(getApplicationContext(), identity.connectorId);
            }
            replyHello(msg, true, "HELLO_ACCEPTED", identity.connectorId, selectedProtocol);
        } catch (Throwable e) {
            if (db != null) DiagnosticsLog.error(db, "local_bus", "hello", e, "HELLO_FAILED", 0, 0, 0, 0, 0, null);
            replyHello(msg, false, "HELLO_FAILED", safe(e), CortexLocalBusProtocolV1.PROTOCOL);
        } finally { close(db); }
        return true;
    }

    private boolean handleV1Ingest(Message msg, Bundle data, CortexConnectorRegistryV1.Identity identity) {
        String raw = data.getString(CortexLocalBusProtocolV1.KEY_EVENT_JSON, "");
        CortexLocalBusProtocolV1.Event event;
        try {
            event = CortexLocalBusProtocolV1.parseEvent(raw);
            if (!identity.connectorId.equals(event.connectorId)) {
                throw new IllegalArgumentException("connector_id does not match caller identity");
            }
        } catch (Throwable e) {
            logInvalid(identity, e);
            reply(msg, false, "INVALID_EVENT", safe(e), "", 0);
            return true;
        }
        return ingestCanonical(msg, identity, event, null);
    }

    private boolean handleV2Ingest(Message msg, Bundle data, CortexConnectorRegistryV1.Identity identity) {
        if (!"second_brain".equals(identity.connectorId) ||
                !CortexRelayBridgeV2.hasAuthenticatedV2Session(identity.connectorId)) {
            reply(msg, false, "V2_NOT_NEGOTIATED", "CORTEX_SIGNAL_V2 requires an authenticated negotiated HELLO session", "", 0);
            return true;
        }
        String raw = data.getString(CortexLocalBusProtocolV1.KEY_EVENT_JSON, "");
        CortexLocalBusProtocolV2.Event v2;
        CortexLocalBusProtocolV1.Event canonical;
        try {
            v2 = CortexLocalBusProtocolV2.parseEvent(raw);
            if (!identity.connectorId.equals(v2.connectorId)) {
                throw new IllegalArgumentException("connector_id does not match caller identity");
            }
            canonical = CortexLocalBusProtocolV2.toCanonicalV1(v2);
        } catch (Throwable e) {
            logInvalid(identity, e);
            reply(msg, false, "INVALID_V2_EVENT", safe(e), "", 0);
            return true;
        }
        return ingestCanonical(msg, identity, canonical, v2);
    }

    private boolean ingestCanonical(
            Message msg,
            CortexConnectorRegistryV1.Identity identity,
            CortexLocalBusProtocolV1.Event event,
            CortexLocalBusProtocolV2.Event v2Event) {
        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            CortexLocalBusStoreV1.ensure(db);
            if (CortexLocalBusStoreV1.alreadyAccepted(db, event.eventId)) {
                long existing = existingSignalId(db, event.eventId);
                logDelivery(db, identity, event, "DUPLICATE_ACCEPTED", existing,
                        v2Event == null ? "" : "protocol=CORTEX_SIGNAL_V2");
                if (v2Event != null) {
                    CortexRelayBridgeV2.recordSignal(getApplicationContext(), identity, v2Event, existing);
                }
                reply(msg, true, "DUPLICATE_ACCEPTED", "Event was already ingested", event.eventId, existing);
                return true;
            }

            CortexLocalBusStoreV1.recordReceived(db, identity, event);
            logDelivery(db, identity, event, "RECEIVED", 0,
                    v2Event == null ? "" : "protocol=CORTEX_SIGNAL_V2");
            CortexConnectorIngestV1.Result result = CortexConnectorIngestV1.ingest(
                    getApplicationContext(), db, identity, event);
            if (result.accepted()) {
                CortexLocalBusStoreV1.accepted(db, identity, event.eventId, result.signalId);
                logDelivery(db, identity, event, "ACCEPTED", result.signalId,
                        v2Event == null ? "" : "protocol=CORTEX_SIGNAL_V2");
                if (v2Event != null) {
                    CortexRelayBridgeV2.recordSignal(getApplicationContext(), identity, v2Event, result.signalId);
                }
                reply(msg, true, "ACCEPTED", "Canonical Cortex ingest succeeded", event.eventId, result.signalId);
            } else {
                CortexLocalBusStoreV1.rejected(db, identity, event.eventId, result.status);
                logDelivery(db, identity, event, result.status, result.signalId,
                        v2Event == null ? "rejected" : "rejected; protocol=CORTEX_SIGNAL_V2");
                reply(msg, false, result.status, "Connector event was not admitted", event.eventId, result.signalId);
            }
        } catch (Throwable e) {
            try {
                if (db != null) {
                    CortexLocalBusStoreV1.rejected(db, identity, event.eventId, safe(e));
                    DiagnosticsLog.error(db, "local_bus", "ingest", e, "INGEST_FAILED", 0, 0, 0, 0, 0,
                            deliveryMeta(identity, event));
                }
            } catch (Throwable ignored) {}
            reply(msg, false, "INGEST_FAILED", safe(e), event.eventId, 0);
        } finally { close(db); }
        return true;
    }

    private void logDelivery(VaultDb db, CortexConnectorRegistryV1.Identity identity,
                             CortexLocalBusProtocolV1.Event event, String status, long signalId, String detail) {
        try {
            JSONObject meta = deliveryMeta(identity, event);
            meta.put("detail", detail);
            DiagnosticsLog.info(db, "local_bus", "ingest_" + status.toLowerCase(), status,
                    0, 0, signalId, 0, 0, 0, meta);
        } catch (Throwable ignored) {}
    }

    private JSONObject deliveryMeta(CortexConnectorRegistryV1.Identity identity, CortexLocalBusProtocolV1.Event event) {
        JSONObject meta = new JSONObject();
        try {
            meta.put("connector_id", identity.connectorId);
            meta.put("connector_package", identity.packageName);
            meta.put("event_id", event.eventId);
            meta.put("source_type", event.sourceType);
            meta.put("source_package", event.sourcePackage);
            meta.put("occurred_at", event.occurredAt);
            JSONObject incoming = event.json.optJSONObject("metadata");
            JSONObject signalV2 = incoming == null ? null : incoming.optJSONObject("local_bus_signal_v2");
            if (signalV2 != null) meta.put("signal_protocol", CortexLocalBusProtocolV2.SIGNAL_PROTOCOL);
        } catch (Throwable ignored) {}
        return meta;
    }

    private void logInvalid(CortexConnectorRegistryV1.Identity identity, Throwable e) {
        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            JSONObject meta = new JSONObject();
            meta.put("connector_id", identity.connectorId);
            meta.put("connector_package", identity.packageName);
            DiagnosticsLog.error(db, "local_bus", "parse_event", e, "INVALID_EVENT", 0, 0, 0, 0, 0, meta);
        } catch (Throwable ignored) {
        } finally { close(db); }
    }

    private void logUnauthorised(int uid) {
        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            JSONObject meta = new JSONObject();
            meta.put("sending_uid", uid);
            DiagnosticsLog.warn(db, "local_bus", "caller_rejected", "unauthorized", "UNAUTHORIZED_CALLER",
                    0, 0, 0, 0, 0, meta);
        } catch (Throwable ignored) {
        } finally { close(db); }
    }

    private long existingSignalId(VaultDb db, String eventId) {
        android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT signal_id FROM connector_ingest_events WHERE event_id=? LIMIT 1",
                new String[]{eventId});
        try { return c.moveToFirst() ? c.getLong(0) : 0L; } finally { c.close(); }
    }

    private void replyHello(Message incoming, boolean ok, String status, String detail, String selectedProtocol) {
        if (incoming == null || incoming.replyTo == null) return;
        try {
            Message out = Message.obtain(null,
                    ok ? CortexLocalBusProtocolV1.MSG_ACK : CortexLocalBusProtocolV1.MSG_ERROR);
            Bundle b = new Bundle();
            b.putString(CortexLocalBusProtocolV1.KEY_STATUS, clean(status));
            b.putString(CortexLocalBusProtocolV1.KEY_DETAIL, clean(detail));
            b.putString(CortexLocalBusProtocolV1.KEY_EVENT_ID, "");
            b.putLong(CortexLocalBusProtocolV1.KEY_SIGNAL_ID, 0L);
            b.putString(CortexLocalBusProtocolV2.KEY_SELECTED_PROTOCOL, clean(selectedProtocol));
            out.setData(b);
            incoming.replyTo.send(out);
        } catch (Throwable ignored) {}
    }

    private void reply(Message incoming, boolean ok, String status, String detail, String eventId, long signalId) {
        if (incoming == null || incoming.replyTo == null) return;
        try {
            Message out = Message.obtain(null, ok ? CortexLocalBusProtocolV1.MSG_ACK : CortexLocalBusProtocolV1.MSG_ERROR);
            Bundle b = new Bundle();
            b.putString(CortexLocalBusProtocolV1.KEY_STATUS, clean(status));
            b.putString(CortexLocalBusProtocolV1.KEY_DETAIL, clean(detail));
            b.putString(CortexLocalBusProtocolV1.KEY_EVENT_ID, clean(eventId));
            b.putLong(CortexLocalBusProtocolV1.KEY_SIGNAL_ID, signalId);
            out.setData(b);
            incoming.replyTo.send(out);
        } catch (Throwable ignored) {}
    }

    private static void close(VaultDb db) { if (db != null) try { db.close(); } catch (Throwable ignored) {} }
    private static String safe(Throwable e) {
        if (e == null) return "unknown";
        String x = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return x.length() <= 400 ? x : x.substring(0, 400);
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
