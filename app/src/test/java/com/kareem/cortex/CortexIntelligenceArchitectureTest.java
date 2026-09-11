package com.kareem.cortex;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CortexIntelligenceArchitectureTest {
    private Context context;
    private VaultDb db;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("cortex.db");
        CortexPersonalPolicy.clear(context);
        db = new VaultDb(context);
        CognitiveStore.ensure(db);
        UniversalEventStore.ensure(db.getWritableDatabase());
        StatefulMeaningStore.ensure(db.getWritableDatabase());
        NexusEngine.ensure(db.getWritableDatabase());
    }

    @After public void tearDown() {
        if (db != null) try { db.close(); } catch (Throwable ignored) {}
        CortexPersonalPolicy.clear(context);
        context.deleteDatabase("cortex.db");
    }

    @Test public void teacherCanTuneFinalJudgmentButCannotOwnTruthOrExecutionLayers() {
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.EVIDENCE));
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.KNOWLEDGE));
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.WORLD_STATE));
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.TRIAGE));
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.REASONING));
        assertTrue(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.JUDGMENT));
        assertFalse(CortexIntelligenceArchitecture.canTeacherWrite(CortexIntelligenceArchitecture.Layer.ACTION));
    }

    @Test public void interruptionCostDefersOrdinaryValueButNotDueCommitment() {
        long now = System.currentTimeMillis();
        AttentionDecisionEngine.Candidate ordinary = new AttentionDecisionEngine.Candidate(
                1, "message", "open", "Routine update", "Useful but not urgent", .90,
                .45, .55, .80, .20, .50, 0, now, now, 1, 1,
                true, false, false, false, false);
        CortexAttentionJudge.Judgment quiet = CortexAttentionJudge.evaluate(
                context, ordinary, new CortexAttentionJudge.RuntimeContext(.50, 1.0));
        assertFalse(quiet.surfaceNow);

        AttentionDecisionEngine.Candidate due = new AttentionDecisionEngine.Candidate(
                2, "commitment", "open", "Owner approval", "Reply required", .95,
                .72, .82, .92, .30, .60, now + 60L * 60L * 1000L, now, now, 2, 3,
                true, true, true, true, false);
        CortexAttentionJudge.Judgment important = CortexAttentionJudge.evaluate(
                context, due, new CortexAttentionJudge.RuntimeContext(.80, 1.0));
        assertTrue(important.surfaceNow);
    }

    @Test public void teacherContextPrefersCanonicalTriageAndPolicyCannotRewriteEvidence() throws Exception {
        SQLiteDatabase s = db.getWritableDatabase();
        long rawId = UniversalEventStore.appendRaw(s, "notification", "whatsapp", "n1",
                "posted", "", "", "Original title", "Original body", new JSONObject(), System.currentTimeMillis());
        assertTrue(rawId > 0);
        String before = rawHash(s, rawId);

        long situationId = UniversalEventStore.upsertSituation(s, "project|negma", "project",
                "Negma approval", "Waiting for owner reply", "open", 88, .94,
                System.currentTimeMillis(), new JSONObject());
        ContentValues a = new ContentValues();
        a.put("semantic_event_id", 999L);
        a.put("situation_id", situationId);
        a.put("kind", "ACTION");
        a.put("title", "Negma approval");
        a.put("body", "Owner reply needs review");
        a.put("state", "open");
        a.put("priority", 91);
        a.put("confidence", .94);
        a.put("source_key", "whatsapp:negma");
        a.put("reason", "material situation update");
        a.put("created_at", System.currentTimeMillis());
        a.put("updated_at", System.currentTimeMillis());
        assertTrue(s.insert("ue_attention_items", null, a) > 0);

        CognitiveStore.addDerived(db, "ACTION", "Legacy noise", "compatibility only", "open",
                .95, 99, "legacy-test", "{}");

        JSONObject pack = CortexChatGptBridge.buildContextPack(context, db);
        assertEquals(2, pack.getInt("schemaVersion"));
        assertEquals("TRIAGE", pack.getJSONArray("priorityCandidates").getJSONObject(0).getString("layer"));
        assertTrue(pack.getJSONArray("priorityCandidates").getJSONObject(0).getBoolean("canonical"));
        assertFalse(pack.getJSONObject("system").getBoolean("compatibilityFallback"));
        assertEquals("JUDGMENT", pack.getJSONObject("system").getString("teacherPolicyLayer"));
        assertEquals(CortexIntelligenceArchitecture.VERSION,
                pack.getJSONObject("architecture").getString("version"));

        JSONObject teacherPolicy = new JSONObject();
        teacherPolicy.put("version", "teacher-test");
        teacherPolicy.put("attentionThreshold", .63);
        teacherPolicy.put("maxNowItems", 4);
        CortexPersonalPolicy.save(context, teacherPolicy);
        assertEquals(before, rawHash(s, rawId));
    }

    @Test public void teacherPolicyCannotActAsSecondUiJudge() {
        PrimeBriefStore.Item item = new PrimeBriefStore.Item(
                1L, "ACTION", "teacher-targeted", "compatibility item", "legacy",
                "open", .95, 5, 0, 0, System.currentTimeMillis());
        JSONObject teacherPolicy = new JSONObject();
        try {
            teacherPolicy.put("version", "teacher-boundary-test");
            teacherPolicy.put("attentionThreshold", .99);
            teacherPolicy.put("suppressPhrases", new org.json.JSONArray().put("teacher-targeted"));
            teacherPolicy.put("boosts", new org.json.JSONArray().put(
                    new JSONObject().put("match", "teacher-targeted").put("weight", 1.0)));
        } catch (Exception e) {
            fail(e.getMessage());
        }
        CortexPersonalPolicy.save(context, teacherPolicy);

        assertFalse(CortexPersonalPolicy.suppress(context, item));
        assertFalse(CortexPersonalPolicy.belowThreshold(context, item));
        assertEquals(.05, CortexPersonalPolicy.score(context, item), .0001);
    }

    @Test public void selectiveReasoningGateEscalatesOnlyWhenJustified() {
        long now = System.currentTimeMillis();
        AttentionDecisionEngine.Candidate easy = new AttentionDecisionEngine.Candidate(
                11, "message", "open", "FYI", "ordinary update", .96,
                .20, .20, .30, .10, .20, 0, now, now, 1, 1,
                true, false, false, false, false);
        assertFalse(CortexReasoningGate.assess(easy, false, false).escalate);

        AttentionDecisionEngine.Candidate ambiguousHighValue = new AttentionDecisionEngine.Candidate(
                12, "commitment", "open", "Approval", "ambiguous but important", .74,
                .80, .85, .90, .30, .60, now + 2L*60L*60L*1000L, now, now, 1, 2,
                true, true, true, true, false);
        assertTrue(CortexReasoningGate.assess(ambiguousHighValue, false, false).escalate);
    }

    private static String rawHash(SQLiteDatabase s, long id) {
        Cursor c = s.rawQuery("SELECT immutable_hash FROM ue_raw_observations WHERE id=?",
                new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? c.getString(0) : ""; }
        finally { c.close(); }
    }
}
