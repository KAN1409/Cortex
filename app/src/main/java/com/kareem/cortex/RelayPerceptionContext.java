package com.kareem.cortex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Cortex-side adapter for richer Relay perception payloads.
 *
 * Relay is allowed to describe what Android observed: episodes, conversations, deltas,
 * entity candidates, quality/provenance and interaction outcomes. It is never allowed to
 * decide personal relevance or priority here. This class deliberately converts only factual
 * evidence into a small bounded object that the Cortex brain can reason over.
 */
public final class RelayPerceptionContext {
    private static final int MAX_COMPACT_CHARS = 520;
    private RelayPerceptionContext() {}

    public static String compact(String rawMetadata) {
        JSONObject meta = object(rawMetadata);
        if (meta == null) return "";
        JSONObject semantic = firstObject(
                meta.optJSONObject("relay_semantic_v3"),
                meta.optJSONObject("relay_semantic_v2"),
                nested(meta, "relay_connector_enrichment", "relay_semantic_v3"),
                nested(meta, "relay_connector_enrichment", "relay_semantic_v2")
        );
        if (semantic == null) return "";

        JSONObject out = new JSONObject();
        try {
            copyString(semantic, out, "semantic_type", "type", "object_type", "signal_type");
            copyString(semantic, out, "episode_id", "episode_id");
            copyString(semantic, out, "conversation_id", "conversation_id", "conversation_instance");
            copyString(semantic, out, "logical_signal_id", "logical_signal_id");
            copyString(semantic, out, "actor", "actor", "sender", "person", "participant");

            JSONObject conversation = firstObject(
                    semantic.optJSONObject("conversation"),
                    semantic.optJSONObject("conversation_state")
            );
            if (conversation != null) {
                JSONObject c = new JSONObject();
                copyString(conversation, c, "id", "conversation_id", "id");
                copyString(conversation, c, "state", "state", "event_type", "message_type");
                copyString(conversation, c, "latest", "latest", "latest_text", "latest_message");
                copyNumber(conversation, c, "new_count", "new_count", "message_count", "new_messages");
                copyBoolean(conversation, c, "opened", "opened", "user_opened");
                copyBoolean(conversation, c, "reply_observed", "reply_observed", "user_replied");
                if (c.length() > 0) out.put("conversation", c);
            }

            JSONObject change = firstObject(
                    semantic.optJSONObject("change"),
                    semantic.optJSONObject("delta"),
                    semantic.optJSONObject("state_transition")
            );
            if (change != null) {
                JSONObject d = new JSONObject();
                copyString(change, d, "type", "change_type", "type");
                copyScalar(change, d, "from", "from", "previous", "old_value");
                copyScalar(change, d, "to", "to", "current", "new_value");
                copyArray(change, d, "changed_fields", "changed_fields");
                if (d.length() > 0) out.put("change", d);
            }

            JSONObject episode = semantic.optJSONObject("episode");
            if (episode != null) {
                JSONObject e = new JSONObject();
                copyString(episode, e, "id", "episode_id", "id");
                copyNumber(episode, e, "duration_ms", "duration_ms", "duration");
                copyArray(episode, e, "apps", "apps");
                copyArray(episode, e, "shared_entities", "shared_entities", "entities");
                if (e.length() > 0) out.put("episode", e);
            }

            Object quality = firstValue(semantic,
                    "evidence_quality", "quality", "quality_score", "confidence");
            if (quality != null && quality != JSONObject.NULL) out.put("evidence_quality", boundedValue(quality));

            JSONArray entities = firstArray(semantic,
                    "entity_candidates", "entities", "identity_candidates");
            if (entities != null && entities.length() > 0) out.put("entity_candidates", boundedArray(entities, 3));

            JSONArray relations = firstArray(semantic,
                    "relationships", "relations", "evidence_relations");
            if (relations != null && relations.length() > 0) out.put("relationships", boundedArray(relations, 3));

            Object outcome = firstValue(semantic,
                    "interaction_outcome", "outcome", "interaction");
            if (outcome != null && outcome != JSONObject.NULL) out.put("interaction_outcome", boundedValue(outcome));

            JSONArray fieldQuality = firstArray(semantic,
                    "field_quality", "field_confidence", "provenance");
            if (fieldQuality != null && fieldQuality.length() > 0) out.put("field_quality", boundedArray(fieldQuality, 4));
        } catch (Throwable ignored) {}

        if (out.length() == 0) return "";
        String compact = out.toString();
        return compact.length() <= MAX_COMPACT_CHARS ? compact : compact.substring(0, MAX_COMPACT_CHARS);
    }

