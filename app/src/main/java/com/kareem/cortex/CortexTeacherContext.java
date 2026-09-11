package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds the only context shape the ChatGPT teacher should normally see.
 *
 * It intentionally excludes bulk raw observations and full OCR/history. The teacher receives
 * current situations, the personal model, attention candidates, uncertainty and recent outcomes.
 * Evidence can later be fetched by reference for a specific disputed case instead of flooding the
 * model context on every synchronization.
 */
public final class CortexTeacherContext {
    public static final int SCHEMA_VERSION = 2;
    private CortexTeacherContext() {}

    public static JSONObject build(Context context, VaultDb db) throws Exception {
        Context app = context.getApplicationContext();
        SQLiteDatabase s = db.getWritableDatabase();
        UniversalEventStore.ensure(s);
        StatefulMeaningStore.ensure(s);
        NexusEngine.ensure(s);

        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("architecture", CortexIntelligenceArchitecture.teacherContract());
        root.put("deviceId", CortexChatGptBridgeConfig.deviceId(app));
        root.put("generatedAt", System.currentTimeMillis());

        JSONArray situations = activeSituations(s);
        JSONArray candidates = canonicalAttentionCandidates(s);
        boolean compatibilityFallback = candidates.length() == 0;
        if (compatibilityFallback) candidates = legacyCandidateFallback(s);

        root.put("activeSituations", situations);
        // Compatibility alias retained for the current MCP bridge while clients move to v2.
        root.put("situations", situations);
        root.put("personalModel", personalModel(s));
        root.put("priorityCandidates", candidates);
        root.put("uncertainCases", uncertainCases(candidates));
        root.put("recentOutcomes", recentOutcomes(s));
        root.put("system", system(app, s, compatibilityFallback));
        return root;
    }

    private static JSONArray activeSituations(SQLiteDatabase s) {
        JSONArray out = new JSONArray();
        Cursor c = null;
        try {
            c = s.rawQuery(
                    "SELECT u.id,COALESCE(u.situation_key,''),COALESCE(u.kind,''),COALESCE(u.title,'')," +
                    "COALESCE(u.summary,''),COALESCE(u.state,''),COALESCE(u.priority,0),COALESCE(u.confidence,0)," +
                    "COALESCE(u.opened_at,0),COALESCE(u.last_changed_at,0)," +
                    "(SELECT COUNT(*) FROM ue_situation_events e WHERE e.situation_id=u.id) " +
                    "FROM ue_situations u WHERE LOWER(COALESCE(u.state,'')) NOT IN ('resolved','closed','completed','dismissed') " +
                    "ORDER BY u.priority DESC,u.last_changed_at DESC LIMIT 40", null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("situationId", c.getLong(0));
                o.put("key", clip(c.getString(1), 180));
                o.put("kind", c.getString(2));
                o.put("title", clip(c.getString(3), 220));
                o.put("summary", clip(c.getString(4), 700));
                o.put("state", c.getString(5));
                o.put("priority", c.getInt(6));
                o.put("confidence", c.getDouble(7));
                o.put("openedAt", c.getLong(8));
                o.put("changedAt", c.getLong(9));
                o.put("evidenceCount", c.getInt(10));
                out.put(o);
            }
        } catch (Throwable ignored) {
        } finally { close(c); }
        return out;
    }

    private static JSONArray canonicalAttentionCandidates(SQLiteDatabase s) {
        JSONArray out = new JSONArray();
        Cursor c = null;
        try {
            c = s.rawQuery(
                    "SELECT a.id,COALESCE(a.situation_id,0),COALESCE(a.kind,''),COALESCE(a.title,'')," +
                    "COALESCE(a.body,''),COALESCE(a.priority,0),COALESCE(a.confidence,0),COALESCE(a.source_key,'')," +
                    "COALESCE(a.reason,''),COALESCE(a.updated_at,0),COALESCE(u.state,''),COALESCE(u.priority,0) " +
                    "FROM ue_attention_items a LEFT JOIN ue_situations u ON u.id=a.situation_id " +
                    "WHERE a.state='open' ORDER BY a.priority DESC,a.updated_at DESC LIMIT 60", null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("candidateId", "attention:" + c.getLong(0));
                o.put("attentionId", c.getLong(0));
                o.put("situationId", c.getLong(1));
                o.put("kind", c.getString(2));
                o.put("title", clip(c.getString(3), 220));
                o.put("summary", clip(c.getString(4), 650));
                o.put("priority", c.getInt(5));
                o.put("confidence", c.getDouble(6));
                o.put("source", c.getString(7));
                o.put("whyCandidate", clip(c.getString(8), 300));
                o.put("updatedAt", c.getLong(9));
                o.put("situationState", c.getString(10));
                o.put("situationPriority", c.getInt(11));
                o.put("canonical", true);
                o.put("layer", "ATTENTION");
                out.put(o);
            }
        } catch (Throwable ignored) {
        } finally { close(c); }
        return out;
    }

