package com.kareem.cortex.prime;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.kareem.cortex.prime.capture.vision.ImageEvidenceCapture;
import com.kareem.cortex.prime.capture.voice.VoiceCaptureController;
import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 71;
    private static final int REQUEST_IMAGE = 72;

    private final int bg = Color.rgb(12, 14, 18);
    private final int card = Color.rgb(24, 27, 33);
    private final int cardSoft = Color.rgb(31, 35, 42);
    private final int text = Color.rgb(244, 246, 248);
    private final int muted = Color.rgb(161, 169, 181);
    private final int accent = Color.rgb(124, 241, 191);
    private final int accentDark = Color.rgb(39, 94, 74);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        VoiceCaptureController.recoverPending(this);
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
        content.setPadding(dp(20), dp(28), dp(20), dp(40));
        scroll.removeAllViews();
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addHeader();
        addCaptureStatus();
        addQuickCapture();
        addEvidenceOverview();
        addRecentEvidence();
        addIntelligencePipeline();
        addFooter();

        setContentView(scroll);
    }

    private void addHeader() {
        TextView eyebrow = label("LOCAL CONTEXT ENGINE", 12, accent, true);
        content.addView(eyebrow);

        TextView title = label("Cortex Prime", 35, text, true);
        title.setPadding(0, dp(8), 0, 0);
        content.addView(title);

        TextView subtitle = label(
                "Your phone becomes evidence. Cortex keeps the raw truth, then intelligence works on top of it.",
                15,
                muted,
                false
        );
        subtitle.setPadding(0, dp(10), 0, dp(22));
        content.addView(subtitle);
    }

    private void addCaptureStatus() {
        boolean relayEnabled = isNotificationAccessEnabled();
        LinearLayout statusCard = cardContainer(card);

        LinearLayout row = horizontal();
        TextView dot = label("●", 16, relayEnabled ? accent : Color.rgb(255, 190, 92), true);
        TextView title = label(relayEnabled ? "Capture is live" : "Notification access is off", 17, text, true);
        title.setPadding(dp(10), 0, 0, 0);
        row.addView(dot);
        row.addView(title);
        statusCard.addView(row);

        TextView detail = label(
                relayEnabled
                        ? "Relay is inside Cortex Prime. No cross-app bridge. Notifications write straight into immutable evidence."
                        : "Turn on notification access once and Relay can feed Cortex Prime directly.",
                14,
                muted,
                false
        );
        detail.setPadding(0, dp(10), 0, 0);
        statusCard.addView(detail);

        if (!relayEnabled) {
            Button enable = actionButton("Enable notification capture", false);
            enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            statusCard.addView(enable, topMargin(dp(14)));
        }

        content.addView(statusCard, sectionMargin());
    }

    private void addQuickCapture() {
        sectionTitle("Quick capture");
        LinearLayout box = cardContainer(card);

        LinearLayout row = horizontal();
        Button voice = actionButton("●  Voice", true);
        voice.setOnClickListener(v -> requestOrStartVoice());
        row.addView(voice, weighted());

        Button image = actionButton("▣  Image", false);
        image.setOnClickListener(v -> pickImage());
        LinearLayout.LayoutParams imageLp = weighted();
        imageLp.setMarginStart(dp(10));
        row.addView(image, imageLp);
        box.addView(row);

        Button stop = actionButton("Stop voice recording", false);
        stop.setOnClickListener(v -> {
            VoiceCaptureController.stop(this);
            Toast.makeText(this, "Saving voice evidence…", Toast.LENGTH_SHORT).show();
        });
        box.addView(stop, topMargin(dp(10)));

        TextView note = label("Voice and images are preserved before any model touches them.", 13, muted, false);
        note.setPadding(0, dp(12), 0, 0);
        box.addView(note);
        content.addView(box, sectionMargin());
    }

    private void addEvidenceOverview() {
        sectionTitle("Evidence");
        List<EvidenceRecord> recent = loadEvidence(500);
        EnumMap<EvidenceSource, Integer> counts = new EnumMap<>(EvidenceSource.class);
        for (EvidenceRecord record : recent) {
            counts.put(record.source, counts.getOrDefault(record.source, 0) + 1);
        }

        LinearLayout box = cardContainer(card);
        LinearLayout metrics = horizontal();
        metrics.addView(metric("TOTAL", String.valueOf(recent.size())), weighted());
        metrics.addView(metric("NOTIFS", String.valueOf(counts.getOrDefault(EvidenceSource.NOTIFICATION, 0))), weighted());
        metrics.addView(metric("VOICE", String.valueOf(counts.getOrDefault(EvidenceSource.VOICE, 0))), weighted());
        metrics.addView(metric("IMAGES", String.valueOf(counts.getOrDefault(EvidenceSource.IMAGE, 0))), weighted());
        box.addView(metrics);

        TextView hint = label("Counts show the most recent 500 evidence records.", 12, muted, false);
        hint.setPadding(0, dp(12), 0, 0);
        box.addView(hint);
        content.addView(box, sectionMargin());
    }

    private void addRecentEvidence() {
        sectionTitle("Recent evidence");
        List<EvidenceRecord> records = loadEvidence(8);
        LinearLayout box = cardContainer(card);

        if (records.isEmpty()) {
            TextView empty = label("Nothing captured yet. Record a voice note, share an image, or enable notification capture.", 14, muted, false);
            box.addView(empty);
        } else {
            for (int i = 0; i < records.size(); i++) {
                EvidenceRecord record = records.get(i);
                if (i > 0) box.addView(divider());
                box.addView(evidenceRow(record));
            }
        }
        content.addView(box, sectionMargin());
    }

    private void addIntelligencePipeline() {
        sectionTitle("Local intelligence");
        LinearLayout box = cardContainer(card);

        TextView intro = label("Five specialist roles, one grounded pipeline. Capture is ready; model adapters are the next layer.", 14, muted, false);
        intro.setPadding(0, 0, 0, dp(8));
        box.addView(intro);

        box.addView(modelRow("01", "ASR", "Voice → transcript", true));
        box.addView(modelRow("02", "Vision / OCR", "Image → visible text + facts", true));
        box.addView(modelRow("03", "Extractor", "People, dates, tasks, events", false));
        box.addView(modelRow("04", "Linker", "Relate + dedupe evidence", false));
        box.addView(modelRow("05", "Organizer", "Propose the Cortex view", false));

        TextView guard = label("Models propose. Validator decides what may become Cortex state.", 13, accent, true);
        guard.setPadding(0, dp(12), 0, 0);
        box.addView(guard);
        content.addView(box, sectionMargin());
    }

    private void addFooter() {
        TextView footer = label("CORTEX PRIME  •  0.2.0 DASHBOARD", 11, muted, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, 0);
        content.addView(footer);
    }

    private View evidenceRow(EvidenceRecord record) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout top = horizontal();
        TextView source = label(sourceLabel(record.source), 12, accent, true);
        TextView time = label(formatTime(record.capturedAtEpochMs), 12, muted, false);
        time.setGravity(Gravity.END);
        top.addView(source, weighted());
        top.addView(time, weighted());
        row.addView(top);

        String body = record.rawText == null ? "" : record.rawText.trim();
        if (body.isEmpty()) {
            if (record.source == EvidenceSource.VOICE) body = "Voice evidence preserved";
            else if (record.source == EvidenceSource.IMAGE) body = "Image evidence preserved";
            else body = record.sourceRef == null ? "Evidence preserved" : record.sourceRef;
        }
        if (body.length() > 180) body = body.substring(0, 180) + "…";
        TextView textView = label(body, 15, text, false);
        textView.setPadding(0, dp(6), 0, 0);
        row.addView(textView);
        return row;
    }

    private View modelRow(String index, String name, String job, boolean captureAvailable) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView number = label(index, 12, muted, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(rounded(cardSoft, 10));
        number.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.addView(number);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(label(name, 15, text, true));
        copy.addView(label(job, 12, muted, false));
        row.addView(copy, weighted());

        TextView status = label(captureAvailable ? "INPUT READY" : "NEXT", 10, captureAvailable ? accent : muted, true);
        status.setPadding(dp(8), dp(5), dp(8), dp(5));
        status.setBackground(rounded(captureAvailable ? accentDark : cardSoft, 10));
        row.addView(status);
        return row;
    }

    private LinearLayout metric(String name, String value) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.addView(label(value, 25, text, true));
        metric.addView(label(name, 10, muted, true));
        return metric;
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

    private void requestOrStartVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            VoiceCaptureController.start(this);
            Toast.makeText(this, "Recording voice evidence", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            VoiceCaptureController.start(this);
            Toast.makeText(this, "Recording voice evidence", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Microphone permission is required for voice capture", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        new Thread(() -> {
            try {
                ImageEvidenceCapture.Outcome outcome = ImageEvidenceCapture.captureSharedImage(this, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Image preserved as evidence", Toast.LENGTH_SHORT).show();
                    render();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, "Could not preserve image evidence", Toast.LENGTH_LONG).show());
            }
        }, "prime-dashboard-image-capture").start();
    }

    private void sectionTitle(String title) {
        TextView label = label(title, 13, muted, true);
        label.setPadding(0, dp(8), 0, dp(8));
        content.addView(label);
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

    private Button actionButton(String title, boolean primary) {
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(18));
        return lp;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, margin, 0, 0);
        return lp;
    }

    private String sourceLabel(EvidenceSource source) {
        switch (source) {
            case NOTIFICATION: return "NOTIFICATION";
            case VOICE: return "VOICE";
            case IMAGE: return "IMAGE";
            case OCR: return "OCR";
            case FILE: return "FILE";
            case SHARE: return "SHARE";
            default: return "TEXT";
        }
    }

    private String formatTime(long epochMs) {
        return new SimpleDateFormat("MMM d  •  HH:mm", Locale.getDefault()).format(new Date(epochMs));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
