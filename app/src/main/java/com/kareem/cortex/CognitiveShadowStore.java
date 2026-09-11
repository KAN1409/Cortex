package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persisted, read-only-to-production shadow evaluation for the v70 cognitive attention path.
 *
 * Shadow evaluation may advance the additive v70 commitment ledger, but it never writes
 * ue_attention_items, ue_projection_decisions, derived_items, situations, or semantic events,
 * so enabling cognitive evaluation cannot change production Now.
 *
 * Persisted situations are reconstructed through CognitiveWorldState before evaluation. v70.19
 * replaces synthetic per-event commitments with the persistent lifecycle/deadline ledger.
 */
public final class CognitiveShadowStore {
    public static final String VERSION = "cognitive_shadow_store_004";

    private CognitiveShadowStore() {}

    public static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_cognitive_shadow_runs(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "engine_version TEXT NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "completed_at INTEGER NOT NULL DEFAULT 0," +
                "candidate_count INTEGER NOT NULL DEFAULT 0," +
                "fresh_candidate_count INTEGER NOT NULL DEFAULT 0," +
                "stale_candidate_count INTEGER NOT NULL DEFAULT 0," +
                "legacy_now_count INTEGER NOT NULL DEFAULT 0," +
                "cognitive_now_count INTEGER NOT NULL DEFAULT 0," +
                "cognitive_eligible_count INTEGER NOT NULL DEFAULT 0," +
                "topk_excluded_count INTEGER NOT NULL DEFAULT 0," +
                "agreements INTEGER NOT NULL DEFAULT 0," +
                "recoveries INTEGER NOT NULL DEFAULT 0," +
                "noise_suppressions INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ue_cognitive_shadow_decisions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "situation_id INTEGER NOT NULL," +
                "legacy_surface INTEGER NOT NULL," +
                "cognitive_eligible INTEGER NOT NULL DEFAULT 0," +
                "cognitive_surface INTEGER NOT NULL," +
                "cognitive_rank INTEGER NOT NULL DEFAULT 0," +
                "cognitive_score REAL NOT NULL DEFAULT 0," +
                "candidate_last_seen_at INTEGER NOT NULL DEFAULT 0," +
                "cognitive_freshness REAL NOT NULL DEFAULT 1," +
                "delta TEXT NOT NULL," +
                "legacy_reason TEXT," +
                "cognitive_reason TEXT," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(run_id,situation_id))");
        addColumn(db,"ue_cognitive_shadow_runs","cognitive_eligible_count","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_runs","topk_excluded_count","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_runs","fresh_candidate_count","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_runs","stale_candidate_count","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_decisions","cognitive_eligible","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_decisions","candidate_last_seen_at","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"ue_cognitive_shadow_decisions","cognitive_freshness","REAL NOT NULL DEFAULT 1");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ue_shadow_run ON ue_cognitive_shadow_decisions(run_id,cognitive_rank,situation_id)");
    }

    /** Runs one bounded snapshot against persisted v69 state without changing any production projection. */
    public static long run(SQLiteDatabase db, int maxNowItems) {
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        CommitmentLifecycleStore.ensure(db);
        ensure(db);
        // Make an explicit shadow run self-contained even when invoked outside StatefulMeaningWorker.
        CommitmentLifecycleStore.rebuild(db,240);
        long started = System.currentTimeMillis();
        List<AttentionDecisionEngine.Candidate> candidates = loadCandidates(db, started);
        List<AttentionShadowComparator.LegacyDecision> legacy = loadLegacy(db, candidates);
        AttentionShadowComparator.Report report = AttentionShadowComparator.compare(
                legacy, candidates, Math.max(1, maxNowItems));

        int fresh = 0, stale = 0;
        Map<Long, AttentionDecisionEngine.Candidate> candidateBySituation = new HashMap<>();
        for (AttentionDecisionEngine.Candidate candidate : candidates) {
            candidateBySituation.put(candidate.situationId, candidate);
            if (candidate.freshness >= 0.80) fresh++;
            if (candidate.freshness < 0.50) stale++;
        }

        ContentValues run = new ContentValues();
        run.put("engine_version", CognitiveWorldState.VERSION + "+" + AttentionDecisionEngine.VERSION + "+" + CommitmentLifecycleStore.VERSION + "+" + VERSION);
        run.put("started_at", started);
        run.put("candidate_count", candidates.size());
        run.put("fresh_candidate_count", fresh);
        run.put("stale_candidate_count", stale);
        run.put("legacy_now_count", countLegacySurface(legacy));
        run.put("cognitive_now_count", report.cognitiveSelectedCount);
        run.put("cognitive_eligible_count", report.cognitiveEligibleCount);
        run.put("topk_excluded_count", report.topKExcludedCount);
        run.put("agreements", report.agreements);
        run.put("recoveries", report.cognitiveRecoveries);
        run.put("noise_suppressions", report.cognitiveNoiseSuppressions);
        long runId = db.insertOrThrow("ue_cognitive_shadow_runs", null, run);

        long now = System.currentTimeMillis();
        for (AttentionShadowComparator.Comparison comparison : report.comparisons) {
            AttentionDecisionEngine.Candidate candidate = candidateBySituation.get(comparison.situationId);
            ContentValues v = new ContentValues();
            v.put("run_id", runId);
            v.put("situation_id", comparison.situationId);
            v.put("legacy_surface", comparison.legacySurface ? 1 : 0);
            v.put("cognitive_eligible", comparison.cognitiveEligible ? 1 : 0);
            v.put("cognitive_surface", comparison.cognitiveSurface ? 1 : 0);
            v.put("cognitive_rank", comparison.cognitiveRank);
            v.put("cognitive_score", comparison.cognitiveScore);
            v.put("candidate_last_seen_at", candidate == null ? 0L : candidate.lastSeenAt);
            v.put("cognitive_freshness", candidate == null ? 1.0 : candidate.freshness);
            v.put("delta", comparison.delta.name());
            v.put("legacy_reason", comparison.legacyReason);
            v.put("cognitive_reason", comparison.cognitiveReason);
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
     * Rebuilds the bounded persisted world from correlated semantic evidence, attaches only OPEN
     * persisted commitments, then asks the world-state layer for attention candidates.
     */
    static List<AttentionDecisionEngine.Candidate> loadCandidates(SQLiteDatabase db, long nowAt) {
        CognitiveWorldState world = new CognitiveWorldState();
        String sql = "SELECT ss.situation_id,ss.correlation_key,COALESCE(u.state,'open'),COALESCE(u.priority,0)," +
                "e.id,COALESCE(e.semantic_type,''),COALESCE(e.intent,''),COALESCE(e.subject,'')," +
                "COALESCE(e.summary,''),COALESCE(e.confidence,0),COALESCE(e.occurred_at,ss.updated_at)," +
                "COALESCE((SELECT st.kind FROM ue_situation_transitions st " +
                " WHERE st.situation_id=ss.situation_id AND st.semantic_event_id=e.id " +
                " ORDER BY st.id DESC LIMIT 1),'SUPPORTING_EVIDENCE')," +
                "COALESCE(r.source_type,''),COALESCE(r.source_key,''),COALESCE(r.event_type,''),COALESCE(r.technical_type,'') " +
                "FROM ue_situation_state_v2 ss " +
                "JOIN ue_situation_members_v2 m ON m.situation_id=ss.situation_id " +
                "JOIN ue_semantic_events e ON e.id=m.semantic_event_id " +
                "JOIN ue_raw_observations r ON r.id=e.raw_observation_id " +
                "LEFT JOIN ue_situations u ON u.id=ss.situation_id " +
                "WHERE ss.situation_id IN (SELECT situation_id FROM ue_situation_state_v2 ORDER BY updated_at DESC LIMIT 240) " +
                "AND e.superseded_by=0 AND e.semantic_state='complete' " +
                "ORDER BY ss.situation_id ASC,e.occurred_at ASC,m.id ASC";
        Cursor c = db.rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                long situationId = c.getLong(0);
                String linkKey = n(c.getString(1));
                if (linkKey.isEmpty()) linkKey = "situation|" + situationId;
                String state = n(c.getString(2));
                int priority = c.getInt(3);
                String type = n(c.getString(5));
                String intent = n(c.getString(6));
                String subject = n(c.getString(7));
                String summary = n(c.getString(8));
                double confidence = clamp01(c.getDouble(9));
                long occurredAt = c.getLong(10);
                String transition = n(c.getString(11));
                String sourceType = n(c.getString(12));
                String sourceKey = n(c.getString(13));
                String eventType = n(c.getString(14));
                String technicalType = n(c.getString(15));
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

                CortexProvenanceGate.Result provenance = CortexProvenanceGate.evaluate(
                        sourceType, sourceKey, eventType, technicalType,
                        subject, summary, type, intent);
                if (!provenance.attentionEligible) continue;

                double urgency = clamp01(priority / 100.0);
                double actionability = request ? .88 : (security ? .85 : (commitment ? .68 : (missed ? .28 : .12)));
                double relevance = security ? .85 : (commitment ? .78 : (request ? .75 : .45));
                double risk = security ? .92 : (severe ? .80 : .08);
                double novelty = material ? .78 : .25;
                if (severe) urgency = Math.max(urgency, .78);
                if (security) urgency = Math.max(urgency, .82);
                if (request) urgency = Math.max(urgency, .62);

                ActionSpecificityGate.Result specificity = ActionSpecificityGate.evaluate(
                        type, intent, subject, summary, request, commitment);
                if (provenance.authority != CortexProvenanceGate.Authority.USER_AUTHORED
                        && (request || "ACTION".equalsIgnoreCase(type) || intent.toLowerCase(Locale.ROOT).contains("action"))
                        && !specificity.eligible) {
                    continue;
                }
                actionability = clamp01(actionability * Math.max(.35, specificity.specificity));

                world.observe(new CognitiveWorldState.Observation(
                        situationId, linkKey, type, state, subject, summary, confidence,
                        urgency, actionability, relevance, risk, novelty,
                        0L, occurredAt, material, request, severe));
            }
        } finally {
            c.close();
        }

        for (CommitmentLifecycleStore.Record commitment : CommitmentLifecycleStore.loadOpen(db,240)) {
            world.putCommitment(new CognitiveWorldState.Commitment(
                    "commitment|" + commitment.id,
                    commitment.linkKey,
                    commitment.summary,
                    commitment.deadlineAt,
                    true,
                    commitment.confidence));
        }
        return world.attentionCandidates(nowAt);
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
        Cursor c = db.rawQuery("SELECT candidate_count,fresh_candidate_count,stale_candidate_count,legacy_now_count,cognitive_eligible_count,cognitive_now_count,topk_excluded_count,agreements,recoveries,noise_suppressions FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1", null);
        if (!c.moveToFirst()) { c.close(); return "no shadow run"; }
        String s = "candidates=" + c.getInt(0) +
                " fresh=" + c.getInt(1) +
                " stale=" + c.getInt(2) +
                " legacyNow=" + c.getInt(3) +
                " cognitiveEligible=" + c.getInt(4) +
                " cognitiveNow=" + c.getInt(5) +
                " topKExcluded=" + c.getInt(6) +
                " agree=" + c.getInt(7) +
                " recover=" + c.getInt(8) +
                " suppress=" + c.getInt(9);
        c.close();
        return s;
    }

    private static int countLegacySurface(List<AttentionShadowComparator.LegacyDecision> xs) {
        int n = 0; for (AttentionShadowComparator.LegacyDecision x : xs) if (x.surfaceNow) n++; return n;
    }

    private static void prune(SQLiteDatabase db, int keepRuns) {
        db.execSQL("DELETE FROM ue_cognitive_shadow_decisions WHERE run_id IN (SELECT id FROM ue_cognitive_shadow_runs ORDER BY id DESC LIMIT -1 OFFSET " + Math.max(1, keepRuns) + ")");
        db.execSQL("DELETE FROM ue_cognitive_shadow_runs WHERE id IN (SELECT id FROM ue_cognitive_shadow_runs ORDER BY id DESC LIMIT -1 OFFSET " + Math.max(1, keepRuns) + ")");
    }

    private static void addColumn(SQLiteDatabase db,String table,String column,String definition) {
        if (hasColumn(db,table,column)) return;
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private static boolean hasColumn(SQLiteDatabase db,String table,String column) {
        Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null);
        try {
            while(c.moveToNext()) {
                int i=c.getColumnIndex("name");
                if(i>=0 && column.equals(c.getString(i))) return true;
            }
            return false;
        } finally { c.close(); }
    }

    private static boolean contains(String value, String... needles) {
        String x = norm(value); for (String n : needles) if (x.contains(norm(n))) return true; return false;
    }
    private static String norm(String s) { return n(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private static String n(String s) { return s == null ? "" : s.trim(); }
    private static double clamp01(double x) { if (Double.isNaN(x)) return 0.0; return Math.max(0.0, Math.min(1.0, x)); }
}
