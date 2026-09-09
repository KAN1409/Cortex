package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persisted, read-only-to-production shadow evaluation for the v70 cognitive attention path.
 *
 * This class may create/write only its own ue_cognitive_shadow_* tables. It never writes
 * ue_attention_items, ue_projection_decisions, derived_items, situations, or semantic events,
 * so enabling shadow evaluation cannot change Now.
 */
public final class CognitiveShadowStore {
    public static final String VERSION = "cognitive_shadow_store_001";

    private CognitiveShadowStore() {}

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_cognitive_shadow_runs(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "engine_version TEXT NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "completed_at INTEGER NOT NULL DEFAULT 0," +
                "candidate_count INTEGER NOT NULL DEFAULT 0," +
                "legacy_now_count INTEGER NOT NULL DEFAULT 0," +
                "cognitive_now_count INTEGER NOT NULL DEFAULT 0," +
                "agreements INTEGER NOT NULL DEFAULT 0," +
                "recoveries INTEGER NOT NULL DEFAULT 0," +
                "noise_suppressions INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_cognitive_shadow_decisions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "situation_id INTEGER NOT NULL," +
                "legacy_surface INTEGER NOT NULL," +
                "cognitive_surface INTEGER NOT NULL," +
                "cognitive_rank INTEGER NOT NULL DEFAULT 0," +
                "cognitive_score REAL NOT NULL DEFAULT 0," +
                "delta TEXT NOT NULL," +
                "legacy_reason TEXT," +
                "cognitive_reason TEXT," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(run_id,situation_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_shadow_run ON ue_cognitive_shadow_decisions(run_id,cognitive_rank,situation_id)");
    }

    /** Runs one bounded snapshot against persisted v69 state without changing any production projection. */
    public static long run(SQLiteDatabase db, int maxNowItems) {
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        ensure(db);
        long started = System.currentTimeMillis();
        List<AttentionDecisionEngine.Candidate> candidates = loadCandidates(db, started);
        List<AttentionShadowComparator.LegacyDecision> legacy = loadLegacy(db, candidates);
        AttentionShadowComparator.Report report = AttentionShadowComparator.compare(
                legacy, candidates, Math.max(1, maxNowItems));

        ContentValues run = new ContentValues();
        run.put("engine_version", AttentionDecisionEngine.VERSION + "+" + VERSION);
        run.put("started_at", started);
        run.put("candidate_count", candidates.size());
        run.put("legacy_now_count", countLegacySurface(legacy));
        run.put("cognitive_now_count", countCognitiveSurface(report));
        run.put("agreements", report.agreements);
        run.put("recoveries", report.cognitiveRecoveries);
        run.put("noise_suppressions", report.cognitiveNoiseSuppressions);
        long runId = db.insertOrThrow("ue_cognitive_shadow_runs", null, run);

        long now = System.currentTimeMillis();
        for (AttentionShadowComparator.Comparison c : report.comparisons) {
            ContentValues v = new ContentValues();
            v.put("run_id", runId);
            v.put("situation_id", c.situationId);
            v.put("legacy_surface", c.legacySurface ? 1 : 0);
            v.put("cognitive_surface", c.cognitiveSurface ? 1 : 0);
            v.put("cognitive_rank", c.cognitiveRank);
            v.put("cognitive_score", c.cognitiveScore);
            v.put("delta", c.delta.name());
            v.put("legacy_reason", c.legacyReason);
            v.put("cognitive_reason", c.cognitiveReason);
            v.put("created_at", now);
            db.insertOrThrow("ue_cognitive_shadow_decisions", null, v);
        }
        ContentValues done = new ContentValues();
        done.put("completed_at", now);
        db.update("ue_cognitive_shadow_runs", done, "id=?", new String[]{String.valueOf(runId)});
        prune(db, 40);
        return runId;
    }

    /**
     * Bootstrap candidate construction from facts already present in v69.
     * These are conservative semantic dimensions, not final personalized cognition. The
     * purpose is to make old-vs-new differences observable on real captures while the
     * world-model enrichment sources are added incrementally.
     */
    static List<AttentionDecisionEngine.Candidate> loadCandidates(SQLiteDatabase db, long nowAt) {
        ArrayList<AttentionDecisionEngine.Candidate> out = new ArrayList<>();
        String sql = "SELECT s.situation_id,s.member_count,s.last_transition_kind," +
                "COALESCE(u.state,'open'),COALESCE(u.priority,0)," +
                "COALESCE(e.semantic_type,''),COALESCE(e.intent,'')," +
                "COALESCE(e.subject,''),COALESCE(e.summary,''),COALESCE(e.confidence,0)," +
                "COALESCE(e.occurred_at,s.updated_at) " +
                "FROM ue_situation_state_v2 s " +
                "LEFT JOIN ue_situations u ON u.id=s.situation_id " +
                "LEFT JOIN ue_situation_members_v2 m ON m.id=(" +
                " SELECT m2.id FROM ue_situation_members_v2 m2 WHERE m2.situation_id=s.situation_id ORDER BY m2.id DESC LIMIT 1) " +
                "LEFT JOIN ue_semantic_events e ON e.id=m.semantic_event_id " +
                "ORDER BY s.updated_at DESC LIMIT 240";
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            long situationId = c.getLong(0);
            int evidence = Math.max(1, c.getInt(1));
            String transition = n(c.getString(2));
            String state = n(c.getString(3));
            int priority = c.getInt(4);
            String type = n(c.getString(5));
            String intent = n(c.getString(6));
            String subject = n(c.getString(7));
            String summary = n(c.getString(8));
            double confidence = clamp01(c.getDouble(9));
            long occurredAt = c.getLong(10);
            String all = norm(type + " " + intent + " " + subject + " " + summary);

            boolean request = contains(type, "request", "action_required", "required_response") ||
                    contains(intent, "request", "command", "send", "reply", "respond", "confirm", "submit", "pay");
            boolean commitment = contains(type, "commitment", "waiting", "follow_up", "pending_response") ||
                    contains(intent, "waiting", "awaiting", "follow_up", "pending");
            boolean security = StatefulMeaningPolicy.isSecurity(all);
            boolean weather = StatefulMeaningPolicy.isWeather(type, all);
            boolean severe = weather && StatefulMeaningPolicy.isSevereWeather(all);
            boolean call = type.toLowerCase(Locale.ROOT).contains("call");
            boolean missed = call && contains(all, "missed", "فائت", "لم يتم الرد");
            boolean material = StatefulMeaningPolicy.materialTransition(transition);

            double urgency = clamp01(priority / 100.0);
            double actionability = request ? .88 : (security ? .85 : (commitment ? .68 : (missed ? .28 : .12)));
            double relevance = security ? .85 : (commitment ? .78 : (request ? .75 : .45));
            double risk = security ? .92 : (severe ? .80 : .08);
            double novelty = material ? .78 : .25;
            if (severe) urgency = Math.max(urgency, .78);
            if (security) urgency = Math.max(urgency, .82);
            if (request) urgency = Math.max(urgency, .62);

            boolean unresolved = !isResolved(state);
            out.add(new AttentionDecisionEngine.Candidate(
                    situationId, type, state, subject, summary, confidence,
                    urgency, actionability, relevance, risk, novelty,
                    0L, nowAt, evidence, evidence, unresolved,
                    commitment, material, request, severe));
        }
        c.close();
        return out;
    }

