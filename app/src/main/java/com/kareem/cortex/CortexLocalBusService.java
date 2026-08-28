package com.kareem.cortex;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

/**
 * Cortex local hub endpoint. Exported intentionally, but every Messenger call is authenticated
 * from Android's sender UID -> installed package registry before payload data is trusted.
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
        if (thread != null) try { thread.quitSafely(); } catch (Throwable ignored) {}
        thread = null; messenger = null;
        super.onDestroy();
    }

    private boolean handle(Message msg) {
        CortexConnectorRegistryV1.Identity identity = CortexConnectorRegistryV1.resolve(this, msg.sendingUid);
        if (identity == null) {
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
            String claimed = clean(data.getString(CortexLocalBusProtocolV1.KEY_CONNECTOR_ID, ""));
            if (!claimed.isEmpty() && !identity.connectorId.equals(claimed)) {
                reply(msg, false, "IDENTITY_MISMATCH", "connector_id does not match Android caller UID", "", 0);
                return true;
            }
            VaultDb db = null;
            try {
                db = new VaultDb(getApplicationContext());
                CortexLocalBusStoreV1.hello(db, identity, data.getString(CortexLocalBusProtocolV1.KEY_CAPABILITIES_JSON, "[]"));
                reply(msg, true, "HELLO_ACCEPTED", identity.connectorId, "", 0);
            } catch (Throwable e) {
                reply(msg, false, "HELLO_FAILED", safe(e), "", 0);
            } finally { close(db); }
            return true;
        }
        if (msg.what != CortexLocalBusProtocolV1.MSG_INGEST) {
            reply(msg, false, "UNKNOWN_MESSAGE", "Unsupported message type", "", 0);
            return true;
        }

        String raw = data.getString(CortexLocalBusProtocolV1.KEY_EVENT_JSON, "");
        CortexLocalBusProtocolV1.Event event;
        try {
            event = CortexLocalBusProtocolV1.parseEvent(raw);
            if (!identity.connectorId.equals(event.connectorId)) throw new IllegalArgumentException("connector_id does not match caller identity");
        } catch (Throwable e) {
            reply(msg, false, "INVALID_EVENT", safe(e), "", 0);
            return true;
        }

        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            CortexLocalBusStoreV1.ensure(db);
            if (CortexLocalBusStoreV1.alreadyAccepted(db, event.eventId)) {
                reply(msg, true, "DUPLICATE_ACCEPTED", "Event was already ingested", event.eventId, existingSignalId(db, event.eventId));
                return true;
            }
            CortexLocalBusStoreV1.recordReceived(db, identity, event);
            CortexConnectorIngestV1.Result result = CortexConnectorIngestV1.ingest(getApplicationContext(), db, identity, event);
            if (result.accepted()) {
                CortexLocalBusStoreV1.accepted(db, identity, event.eventId, result.signalId);
                reply(msg, true, "ACCEPTED", "Canonical Cortex ingest succeeded", event.eventId, result.signalId);
            } else {
                CortexLocalBusStoreV1.rejected(db, identity, event.eventId, result.status);
                reply(msg, false, result.status, "Connector event was not admitted", event.eventId, result.signalId);
            }
        } catch (Throwable e) {
            try { if (db != null) CortexLocalBusStoreV1.rejected(db, identity, event.eventId, safe(e)); } catch (Throwable ignored) {}
            reply(msg, false, "INGEST_FAILED", safe(e), event.eventId, 0);
        } finally { close(db); }
        return true;
    }

    private long existingSignalId(VaultDb db, String eventId) {
        android.database.Cursor c = db.getReadableDatabase().rawQuery("SELECT signal_id FROM connector_ingest_events WHERE event_id=? LIMIT 1", new String[]{eventId});
        try { return c.moveToFirst() ? c.getLong(0) : 0L; } finally { c.close(); }
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
    private static String safe(Throwable e) { if (e == null) return "unknown"; String x = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()); return x.length() <= 400 ? x : x.substring(0, 400); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