    public static String actor(String rawMetadata) {
        JSONObject meta = object(rawMetadata);
        if (meta == null) return "";
        JSONObject semantic = firstObject(
                meta.optJSONObject("relay_semantic_v3"),
                meta.optJSONObject("relay_semantic_v2"),
                nested(meta, "relay_connector_enrichment", "relay_semantic_v3"),
                nested(meta, "relay_connector_enrichment", "relay_semantic_v2")
        );
        if (semantic == null) return "";
        return firstString(semantic, "actor", "sender", "person", "participant");
    }

    private static JSONObject object(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return new JSONObject(raw); } catch (Throwable ignored) { return null; }
    }

    private static JSONObject nested(JSONObject root, String parent, String child) {
        if (root == null) return null;
        JSONObject p = root.optJSONObject(parent);
        return p == null ? null : p.optJSONObject(child);
    }

    private static JSONObject firstObject(JSONObject... values) {
        if (values == null) return null;
        for (JSONObject value : values) if (value != null) return value;
        return null;
    }

    private static JSONArray firstArray(JSONObject source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) {
            JSONArray value = source.optJSONArray(key);
            if (value != null) return value;
        }
        return null;
    }

    private static Object firstValue(JSONObject source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) if (source.has(key) && !source.isNull(key)) return source.opt(key);
        return null;
    }

    private static String firstString(JSONObject source, String... keys) {
        Object value = firstValue(source, keys);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }

    private static void copyString(JSONObject from, JSONObject to, String target, String... sourceKeys) throws Exception {
        String value = firstString(from, sourceKeys);
        if (!value.isEmpty()) to.put(target, clip(value, 120));
    }

    private static void copyNumber(JSONObject from, JSONObject to, String target, String... sourceKeys) throws Exception {
        Object value = firstValue(from, sourceKeys);
        if (value instanceof Number) to.put(target, value);
    }

    private static void copyBoolean(JSONObject from, JSONObject to, String target, String... sourceKeys) throws Exception {
        Object value = firstValue(from, sourceKeys);
        if (value instanceof Boolean) to.put(target, value);
    }

    private static void copyScalar(JSONObject from, JSONObject to, String target, String... sourceKeys) throws Exception {
        Object value = firstValue(from, sourceKeys);
        if (value == null || value == JSONObject.NULL) return;
        if (value instanceof Number || value instanceof Boolean) to.put(target, value);
        else to.put(target, clip(String.valueOf(value), 120));
    }

    private static void copyArray(JSONObject from, JSONObject to, String target, String... sourceKeys) throws Exception {
        JSONArray value = firstArray(from, sourceKeys);
        if (value != null && value.length() > 0) to.put(target, boundedArray(value, 4));
    }

    private static JSONArray boundedArray(JSONArray input, int limit) {
        JSONArray out = new JSONArray();
        if (input == null) return out;
        for (int i = 0; i < Math.min(limit, input.length()); i++) out.put(boundedValue(input.opt(i)));
        return out;
    }

    private static Object boundedValue(Object value) {
        if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
        if (value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof JSONObject) {
            JSONObject src = (JSONObject) value, out = new JSONObject();
            Iterator<String> keys = src.keys();
            int copied = 0;
            while (keys.hasNext() && copied < 5) {
                String key = keys.next();
                try { out.put(key, boundedValue(src.opt(key))); copied++; } catch (Throwable ignored) {}
            }
            return out;
        }
        if (value instanceof JSONArray) return boundedArray((JSONArray) value, 4);
        return clip(String.valueOf(value), 120);
    }

    private static String clip(String value, int max) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