    static List<AttentionShadowComparator.LegacyDecision> loadLegacy(
            SQLiteDatabase db, List<AttentionDecisionEngine.Candidate> candidates) {
        ArrayList<AttentionShadowComparator.LegacyDecision> out = new ArrayList<>();
        for (AttentionDecisionEngine.Candidate candidate : candidates) {
            boolean surface = false;
            String reason = "no eligible v69 Now projection";
            Cursor c = db.rawQuery(
                    "SELECT pd.reason FROM ue_projection_decisions pd " +
                            "WHERE pd.situation_id=? AND pd.projection='NOW' AND pd.eligible=1 " +
                            "AND pd.policy_version=? ORDER BY pd.id DESC LIMIT 1",
                    new String[]{String.valueOf(candidate.situationId), StatefulMeaningPolicy.VERSION});
            if (c.moveToFirst()) {
                surface = true;
                reason = n(c.getString(0));
            }
            c.close();
            if (!surface) {
                c = db.rawQuery(
                        "SELECT reason FROM ue_attention_items WHERE situation_id=? AND state='open' ORDER BY priority DESC,id DESC LIMIT 1",
                        new String[]{String.valueOf(candidate.situationId)});
                if (c.moveToFirst()) {
                    surface = true;
                    reason = "open legacy attention: " + n(c.getString(0));
                }
                c.close();
            }
            out.add(new AttentionShadowComparator.LegacyDecision(candidate.situationId, surface, reason));
        }
        return out;
    }

    public static String latestSummary(SQLiteDatabase db) {
        ensure(db);
        Cursor c = db.rawQuery("SELECT candidate_count,legacy_now_count,cognitive_now_count,agreements,recoveries,noise_suppressions FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1", null);
        if (!c.moveToFirst()) { c.close(); return "no shadow run"; }
        String s = "candidates=" + c.getInt(0) +
                " legacyNow=" + c.getInt(1) +
                " cognitiveNow=" + c.getInt(2) +
                " agree=" + c.getInt(3) +
                " recover=" + c.getInt(4) +
                " suppress=" + c.getInt(5);
        c.close();
        return s;
    }

    private static int countLegacySurface(List<AttentionShadowComparator.LegacyDecision> xs) {
        int n = 0; for (AttentionShadowComparator.LegacyDecision x : xs) if (x.surfaceNow) n++; return n;
    }
    private static int countCognitiveSurface(AttentionShadowComparator.Report r) {
        int n = 0; for (AttentionShadowComparator.Comparison x : r.comparisons) if (x.cognitiveSurface) n++; return n;
    }
    private static void prune(SQLiteDatabase db, int keepRuns) {
        db.execSQL("DELETE FROM ue_cognitive_shadow_decisions WHERE run_id IN (SELECT id FROM ue_cognitive_shadow_runs ORDER BY id DESC LIMIT -1 OFFSET " + Math.max(1, keepRuns) + ")");
        db.execSQL("DELETE FROM ue_cognitive_shadow_runs WHERE id IN (SELECT id FROM ue_cognitive_shadow_runs ORDER BY id DESC LIMIT -1 OFFSET " + Math.max(1, keepRuns) + ")");
    }
    private static boolean isResolved(String s) {
        String x = norm(s); return x.equals("resolved") || x.equals("closed") || x.equals("completed") || x.equals("dismissed");
    }
    private static boolean contains(String value, String... needles) {
        String x = norm(value); for (String n : needles) if (x.contains(norm(n))) return true; return false;
    }
    private static String norm(String s) { return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private static String n(String s) { return s == null ? "" : s.trim(); }
    private static double clamp01(double x) { if (Double.isNaN(x)) return 0.0; return Math.max(0.0, Math.min(1.0, x)); }
}
