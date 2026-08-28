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
            String claimed = clean(data.getString(CortexLocalBusProtocolV1.KEY_CONNECTOR_ID, ""));
            if (!claimed.isEmpty() && !identity.connectorId.equals(claimed)) {
                reply(msg, false, "IDENTITY_MISMATCH", "connector_id does not match Android caller UID", "", 0);
                return true;
            }
            VaultDb db = null;
            try {
                db = new VaultDb(getApplicationContext());
                CortexLocalBusStoreV1.hello(db, identity, data.getString(CortexLocalBusProtocolV1.KEY_CAPABILITIES_JSON, "[]"));
                JSONObject meta=new JSONObject();meta.put("connector_id",identity.connectorId);meta.put("package",identity.packageName);meta.put("sending_uid",msg.sendingUid);
                DiagnosticsLog.info(db,"local_bus","hello","accepted",0,0,0,0,0,0,meta);
                reply(msg, true, "HELLO_ACCEPTED", identity.connectorId, "", 0);
            } catch (Throwable e) {
                if(db!=null)DiagnosticsLog.error(db,"local_bus","hello",e,"HELLO_FAILED",0,0,0,0,0,null);
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
            logInvalid(identity,e);
            reply(msg, false, "INVALID_EVENT", safe(e), "", 0);
            return true;
        }

        VaultDb db = null;
        try {
            db = new VaultDb(getApplicationContext());
            CortexLocalBusStoreV1.ensure(db);
            if (CortexLocalBusStoreV1.alreadyAccepted(db, event.eventId)) {
                long existing=existingSignalId(db,event.eventId);
                logDelivery(db,identity,event,"DUPLICATE_ACCEPTED",existing,"");
                reply(msg, true, "DUPLICATE_ACCEPTED", "Event was already ingested", event.eventId, existing);
                return true;
            }
            CortexLocalBusStoreV1.recordReceived(db, identity, event);
            logDelivery(db,identity,event,"RECEIVED",0,"");
            CortexConnectorIngestV1.Result result = CortexConnectorIngestV1.ingest(getApplicationContext(), db, identity, event);
            if (result.accepted()) {
                CortexLocalBusStoreV1.accepted(db, identity, event.eventId, result.signalId);
                logDelivery(db,identity,event,"ACCEPTED",result.signalId,"");
                reply(msg, true, "ACCEPTED", "Canonical Cortex ingest succeeded", event.eventId, result.signalId);
            } else {
                CortexLocalBusStoreV1.rejected(db, identity, event.eventId, result.status);
                logDelivery(db,identity,event,result.status,result.signalId,"rejected");
                reply(msg, false, result.status, "Connector event was not admitted", event.eventId, result.signalId);
            }
        } catch (Throwable e) {
            try { if (db != null) {CortexLocalBusStoreV1.rejected(db, identity, event.eventId, safe(e));DiagnosticsLog.error(db,"local_bus","ingest",e,"INGEST_FAILED",0,0,0,0,0,deliveryMeta(identity,event));} } catch (Throwable ignored) {}
            reply(msg, false, "INGEST_FAILED", safe(e), event.eventId, 0);
        } finally { close(db); }
        return true;
    }

    private void logDelivery(VaultDb db,CortexConnectorRegistryV1.Identity identity,CortexLocalBusProtocolV1.Event event,String status,long signalId,String detail){
        try{JSONObject meta=deliveryMeta(identity,event);meta.put("detail",detail);DiagnosticsLog.info(db,"local_bus","ingest_"+status.toLowerCase(),status,0,0,signalId,0,0,0,meta);}catch(Throwable ignored){}
    }
    private JSONObject deliveryMeta(CortexConnectorRegistryV1.Identity identity,CortexLocalBusProtocolV1.Event event){
        JSONObject meta=new JSONObject();try{meta.put("connector_id",identity.connectorId);meta.put("connector_package",identity.packageName);meta.put("event_id",event.eventId);meta.put("source_type",event.sourceType);meta.put("source_package",event.sourcePackage);meta.put("occurred_at",event.occurredAt);}catch(Throwable ignored){}return meta;
    }
    private void logInvalid(CortexConnectorRegistryV1.Identity identity,Throwable e){VaultDb db=null;try{db=new VaultDb(getApplicationContext());JSONObject meta=new JSONObject();meta.put("connector_id",identity.connectorId);meta.put("connector_package",identity.packageName);DiagnosticsLog.error(db,"local_bus","parse_event",e,"INVALID_EVENT",0,0,0,0,0,meta);}catch(Throwable ignored){}finally{close(db);}}
    private void logUnauthorised(int uid){VaultDb db=null;try{db=new VaultDb(getApplicationContext());JSONObject meta=new JSONObject();meta.put("sending_uid",uid);DiagnosticsLog.warn(db,"local_bus","caller_rejected","unauthorized","UNAUTHORIZED_CALLER",0,0,0,0,0,meta);}catch(Throwable ignored){}finally{close(db);}}

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
