package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Read-only projection of trusted Cortex Relay notification evidence for product UI. */
public final class RelayEvidenceStore {
    private RelayEvidenceStore() {}

    public static final class Item {
        public final long signalId;
        public final long threadId;
        public final String source;
        public final String title;
        public final String body;
        public final String disposition;
        public final String cognitiveState;
        public final String cognitiveVersion;
        public final String finalReason;
        public final long occurredAt;
        public final int importance;
        public final double confidence;
        public final long modelRunId;
        public final String provider;
        public final String model;
        public final String route;
        public final String runState;
        public final double modelConfidence;

        Item(long signalId, long threadId, String source, String title, String body,
             String disposition, String cognitiveState, String cognitiveVersion, String finalReason,
             long occurredAt, int importance, double confidence, long modelRunId, String provider,
             String model, String route, String runState, double modelConfidence) {
            this.signalId = signalId;
            this.threadId = threadId;
            this.source = nz(source);
            this.title = nz(title);
            this.body = nz(body);
            this.disposition = nz(disposition);
            this.cognitiveState = nz(cognitiveState);
            this.cognitiveVersion = nz(cognitiveVersion);
            this.finalReason = nz(finalReason);
            this.occurredAt = occurredAt;
            this.importance = importance;
            this.confidence = confidence;
            this.modelRunId = modelRunId;
            this.provider = nz(provider);
            this.model = nz(model);
            this.route = nz(route);
            this.runState = nz(runState);
            this.modelConfidence = modelConfidence;
        }

        public boolean isPrimaryQwen() {
            return "cognitive_v2_primary".equalsIgnoreCase(route)
                    || cognitiveVersion.toLowerCase().contains("v2_primary");
        }

        public String brainLabel() {
            if (isPrimaryQwen()) return "QWEN · V2 PRIMARY";
            if ("cognitive_v2_canary".equalsIgnoreCase(route)
                    || cognitiveVersion.toLowerCase().contains("v2_canary")) return "QWEN · V2 CANARY";
            if (!route.isEmpty()) return cleanRoute(route);
            if (cognitiveState.isEmpty() || "PENDING_MODEL".equalsIgnoreCase(cognitiveState)
                    || "ROUTED".equalsIgnoreCase(cognitiveState)) return "BRAIN · THINKING";
            if (cognitiveVersion.toLowerCase().startsWith("legacy")) return "LEGACY FALLBACK";
            return "CORTEX · " + pretty(cognitiveState);
        }

        public String decisionLabel() {
            String d = disposition.trim().toUpperCase();
            return d.isEmpty() ? pretty(cognitiveState) : d;
        }

        public String provenanceLine() {
            StringBuilder s = new StringBuilder("RELAY");
            String brain = brainLabel();
            if (!brain.isEmpty()) s.append(" · ").append(brain);
            String decision = decisionLabel();
            if (!decision.isEmpty()) s.append(" · ").append(decision);
            return s.toString();
        }

        private static String cleanRoute(String route) {
            return pretty(route.replace("cognitive_", ""));
        }
    }

    public static ArrayList<Item> latest(VaultDb db, int limit) {
        ArrayList<Item> out = new ArrayList<>();
        if (db == null || limit <= 0) return out;
        CognitiveStore.ensure(db);
        Cursor c = null;
        try {
            String sql = "SELECT r.id,r.thread_id,r.source,r.title,r.body,r.disposition," +
                    "r.cognitive_state,r.cognitive_version,r.final_reason,r.occurred_at," +
                    "r.importance,r.confidence,r.metadata_json " +
                    "FROM raw_signals r WHERE lower(COALESCE(r.kind,''))='notification' " +
                    "AND (COALESCE(r.metadata_json,'') LIKE '%\"source_connector\":\"second_brain\"%' " +
                    "OR COALESCE(r.metadata_json,'') LIKE '%\"capture_mode\":\"relay_local_bus\"%') " +
                    "ORDER BY r.occurred_at DESC,r.id DESC LIMIT ?";
            c = db.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(Math.min(50, limit))});
            while (c.moveToNext()) {
                long signalId = c.getLong(0);
                Model model = latestAuthorityRun(db, signalId);
                out.add(new Item(signalId, c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getLong(9),
                        c.getInt(10), c.getDouble(11), model.id, model.provider, model.model,
                        model.route, model.state, model.confidence));
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public static Item bySignalId(VaultDb db, long signalId) {
        if (db == null || signalId <= 0) return null;
        CognitiveStore.ensure(db);
        Cursor c = null;
        try {
            c = db.getReadableDatabase().rawQuery(
                    "SELECT id,thread_id,source,title,body,disposition,cognitive_state,cognitive_version," +
                    "final_reason,occurred_at,importance,confidence,metadata_json FROM raw_signals WHERE id=? LIMIT 1",
                    new String[]{String.valueOf(signalId)});
            if (!c.moveToFirst()) return null;
            String metadata = nz(c.getString(12));
            if (!isRelayMetadata(metadata)) return null;
            Model model = latestAuthorityRun(db, signalId);
            return new Item(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                    c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getLong(9),
                    c.getInt(10), c.getDouble(11), model.id, model.provider, model.model,
                    model.route, model.state, model.confidence);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    public static String connectorEventId(VaultDb db, long signalId) {
        if (db == null || signalId <= 0) return "";
        Cursor c = null;
        try {
            c = db.getReadableDatabase().rawQuery("SELECT metadata_json FROM raw_signals WHERE id=? LIMIT 1",
                    new String[]{String.valueOf(signalId)});
            if (!c.moveToFirst()) return "";
            JSONObject o = new JSONObject(nz(c.getString(0)));
            String id = o.optString("connector_event_id", "");
            if (!id.isEmpty()) return id;
            JSONObject relay = o.optJSONObject("relay_connector_enrichment");
            return relay == null ? "" : relay.optString("connector_event_id", "");
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (c != null) c.close();
        }
    }

    private static boolean isRelayMetadata(String metadata) {
        return metadata.contains("\"source_connector\":\"second_brain\"")
                || metadata.contains("\"capture_mode\":\"relay_local_bus\"");
    }

    private static Model latestAuthorityRun(VaultDb db, long signalId) {
        Cursor c = null;
        try {
            c = db.getReadableDatabase().rawQuery(
                    "SELECT id,provider,model,route,state,confidence FROM model_runs " +
                    "WHERE role='cognitive_authority' AND CAST(json_extract(output_json,'$.signal_id') AS INTEGER)=? " +
                    "ORDER BY id DESC LIMIT 1", new String[]{String.valueOf(signalId)});
            if (c.moveToFirst()) return new Model(c.getLong(0), c.getString(1), c.getString(2),
                    c.getString(3), c.getString(4), c.getDouble(5));
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return Model.EMPTY;
    }

    private static final class Model {
        static final Model EMPTY = new Model(0, "", "", "", "", 0);
        final long id;
        final String provider, model, route, state;
        final double confidence;
        Model(long id, String provider, String model, String route, String state, double confidence) {
            this.id = id;
            this.provider = nz(provider);
            this.model = nz(model);
            this.route = nz(route);
            this.state = nz(state);
            this.confidence = confidence;
        }
    }

    private static String pretty(String value) {
        String x = nz(value).replace('_', ' ').trim().toLowerCase();
        if (x.isEmpty()) return "";
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
