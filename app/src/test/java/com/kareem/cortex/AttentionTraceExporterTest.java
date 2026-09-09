package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AttentionTraceExporterTest {
    private SQLiteDatabase db;

    @Before public void before() {
        db = SQLiteDatabase.create(null);
        UniversalEventStore.ensure(db);
        StatefulMeaningStore.ensure(db);
        CognitiveShadowStore.ensure(db);
    }

    @After public void after() { if (db != null) db.close(); }

    @Test public void exportLinksCaptureSituationDecisionAndActualNowWithoutMutation() throws Exception {
        long raw = UniversalEventStore.appendRaw(db, "notification", "com.google.android.gms", "security-1",
                "posted", "system", "security", "Google security alert",
                "Saved passwords were found online", new JSONObject(), 1000);
        long stream = UniversalEventStore.upsertStream(db, "notification", "security-stream",
                "active", "h1", "Google security alert", "Saved passwords were found online",
                "system", "security", 1000, true, new JSONObject());
        long event = UniversalEventStore.insertSemantic(db, raw, stream, 1, "security_alert", "action",
                "Google security alert", "Saved passwords were found online; change passwords", .96,
                "complete", true, "test", "trace test", 1000);
        long situation = StatefulMeaningStore.correlate(db, event, 0, "google",
                "security_alert", "Google security alert",
                "Saved passwords were found online; change passwords", .96, 1000);
        StatefulMeaningPolicy.ProjectionDecision decision = StatefulMeaningPolicy.projection(
                "security_alert", "action", .96, "OPENED", 1,
                "Google security alert", "Saved passwords were found online; change passwords");
        StatefulMeaningStore.recordProjectionDecision(db, event, situation, decision);
        UniversalEventStore.attention(db, event, situation, "ACTION", "Google security alert",
                "Change passwords", 95, .96, "google", "critical actionable security alert");
        CognitiveShadowStore.run(db, 5);

        long rawBefore = count("SELECT COUNT(*) FROM ue_raw_observations");
        long semanticBefore = count("SELECT COUNT(*) FROM ue_semantic_events");
        long situationBefore = count("SELECT COUNT(*) FROM ue_situations");
        long projectionBefore = count("SELECT COUNT(*) FROM ue_projection_decisions");
        long attentionBefore = count("SELECT COUNT(*) FROM ue_attention_items");

        JSONObject root = new JSONObject(AttentionTraceExporter.export(db));
        assertEquals(AttentionTraceExporter.VERSION, root.getString("format"));
        assertTrue(root.has("app_version_name"));
        assertTrue(root.has("schema_revision"));

        JSONArray capture = root.getJSONArray("capture");
        assertEquals(1, capture.length());
        assertEquals(raw, capture.getJSONObject(0).getLong("capture_id"));
        assertEquals(event, capture.getJSONObject(0).getLong("semantic_event_id"));

        JSONArray traces = root.getJSONArray("trace_items");
        assertEquals(1, traces.length());
        JSONObject trace = traces.getJSONObject(0);
        assertEquals(situation, trace.getLong("situation_id"));
        assertEquals(raw, trace.getJSONArray("evidence").getJSONObject(0).getLong("capture_id"));
        assertEquals(event, trace.getJSONArray("evidence").getJSONObject(0).getLong("semantic_event_id"));
        assertTrue(trace.getJSONObject("production_now_decision").getBoolean("evaluated"));
        assertTrue(trace.getJSONObject("production_now_decision").getBoolean("surface_now"));
        assertTrue(trace.getJSONObject("actual_now").getBoolean("present"));
        assertEquals(1, trace.getJSONObject("actual_now").getInt("rank"));
        assertTrue(trace.getJSONObject("shadow_attention").getBoolean("available"));
        assertEquals("CONSISTENT", trace.getString("projection_consistency"));

        JSONObject summary = root.getJSONObject("summary");
        assertEquals(1, summary.getLong("capture_count"));
        assertEquals(1, summary.getLong("linked_semantic_count"));
        assertEquals(0, summary.getLong("unlinked_complete_semantic_count"));
        assertEquals(0, summary.getLong("eligible_not_materialized_count"));
        assertEquals(0, summary.getLong("materialized_without_current_eligibility_count"));

        assertEquals(rawBefore, count("SELECT COUNT(*) FROM ue_raw_observations"));
        assertEquals(semanticBefore, count("SELECT COUNT(*) FROM ue_semantic_events"));
        assertEquals(situationBefore, count("SELECT COUNT(*) FROM ue_situations"));
        assertEquals(projectionBefore, count("SELECT COUNT(*) FROM ue_projection_decisions"));
        assertEquals(attentionBefore, count("SELECT COUNT(*) FROM ue_attention_items"));
    }

    @Test public void exportMakesUnlinkedUnderstandingVisibleWithoutCallingItWrong() throws Exception {
        long raw = UniversalEventStore.appendRaw(db, "notification", "weather", "weather-1",
                "posted", "system", "weather", "New Cairo", "30 degrees and clear",
                new JSONObject(), 2000);
        long stream = UniversalEventStore.upsertStream(db, "notification", "weather-stream",
                "active", "h2", "New Cairo", "30 degrees and clear",
                "system", "weather", 2000, true, new JSONObject());
        long event = UniversalEventStore.insertSemantic(db, raw, stream, 1, "weather_event", "weather",
                "New Cairo", "30 degrees and clear", .95, "complete", true,
                "test", "trace test", 2000);

        JSONObject root = new JSONObject(AttentionTraceExporter.export(db));
        JSONArray unlinked = root.getJSONArray("unlinked_semantic_events");
        assertEquals(1, unlinked.length());
        assertEquals(event, unlinked.getJSONObject(0).getLong("semantic_event_id"));
        assertFalse(root.getJSONObject("summary").getString("note").toLowerCase().contains("false negative"));
    }

    private long count(String sql) {
        Cursor c = db.rawQuery(sql, null);
        try { return c.moveToFirst() ? c.getLong(0) : 0; }
        finally { c.close(); }
    }
}