    /** Temporary migration path only. It is explicitly labeled so the teacher cannot mistake it for truth. */
    private static JSONArray legacyCandidateFallback(SQLiteDatabase s) {
        JSONArray out = new JSONArray();
        Cursor c = null;
        try {
            if (!table(s, "derived_items")) return out;
            c = s.rawQuery(
                    "SELECT id,COALESCE(kind,''),COALESCE(title,''),COALESCE(body,''),COALESCE(state,'')," +
                    "COALESCE(confidence,0),COALESCE(importance,0),COALESCE(source_key,''),COALESCE(updated_at,0) " +
                    "FROM derived_items WHERE state IN ('open','pending') ORDER BY importance DESC,updated_at DESC LIMIT 40", null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("candidateId", "legacy:" + c.getLong(0));
                o.put("id", c.getLong(0));
                o.put("kind", c.getString(1));
                o.put("title", clip(c.getString(2), 220));
                o.put("summary", clip(c.getString(3), 650));
                o.put("state", c.getString(4));
                o.put("confidence", c.getDouble(5));
                o.put("priority", c.getInt(6));
                // Kept for current bridge/test compatibility.
                o.put("importance", c.getInt(6));
                o.put("source", c.getString(7));
                o.put("updatedAt", c.getLong(8));
                o.put("canonical", false);
                o.put("layer", "LEGACY_COMPATIBILITY");
                o.put("whyCandidate", "Compatibility fallback only; judge against canonical situation state before teaching policy.");
                out.put(o);
            }
        } catch (Throwable ignored) {
        } finally { close(c); }
        return out;
    }

    private static JSONObject personalModel(SQLiteDatabase s) {
        JSONObject root = new JSONObject();
        JSONArray interests = new JSONArray();
        Cursor c = null;
        try {
            c = s.rawQuery("SELECT id,label,affinity,momentum,confidence,saturation,evidence_count,updated_at " +
                    "FROM nx_interests ORDER BY (affinity*confidence+momentum*.35) DESC LIMIT 16", null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getString(0));
                o.put("label", c.getString(1));
                o.put("affinity", c.getDouble(2));
                o.put("momentum", c.getDouble(3));
                o.put("confidence", c.getDouble(4));
                o.put("saturation", c.getDouble(5));
                o.put("evidenceCount", c.getInt(6));
                o.put("updatedAt", c.getLong(7));
                interests.put(o);
            }
        } catch (Throwable ignored) {
        } finally { close(c); }
        try {
            root.put("interests", interests);
            root.put("owner", "NEXUS");
            root.put("rule", "Personal model informs relevance; it never directly surfaces or executes an item.");
        } catch (Exception ignored) {}
        return root;
    }

    private static JSONArray uncertainCases(JSONArray candidates) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < candidates.length() && out.length() < 18; i++) {
            JSONObject c = candidates.optJSONObject(i);
            if (c == null) continue;
            double confidence = c.optDouble("confidence", 0);
            int priority = c.optInt("priority", c.optInt("importance", 0));
            if (confidence >= .72 && confidence <= .88 && priority >= 45) out.put(c);
        }
        return out;
    }

    private static JSONArray recentOutcomes(SQLiteDatabase s) {
        JSONArray out = new JSONArray();
        Cursor c = null;
        try {
            if (!table(s, "feedback_events")) return out;
            c = s.rawQuery("SELECT COALESCE(target_type,''),COALESCE(target_id,0),COALESCE(event_type,'')," +
                    "COALESCE(value_json,''),COALESCE(policy_version,''),COALESCE(source_key,'')," +
                    "COALESCE(candidate_kind,''),COALESCE(created_at,0) FROM feedback_events " +
                    "ORDER BY created_at DESC LIMIT 80", null);
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("targetType", c.getString(0));
                o.put("targetId", c.getLong(1));
                o.put("event", c.getString(2));
                o.put("value", clip(c.getString(3), 300));
                o.put("policyVersion", c.getString(4));
                o.put("source", c.getString(5));
                o.put("candidateKind", c.getString(6));
                o.put("at", c.getLong(7));
                out.put(o);
            }
        } catch (Throwable ignored) {
        } finally { close(c); }
        return out;
    }

    private static JSONObject system(Context app, SQLiteDatabase s, boolean compatibilityFallback) {
        JSONObject o = new JSONObject();
        try {
            o.put("architectureVersion", CortexIntelligenceArchitecture.VERSION);
            o.put("attentionJudgeVersion", CortexAttentionJudge.VERSION);
            o.put("policyVersion", CortexPersonalPolicy.version(app));
            o.put("attentionThreshold", CortexPersonalPolicy.attentionThreshold(app));
            o.put("maxNowItems", CortexPersonalPolicy.maxNowItems(app));
            o.put("compatibilityFallback", compatibilityFallback);
            o.put("observed", count(s, "SELECT COUNT(*) FROM ue_raw_observations"));
            o.put("understood", count(s, "SELECT COUNT(*) FROM ue_semantic_events WHERE semantic_state='complete' AND superseded_by=0"));
            o.put("activeSituations", count(s, "SELECT COUNT(*) FROM ue_situations WHERE LOWER(COALESCE(state,'')) NOT IN ('resolved','closed','completed','dismissed')"));
            o.put("openAttention", count(s, "SELECT COUNT(*) FROM ue_attention_items WHERE state='open'"));
            o.put("instruction", "Teach bounded JUDGMENT policy from grounded state/outcomes. Request evidence by reference only when a case is ambiguous. Never classify, suppress, rank, or execute items outside CortexAttentionJudge.");
        } catch (Throwable ignored) {}
        return o;
    }

    private static long count(SQLiteDatabase s, String sql) {
        Cursor c = null;
        try { c = s.rawQuery(sql, null); return c.moveToFirst() ? c.getLong(0) : 0; }
        catch (Throwable ignored) { return 0; }
        finally { close(c); }
    }
    private static boolean table(SQLiteDatabase s, String name) {
        Cursor c = null;
        try { c = s.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", new String[]{name}); return c.moveToFirst(); }
        catch (Throwable ignored) { return false; }
        finally { close(c); }
    }
    private static void close(Cursor c) { if (c != null) try { c.close(); } catch (Throwable ignored) {} }
    private static String clip(String s, int n) {
        String x = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return x.length() <= n ? x : x.substring(0, n) + "…";
    }
}
