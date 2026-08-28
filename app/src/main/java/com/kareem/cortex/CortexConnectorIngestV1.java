package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;

/** Maps trusted local connector payloads into the same RawSignal -> V4 Evidence path Cortex already uses. */
public final class CortexConnectorIngestV1 {
    private CortexConnectorIngestV1() {}

    public static Result ingest(Context context, VaultDb db, CortexConnectorRegistryV1.Identity identity, CortexLocalBusProtocolV1.Event event) {
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
            meta.put("capture_mode", "live_tunnel");
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
            if (messages != null) meta.put("messages", messages);
        } catch (Throwable ignored) {}

        PhoneContextStore.ensure(db);
        try {
            PhoneContextStore.record(db, "notification_context", "connector:" + identity.connectorId,
                    event.sourcePackage, event.sourcePackage, "", "notification_connector", body, event.occurredAt, meta);
        } catch (Throwable ignored) {}

        MasterRelevanceFilter.Signal signal = new MasterRelevanceFilter.Signal(
                "notification", event.sourcePackage, title, body, meta.toString(), event.occurredAt, ongoing);
        long signalId = NotificationSignalIngressV1.capture(db, signal);
        if (signalId <= 0) return new Result(signalId, "RAW_CAPTURE_FAILED");

        // Connector payload may be richer than Cortex's native listener even when both sensors saw
        // the same physical notification. Preserve that extra structure additively on Evidence.
        appendConnectorEnrichment(db, signalId, identity, event, body);

        long itemId = RawSignalStore.promotedItemId(db, signalId);
        long threadId = RawSignalStore.threadId(db, signalId);
        // Critical dedupe case: the native listener may have created the canonical Raw Signal first
        // with only a short preview, while Second Brain later supplies expanded text containing the
        // actual request/deadline. Re-run the same relevance boundary against the trusted additive
        // enrichment instead of letting exact-notification dedupe accidentally discard semantics.
        if (itemId <= 0) {
            try { itemId = RawSignalStore.promoteTrustedEnrichment(db, signalId, threadId, signal); }
            catch (Throwable ignored) {}
        }
        try { NotificationEnrichmentEngine.enrich(db, signalId, itemId, threadId, signal); } catch (Throwable ignored) {}
        try { if (threadId > 0) ThreadModelAdjudicator.enqueue(context, threadId, signalId); } catch (Throwable ignored) {}
        try { if (itemId > 0) AnalysisQueue.kick(context, null, null); } catch (Throwable ignored) {}

        // A meaningful connector event should become visible to canonical Memory/Situations without
        // waiting for the next bounded startup/backfill batch. Context-only notifications still stop
        // at Evidence because promoteTrustedEnrichment uses the same relevance governor.
        if (itemId > 0) CognitiveRealtimeProjectionV4.schedule(context, signalId);
        return new Result(signalId, "ACCEPTED");
    }

    private static void appendConnectorEnrichment(VaultDb db, long signalId, CortexConnectorRegistryV1.Identity identity, CortexLocalBusProtocolV1.Event event, String body) {
        try {
            Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT object_id FROM v4_legacy_map WHERE legacy_table='raw_signals' AND legacy_id=? AND object_type='EVIDENCE' LIMIT 1",
                    new String[]{String.valueOf(signalId)});
            String evidenceId;
            try { evidenceId = c.moveToFirst() ? clean(c.getString(0)) : ""; }
            finally { c.close(); }
            if (evidenceId.isEmpty()) return;
            CognitiveStoreV4.appendEvidenceAnalysis(db, evidenceId, "CONNECTOR_ENRICHMENT",
                    "local_bus:" + identity.connectorId, "1", body, event.json.toString());
        } catch (Throwable ignored) {}
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
