package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

/** Maps trusted Relay payloads into Cortex's single cognitive notification-evidence path. */
public final class CortexConnectorIngestV1 {
    private CortexConnectorIngestV1() {}

    public static Result ingest(Context context, VaultDb db, CortexConnectorRegistryV1.Identity identity,
                                CortexLocalBusProtocolV1.Event event) {
        if (context == null || db == null || identity == null || event == null) throw new IllegalArgumentException("ingest args required");
        if (!identity.connectorId.equals(event.connectorId)) throw new IllegalArgumentException("connector_id does not match caller identity");
        if (!"NOTIFICATION".equals(event.sourceType)) throw new IllegalArgumentException("source_type not supported in Local Bus V1");
        if (!PrivacyPolicy.canCollect(context, "notifications")) return new Result(0, "POLICY_BLOCKED");

        JSONObject o = event.json;
        String title = clean(o.optString("title", ""));
        String text = clean(o.optString("text", ""));
        String expanded = clean(o.optString("expanded_text", ""));
        String conversationTitle = clean(o.optString("conversation_title", ""));
        String notificationKey = clean(o.optString("notification_key", ""));
        boolean ongoing = o.optBoolean("ongoing", false);
        String bestText = !expanded.isEmpty() ? expanded : text;
        if (title.isEmpty() && bestText.isEmpty()) return new Result(0, "EMPTY");
        String body = (title + (title.isEmpty() || bestText.isEmpty() ? "" : "\n") + bestText).trim();

        JSONObject meta = new JSONObject();
        try {
            JSONObject incoming = o.optJSONObject("metadata");
            if (incoming != null) copy(incoming, meta);
            meta.put("capture_kind", "connector_notification");
            meta.put("capture_mode", "relay_local_bus");
            meta.put("source_connector", identity.connectorId);
            meta.put("connector_package", identity.packageName);
            meta.put("connector_event_id", event.eventId);
            meta.put("source_priority", identity.sourcePriority);
            meta.put("package", event.sourcePackage);
            meta.put("posted_at", event.occurredAt);
            meta.put("ongoing", ongoing);
            meta.put("has_visible_text", !body.isEmpty());
            if (!notificationKey.isEmpty()) meta.put("notification_key", notificationKey);
            if (!conversationTitle.isEmpty()) meta.put("conversation_title", conversationTitle);
            JSONArray messages = o.optJSONArray("messages");
            if (messages != null) meta.put("messages", new JSONArray(messages.toString()));
        } catch (Throwable ignored) {}

        PhoneContextStore.ensure(db);
        try {
            PhoneContextStore.record(db, "notification_context", "connector:" + identity.connectorId,
                    event.sourcePackage, event.sourcePackage, "", "notification_connector", body,
                    event.occurredAt, meta);
        } catch (Throwable ignored) {}

        MasterRelevanceFilter.Signal signal = new MasterRelevanceFilter.Signal(
                "notification", event.sourcePackage, title, body, meta.toString(), event.occurredAt, ongoing);

        long signalId = RawSignalStore.capture(context, db, signal);
        if (signalId <= 0) return new Result(signalId, "RAW_CAPTURE_FAILED");
        mergeConnectorMetadata(db, signalId, meta);

        long itemId = RawSignalStore.promotedItemId(db, signalId);
        long threadId = RawSignalStore.threadId(db, signalId);
        try { NotificationEnrichmentEngine.enrich(db, signalId, itemId, threadId, signal); } catch (Throwable ignored) {}

        if (threadId > 0 && RawSignalStore.shouldEnqueueLegacyModel(db, signalId)) {
            try { ThreadModelAdjudicator.enqueue(context, threadId, signalId); } catch (Throwable ignored) {}
        }
        if (itemId > 0) {
            try { AnalysisQueue.kick(context, null, null); } catch (Throwable ignored) {}
        }
        return new Result(signalId, "ACCEPTED");
    }

    private static void mergeConnectorMetadata(VaultDb db, long signalId, JSONObject relayMeta) {
        if (db == null || signalId <= 0 || relayMeta == null) return;
        Cursor c = db.getReadableDatabase().query("raw_signals", new String[]{"metadata_json"},
                "id=?", new String[]{String.valueOf(signalId)}, null, null, null, "1");
        try {
            JSONObject merged = new JSONObject();
            if (c.moveToFirst()) {
                String raw = c.isNull(0) ? "" : c.getString(0);
                if (raw != null && !raw.trim().isEmpty()) {
                    try { copy(new JSONObject(raw), merged); } catch (Throwable ignored) {}
                }
            }
            try {
                merged.put("relay_connector_enrichment", new JSONObject(relayMeta.toString()));
                merged.put("source_connector", relayMeta.optString("source_connector", "second_brain"));
                merged.put("connector_event_id", relayMeta.optString("connector_event_id", ""));
                JSONObject semantic = relayMeta.optJSONObject("relay_semantic_v2");
                if (semantic != null) merged.put("relay_semantic_v2", new JSONObject(semantic.toString()));
                JSONArray actions = relayMeta.optJSONArray("relay_action_capabilities_v1");
                if (actions != null) merged.put("relay_action_capabilities_v1", new JSONArray(actions.toString()));
            } catch (Throwable ignored) {}
            ContentValues v = new ContentValues();
            v.put("metadata_json", merged.toString());
            v.put("updated_at", System.currentTimeMillis());
            db.getWritableDatabase().update("raw_signals", v, "id=?", new String[]{String.valueOf(signalId)});
        } finally {
            c.close();
        }
    }

    private static void copy(JSONObject from, JSONObject to) {
        if (from == null || to == null) return;
        java.util.Iterator<String> keys = from.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.trim().isEmpty()) continue;
            try { to.put(key, from.opt(key)); } catch (Throwable ignored) {}
        }
    }

    private static String clean(String s) { return s == null ? "" : s.replace('\u0000', ' ').trim(); }

    public static final class Result {
        public final long signalId;
        public final String status;
        Result(long signalId, String status) { this.signalId = signalId; this.status = status == null ? "" : status; }
        public boolean accepted() { return signalId > 0 && "ACCEPTED".equals(status); }
    }
}
