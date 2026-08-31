package com.kareem.cortex.prime;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kareem.cortex.prime.capture.vision.ImagePerceptionProcessor;
import com.kareem.cortex.prime.capture.voice.VoiceTranscriptionProcessor;
import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;
import com.kareem.cortex.prime.intelligence.IntelligenceSnapshot;
import com.kareem.cortex.prime.intelligence.LocalIntelligenceEngine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Cortex Prime home. Proposals remain grounded in immutable evidence. */
public final class NowActivity extends Activity {
    private final int bg = Color.rgb(12, 14, 18);
    private final int card = Color.rgb(24, 27, 33);
    private final int cardSoft = Color.rgb(31, 35, 42);
    private final int text = Color.rgb(244, 246, 248);
    private final int muted = Color.rgb(161, 169, 181);
    private final int accent = Color.rgb(124, 241, 191);
    private final int amber = Color.rgb(255, 190, 92);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        VoiceTranscriptionProcessor.recoverUntranscribed(this);
        ImagePerceptionProcessor.recoverUnprocessed(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(44));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<EvidenceRecord> evidence = loadEvidence(500);
        IntelligenceSnapshot snapshot = LocalIntelligenceEngine.analyze(evidence);

        addHeader();
        addCaptureStatus();
        addNow(snapshot);
        addPerception(evidence);
        addConversations(snapshot);
        addGrounding(snapshot, evidence.size());
        addDashboardButton();
        addFooter();
        setContentView(scroll);
    }

    private void addHeader() {
        content.addView(label("CORTEX PRIME", 12, accent, true));
        TextView now = label("Now", 38, text, true);
        now.setPadding(0, dp(6), 0, 0);
        content.addView(now);
        TextView subtitle = label("What changed, what may need you, and what Cortex can prove from your phone right now.", 15, muted, false);
        subtitle.setPadding(0, dp(8), 0, dp(22));
        content.addView(subtitle);
    }

    private void addCaptureStatus() {
        boolean relayEnabled = isNotificationAccessEnabled();
        LinearLayout box = cardContainer(card);
        LinearLayout row = horizontal();
        row.addView(label("●", 16, relayEnabled ? accent : amber, true));
        TextView title = label(relayEnabled ? "Live context capture" : "Notification capture is off", 16, text, true);
        title.setPadding(dp(10), 0, 0, 0);
        row.addView(title);
        box.addView(row);
        TextView perception = label("Voice transcription + image OCR recovery are active. Derived results stay linked to their immutable parent evidence.", 12, muted, false);
        perception.setPadding(0, dp(9), 0, 0);
        box.addView(perception);
        if (!relayEnabled) {
            Button enable = button("Enable notification capture", true);
            enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            box.addView(enable, topMargin(dp(12)));
        }
        content.addView(box, sectionMargin());
    }

    private void addNow(IntelligenceSnapshot snapshot) {
        sectionTitle("Needs attention");
        LinearLayout box = cardContainer(card);
        if (snapshot.attentionCandidates.isEmpty() && snapshot.taskCandidates.isEmpty() && snapshot.temporalHints.isEmpty()) {
            box.addView(label("Nothing grounded is asking for attention yet.", 16, text, true));
            TextView detail = label("Cortex stays quiet rather than promoting weak notification noise.", 13, muted, false);
            detail.setPadding(0, dp(6), 0, 0);
            box.addView(detail);
        } else {
            int shown = 0;
            for (IntelligenceSnapshot.SignalProposal proposal : snapshot.attentionCandidates) {
                if (shown++ >= 4) break;
                if (shown > 1) box.addView(divider());
                box.addView(signalRow("ATTENTION", proposal, amber));
            }
            for (IntelligenceSnapshot.SignalProposal proposal : snapshot.taskCandidates) {
                if (shown++ >= 5) break;
                if (shown > 1) box.addView(divider());
                box.addView(signalRow("TASK?", proposal, accent));
            }
            for (IntelligenceSnapshot.SignalProposal proposal : snapshot.temporalHints) {
                if (shown++ >= 6) break;
                if (shown > 1) box.addView(divider());
                box.addView(signalRow("TIME", proposal, accent));
            }
        }
        content.addView(box, sectionMargin());
    }

    private View signalRow(String kind, IntelligenceSnapshot.SignalProposal proposal, int kindColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout top = horizontal();
        top.addView(label(kind, 11, kindColor, true), weighted());
        TextView confidence = label(Math.round(proposal.confidence * 100) + "%", 11, muted, true);
        confidence.setGravity(Gravity.END);
        top.addView(confidence, weighted());
        row.addView(top);
        TextView body = label(proposal.label, 15, text, false);
        body.setPadding(0, dp(5), 0, 0);
        row.addView(body);
        TextView source = label("Evidence " + shortId(proposal.evidenceId), 11, muted, false);
        source.setPadding(0, dp(5), 0, 0);
        row.addView(source);
        return row;
    }

    private void addPerception(List<EvidenceRecord> evidence) {
        sectionTitle("Perception");
        LinearLayout box = cardContainer(card);
        int shown = 0;
        for (EvidenceRecord record : evidence) {
            if (!isDerivedPerception(record)) continue;
            if (shown > 0) box.addView(divider());
            box.addView(perceptionRow(record));
            shown++;
            if (shown >= 4) break;
        }
        if (shown == 0) {
            box.addView(label("No transcript or OCR result has been committed yet. New and recoverable captures are processed downstream without changing the raw evidence.", 13, muted, false));
        }
        content.addView(box, sectionMargin());
    }

    private boolean isDerivedPerception(EvidenceRecord record) {
        if (record == null) return false;
        if (record.source != EvidenceSource.TEXT && record.source != EvidenceSource.OCR) return false;
        return record.sourceRef != null && record.sourceRef.startsWith("derived-from:");
    }

    private View perceptionRow(EvidenceRecord record) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        String payload = record.rawPayloadJson == null ? "" : record.rawPayloadJson;
        boolean status = payload.contains("_STATUS_V1");
        String type = record.source == EvidenceSource.OCR ? "IMAGE OCR" : "VOICE ASR";
        String state = status ? "STATUS" : "DERIVED";
        LinearLayout top = horizontal();
        top.addView(label(type + "  •  " + state, 11, status ? amber : accent, true), weighted());
        TextView time = label(formatTime(record.capturedAtEpochMs), 10, muted, false);
        time.setGravity(Gravity.END);
        top.addView(time, weighted());
        row.addView(top);
        String body = record.rawText == null ? "" : record.rawText.trim();
        if (body.isEmpty()) body = "Derived result stored";
        if (body.length() > 220) body = body.substring(0, 220) + "…";
        TextView bodyView = label(body, 14, text, false);
        bodyView.setPadding(0, dp(5), 0, 0);
        row.addView(bodyView);
        String parent = record.sourceRef == null ? "" : record.sourceRef.replace("derived-from:", "");
        TextView provenance = label("Parent " + shortId(parent) + "  •  immutable provenance", 10, muted, false);
        provenance.setPadding(0, dp(5), 0, 0);
        row.addView(provenance);
        return row;
    }

    private void addConversations(IntelligenceSnapshot snapshot) {
        sectionTitle("Active conversations");
        LinearLayout box = cardContainer(card);
        int shown = Math.min(5, snapshot.threads.size());
        if (shown == 0) {
            box.addView(label("No grounded conversation threads yet.", 14, muted, false));
        } else {
            for (int i = 0; i < shown; i++) {
                if (i > 0) box.addView(divider());
                IntelligenceSnapshot.ThreadProposal thread = snapshot.threads.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(11), 0, dp(11));
                LinearLayout top = horizontal();
                top.addView(label(thread.label, 16, text, true), weighted());
                TextView count = label(thread.evidenceIds.size() + " ev", 11, muted, true);
                count.setGravity(Gravity.END);
                top.addView(count, weighted());
                row.addView(top);
                if (!thread.latestSnippet.isEmpty()) {
                    TextView snippet = label(thread.latestSnippet, 13, muted, false);
                    snippet.setPadding(0, dp(5), 0, 0);
                    row.addView(snippet);
                }
                TextView time = label(formatTime(thread.latestEpochMs), 11, muted, false);
                time.setPadding(0, dp(5), 0, 0);
                row.addView(time);
                box.addView(row);
            }
        }
        content.addView(box, sectionMargin());
    }

    private void addGrounding(IntelligenceSnapshot snapshot, int evidenceCount) {
        sectionTitle("Grounding");
        LinearLayout box = cardContainer(card);
        LinearLayout metrics = horizontal();
        metrics.addView(metric("EVIDENCE", String.valueOf(evidenceCount)), weighted());
        metrics.addView(metric("THREADS", String.valueOf(snapshot.threads.size())), weighted());
        metrics.addView(metric("PEOPLE", String.valueOf(snapshot.people.size())), weighted());
        metrics.addView(metric("SIGNALS", String.valueOf(snapshot.attentionCandidates.size() + snapshot.taskCandidates.size() + snapshot.temporalHints.size())), weighted());
        box.addView(metrics);
        TextView guard = label("Every item above points back to immutable evidence. Proposals are not facts and do not execute actions.", 12, accent, true);
        guard.setPadding(0, dp(12), 0, 0);
        box.addView(guard);
        content.addView(box, sectionMargin());
    }

    private void addDashboardButton() {
        Button evidence = button("Open evidence & capture dashboard", false);
        evidence.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        content.addView(evidence, sectionMargin());
    }

    private void addFooter() {
        TextView footer = label("CORTEX PRIME  •  " + appVersion() + "  •  GROUNDED PERCEPTION", 11, muted, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        content.addView(footer);
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private List<EvidenceRecord> loadEvidence(int limit) {
        try (EvidenceSqliteStore store = new EvidenceSqliteStore(this)) {
            return store.recent(limit);
        } catch (RuntimeException failure) {
            return new ArrayList<>();
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private LinearLayout metric(String name, String value) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.addView(label(value, 24, text, true));
        metric.addView(label(name, 9, muted, true));
        return metric;
    }

    private void sectionTitle(String title) {
        TextView view = label(title, 13, muted, true);
        view.setPadding(0, dp(8), 0, dp(8));
        content.addView(view);
    }

    private LinearLayout cardContainer(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(rounded(color, 18));
        return box;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button button(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.rgb(7, 31, 22) : text);
        button.setBackground(rounded(primary ? accent : cardSoft, 14));
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        return button;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setLineSpacing(0f, 1.08f);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(47, 51, 59));
        line.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return line;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams sectionMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(18));
        return lp;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, margin, 0, 0);
        return lp;
    }

    private String formatTime(long epochMs) {
        if (epochMs <= 0) return "";
        return new SimpleDateFormat("MMM d  •  HH:mm", Locale.getDefault()).format(new Date(epochMs));
    }

    private String shortId(String value) {
        if (value == null) return "";
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
