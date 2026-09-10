package com.kareem.cortex;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Read-only diagnostic surface for the v70 cognitive shadow path.
 *
 * The screen can trigger a shadow evaluation, but CognitiveShadowStore writes only its own
 * ue_cognitive_shadow_* tables. Production Now/Brief/Brain projection tables remain untouched.
 */
public final class CognitiveShadowActivity extends Activity {
    private VaultDb vault;
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CortexUi.applyWindow(this);
        vault = new VaultDb(getApplicationContext());
        build();
        render();
    }

    @Override protected void onDestroy() {
        if (vault != null) {
            try { vault.close(); } catch (Throwable ignored) {}
            vault = null;
        }
        super.onDestroy();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);
        root.setPadding(dp(18), dp(14), dp(18), dp(18));

        TextView title = CortexUi.plain(this, "Cognitive Shadow", 27, CortexUi.TEXT);
        CortexUi.medium(title);
        root.addView(title);

        TextView sub = CortexUi.text(this,
                "Real-device comparison only. The cognitive brain is observing the same situations as Now but cannot change Now yet.",
                11, CortexUi.MUTED);
        sub.setPadding(0, dp(5), 0, dp(12));
        root.addView(sub);

        Button run = new Button(this);
        run.setText("RUN SHADOW NOW");
        run.setOnClickListener(v -> runShadow());
        root.addView(run, new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        CortexUi.fitSystemBars(this, root);
    }

    private void runShadow() {
        if (vault == null) return;
        final TextView busy = CortexUi.text(this, "Running bounded shadow evaluation…", 12, CortexUi.MUTED);
        content.removeAllViews();
        content.addView(busy);
        new Thread(() -> {
            String error = null;
            try {
                CognitiveShadowStore.run(vault.getWritableDatabase(), 5);
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + safe(t.getMessage());
            }
            final String e = error;
            runOnUiThread(() -> {
                if (e != null) showError(e);
                else render();
            });
        }, "CortexCognitiveShadowManual").start();
    }

    private void render() {
        if (content == null || vault == null) return;
        content.removeAllViews();
        SQLiteDatabase db = vault.getReadableDatabase();
        CognitiveShadowStore.ensure(db);

        Cursor run = null;
        try {
            run = db.rawQuery(
                    "SELECT id,engine_version,started_at,completed_at,candidate_count,legacy_now_count,cognitive_now_count,agreements,recoveries,noise_suppressions " +
                    "FROM ue_cognitive_shadow_runs WHERE completed_at>0 ORDER BY id DESC LIMIT 1", null);
            if (!run.moveToFirst()) {
                empty("No real-device shadow run yet. Tap RUN SHADOW NOW or let Cortex observe new events.");
                return;
            }

            long runId = run.getLong(0);
            int candidates = run.getInt(4);
            int legacyNow = run.getInt(5);
            int cognitiveNow = run.getInt(6);
            int agreements = run.getInt(7);
            int recoveries = run.getInt(8);
            int suppressions = run.getInt(9);

            card("LATEST RUN",
                    "Candidates: " + candidates +
                    "\nLegacy Now: " + legacyNow +
                    "\nCognitive Now: " + cognitiveNow +
                    "\nAgreements: " + agreements +
                    "\nNew brain surfaces missed by legacy: " + recoveries +
                    "\nNew brain suppresses legacy noise: " + suppressions +
                    "\nEngine: " + safe(run.getString(1)));

            section("DECISION DIFFERENCES");
            renderDifferences(db, runId);

            section("SAFETY CONTRACT");
            card("SHADOW ISOLATION",
                    "This screen does not write production attention or projection tables. " +
                    "A difference here is evidence for evaluation, not a live change to Now.");
        } finally {
            if (run != null) run.close();
        }
    }

    private void renderDifferences(SQLiteDatabase db, long runId) {
        Cursor c = null;
        int count = 0;
        try {
            c = db.rawQuery(
                    "SELECT d.situation_id,d.delta,d.legacy_surface,d.cognitive_surface,d.cognitive_rank,d.cognitive_score," +
                    "COALESCE(d.legacy_reason,''),COALESCE(d.cognitive_reason,'')," +
                    "COALESCE((SELECT e.subject FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id " +
                    "WHERE m.situation_id=d.situation_id ORDER BY m.id DESC LIMIT 1),'')," +
                    "COALESCE((SELECT e.summary FROM ue_situation_members_v2 m JOIN ue_semantic_events e ON e.id=m.semantic_event_id " +
                    "WHERE m.situation_id=d.situation_id ORDER BY m.id DESC LIMIT 1),'') " +
                    "FROM ue_cognitive_shadow_decisions d WHERE d.run_id=? AND d.delta<>'AGREEMENT' " +
                    "ORDER BY CASE d.delta WHEN 'NEW_SURFACES_LEGACY_MISSED' THEN 0 WHEN 'NEW_SUPPRESSES_LEGACY_NOISE' THEN 1 ELSE 2 END," +
                    "d.cognitive_rank ASC,d.cognitive_score DESC,d.situation_id ASC LIMIT 40",
                    new String[]{String.valueOf(runId)});
            while (c.moveToNext()) {
                count++;
                String delta = safe(c.getString(1));
                String subject = safe(c.getString(8));
                String summary = safe(c.getString(9));
                String heading = subject.isEmpty() ? "Situation " + c.getLong(0) : subject;
                String body = delta +
                        "\nLegacy: " + (c.getInt(2) == 1 ? "NOW" : "not Now") +
                        "\nCognitive: " + (c.getInt(3) == 1 ? "NOW" : "not Now") +
                        "\nRank: " + c.getInt(4) + "   Score: " + String.format(Locale.US, "%.3f", c.getDouble(5)) +
                        (summary.isEmpty() ? "" : "\n" + summary) +
                        "\nLegacy reason: " + safe(c.getString(6)) +
                        "\nCognitive reason: " + safe(c.getString(7));
                card(heading, body);
            }
        } finally {
            if (c != null) c.close();
        }
        if (count == 0) empty("No disagreement in the latest run.");
    }

    private void section(String text) {
        TextView h = CortexUi.plain(this, text, 11, CortexUi.MUTED);
        CortexUi.medium(h);
        h.setPadding(0, dp(16), 0, dp(7));
        content.addView(h);
    }

    private void card(String title, String body) {
        LinearLayout card = CortexUi.card(this, 18);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        TextView h = CortexUi.text(this, title, 14, CortexUi.TEXT);
        CortexUi.medium(h);
        h.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        card.addView(h);
        TextView b = CortexUi.text(this, body, 11, CortexUi.MUTED);
        b.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        b.setPadding(0, dp(6), 0, 0);
        card.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(8));
        content.addView(card, p);
    }

    private void empty(String text) {
        TextView e = CortexUi.text(this, text, 12, CortexUi.MUTED);
        e.setGravity(Gravity.START);
        e.setPadding(0, dp(8), 0, dp(12));
        content.addView(e);
    }

    private void showError(String error) {
        content.removeAllViews();
        card("SHADOW RUN FAILED", error);
    }

    private int dp(int value) { return CortexUi.dp(this, value); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
