package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CognitiveShadowStoreTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private SQLiteDatabase db;

    @Before public void before() {
        db = SQLiteDatabase.create(null);
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        CommitmentLifecycleStore.ensure(db);
    }

    @After public void after() { if (db != null) db.close(); }

    @Test public void shadowRunPersistsComparisonWithoutMutatingProductionNowTables() {
        long at = System.currentTimeMillis() - 1000L;
        long event = semantic("security_alert", "security", "Google security alert",
                "Compromised password; change it now", .96, at);
        long situation = StatefulMeaningStore.correlate(db, event, 0, "google",
                "security_alert", "Google security alert",
                "Compromised password; change it now", .96, at);
        StatefulMeaningPolicy.ProjectionDecision legacy = StatefulMeaningPolicy.projection(
                "security_alert", "security", .96, "OPENED", 1,
                "Google security alert", "Compromised password; change it now");
        StatefulMeaningStore.recordProjectionDecision(db, event, situation, legacy);

        long projectionCount = count("SELECT COUNT(*) FROM ue_projection_decisions");
        long attentionCount = count("SELECT COUNT(*) FROM ue_attention_items");
        long semanticCount = count("SELECT COUNT(*) FROM ue_semantic_events");

        long runId = CognitiveShadowStore.run(db, 5);

        assertTrue(runId > 0);
        assertEquals(1, count("SELECT COUNT(*) FROM ue_cognitive_shadow_runs"));
        assertEquals(1, count("SELECT COUNT(*) FROM ue_cognitive_shadow_decisions"));
        assertEquals(1, count("SELECT cognitive_eligible FROM ue_cognitive_shadow_decisions LIMIT 1"));
        assertEquals(projectionCount, count("SELECT COUNT(*) FROM ue_projection_decisions"));
        assertEquals(attentionCount, count("SELECT COUNT(*) FROM ue_attention_items"));
        assertEquals(semanticCount, count("SELECT COUNT(*) FROM ue_semantic_events"));
        assertTrue(CognitiveShadowStore.latestSummary(db).contains("candidates=1"));
        assertTrue(CognitiveShadowStore.latestSummary(db).contains("fresh=1"));
        assertTrue(CognitiveShadowStore.latestSummary(db).contains("stale=0"));
        assertTrue(CognitiveShadowStore.latestSummary(db).contains("cognitiveEligible=1"));
    }

    @Test public void persistedEvidenceFlowsThroughWorldStateBeforeAttentionEvaluation() {
        long first = semantic("commitment", "waiting", "Quotation",
                "Send quotation today", .92, 1000);
        long situation = StatefulMeaningStore.correlate(db, first, 0, "mail",
                "commitment", "Quotation", "Send quotation today", .92, 1000);

        long second = semantic("commitment", "waiting", "Quotation",
                "Quotation is still pending", .94, 2000);
        long sameSituation = StatefulMeaningStore.correlate(db, second, 0, "mail",
                "commitment", "Quotation", "Quotation is still pending", .94, 2000);

        assertEquals(situation, sameSituation);
        CommitmentLifecycleStore.rebuild(db,20);
        List<AttentionDecisionEngine.Candidate> candidates = CognitiveShadowStore.loadCandidates(db, 3000);
        assertEquals(1, candidates.size());
        AttentionDecisionEngine.Candidate candidate = candidates.get(0);
        assertEquals(situation, candidate.situationId);
        assertEquals(2, candidate.evidenceCount);
        assertEquals(2, candidate.repeatedCount);
        assertTrue(candidate.linkedOpenCommitment);
        assertEquals("Quotation is still pending", candidate.summary);
        assertEquals(2000L, candidate.lastSeenAt);
        assertEquals(1.0, candidate.freshness, .0001);
        assertTrue(candidate.deadlineAt>0);
    }

    @Test public void persistedCommitmentDeadlineReachesAttentionCandidate() {
        long at=System.currentTimeMillis()-1000L;
        long event=semantic("commitment","waiting","Quotation","Send quotation tomorrow at 10:00",.95,at);
        long situation=StatefulMeaningStore.correlate(db,event,0,"mail","commitment","Quotation","Send quotation tomorrow at 10:00",.95,at);
        CognitiveShadowStore.run(db,5);

        CommitmentLifecycleStore.Record record=CommitmentLifecycleStore.findForSituation(db,situation);
        assertNotNull(record);
        assertTrue(record.isOpen());
        assertTrue(record.deadlineAt>at);

        List<AttentionDecisionEngine.Candidate> candidates=CognitiveShadowStore.loadCandidates(db,at+1000L);
        assertEquals(1,candidates.size());
        assertTrue(candidates.get(0).linkedOpenCommitment);
        assertEquals(record.deadlineAt,candidates.get(0).deadlineAt);
    }

    @Test public void fulfilledPersistedCommitmentStopsBoostingWorldCandidate() {
        long at=System.currentTimeMillis()-1000L;
        long event=semantic("commitment","waiting","PO","Need to send PO tomorrow",.95,at);
        long situation=StatefulMeaningStore.correlate(db,event,0,"mail","commitment","PO","Need to send PO tomorrow",.95,at);
        CommitmentLifecycleStore.rebuild(db,20);
        CommitmentLifecycleStore.Record open=CommitmentLifecycleStore.findForSituation(db,situation);
        assertNotNull(open);
        assertTrue(open.isOpen());

        CommitmentLifecycleStore.observeSemantic(db,event+1000,situation,open.linkKey,
                "commitment_completed","completed","PO","PO sent",.99,at+2000L,"open");
        CommitmentLifecycleStore.Record terminal=CommitmentLifecycleStore.findForSituation(db,situation);
        assertEquals(CommitmentLifecycleStore.FULFILLED,terminal.state);

        List<AttentionDecisionEngine.Candidate> candidates=CognitiveShadowStore.loadCandidates(db,at+3000L);
        assertEquals(1,candidates.size());
        assertFalse(candidates.get(0).linkedOpenCommitment);
        assertEquals(0L,candidates.get(0).deadlineAt);
    }

    @Test public void realPersistedRoutineWeatherIsSuppressedByCognitiveShadow() {
        long at = System.currentTimeMillis() - 1000L;
        long event = semantic("weather_event", "weather", "New Cairo", "24° and clear", .97, at);
        long situation = StatefulMeaningStore.correlate(db, event, 0, "weather",
                "weather_event", "New Cairo", "24° and clear", .97, at);
        UniversalEventStore.attention(db, event, situation, "ACTION", "New Cairo",
                "24° and clear", 80, .97, "weather", "legacy noisy attention");

        CognitiveShadowStore.run(db, 5);

        Cursor c = db.rawQuery("SELECT legacy_surface,cognitive_eligible,cognitive_surface,delta,cognitive_reason,cognitive_freshness FROM ue_cognitive_shadow_decisions LIMIT 1", null);
        assertTrue(c.moveToFirst());
        assertEquals(1, c.getInt(0));
        assertEquals(0, c.getInt(1));
        assertEquals(0, c.getInt(2));
        assertEquals(AttentionShadowComparator.Delta.NEW_SUPPRESSES_LEGACY_NOISE.name(), c.getString(3));
        assertTrue(c.getString(4).contains("routine weather"));
        assertTrue(c.getDouble(5) >= .80);
        c.close();
    }

    @Test public void stalePersistedRequestIsMeasuredAndSuppressedByTemporalAttention() {
        long at = System.currentTimeMillis() - 10L * DAY;
        long event = semantic("action_request", "request", "Old request",
                "Please send the old file", .96, at);
        long situation = StatefulMeaningStore.correlate(db, event, 0, "mail",
                "action_request", "Old request", "Please send the old file", .96, at);
        StatefulMeaningPolicy.ProjectionDecision legacy = StatefulMeaningPolicy.projection(
                "action_request", "request", .96, "OPENED", 1,
                "Old request", "Please send the old file");
        StatefulMeaningStore.recordProjectionDecision(db, event, situation, legacy);

        CognitiveShadowStore.run(db, 5);

        Cursor c = db.rawQuery("SELECT cognitive_eligible,cognitive_surface,cognitive_freshness,candidate_last_seen_at,cognitive_reason FROM ue_cognitive_shadow_decisions WHERE situation_id=? LIMIT 1", new String[]{String.valueOf(situation)});
        assertTrue(c.moveToFirst());
        // It is now a durable open commitment, so staleness alone must not erase a still-open obligation.
        assertEquals(.25, c.getDouble(2), .0001);
        assertEquals(at, c.getLong(3));
        c.close();
        assertTrue(CognitiveShadowStore.latestSummary(db).contains("stale=1"));
    }

    private long semantic(String type, String intent, String subject, String summary, double confidence, long at) {
        long raw = UniversalEventStore.appendRaw(db, "notification", "test", "obs-" + at + "-" + type,
                "posted", "message", "conversation_notification", subject, summary, new JSONObject(), at);
        long stream = UniversalEventStore.upsertStream(db, "notification", "stream-" + at + "-" + type,
                "active", "h-" + at, subject, summary, "message", "conversation_notification", at, true, new JSONObject());
        return UniversalEventStore.insertSemantic(db, raw, stream, 1, type, intent, subject, summary,
                confidence, "complete", true, "test", "shadow test", at);
    }

    private long count(String sql) {
        Cursor c = db.rawQuery(sql, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }
}
