package com.kareem.cortex;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.UUID;

/** Internal user-controlled surface for validating the Cortex <-> Relay V2 bridge on a real phone. */
public final class CortexRelayV2DiagnosticsActivity extends Activity {
    private LinearLayout body;

    private int dp(int x) { return CortexUi.dp(this, x); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CortexUi.applyWindow(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null) render();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(CortexUi.aurora(this));

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        CortexUi.fitSystemBars(this, root);
        render();
    }

    private void render() {
        body.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = CortexUi.plain(this, "‹", 34, CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView eye = CortexUi.plain(this, "RELAY V2", 10, CortexUi.AURORA);
        CortexUi.medium(eye);
        titles.addView(eye);
        TextView title = CortexUi.plain(this, "Cortex ↔ Relay bridge", 26, CortexUi.TEXT);
        CortexUi.bold(title);
        titles.addView(title);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        TextView intro = CortexUi.text(this,
                "Authenticated local bridge diagnostics. Nothing here executes a phone action until you explicitly confirm it.",
                12, CortexUi.MUTED);
        intro.setPadding(dp(2), dp(6), dp(2), dp(12));
        body.addView(intro);

        JSONObject snapshot = CortexRelayBridgeV2.diagnosticSnapshot(this);
        JSONObject lastSignal = snapshot.optJSONObject("last_v2_signal");
        JSONObject actionResult = snapshot.optJSONObject("last_action_result");
        JSONObject policyResult = snapshot.optJSONObject("last_policy_result");

        body.addView(CortexUi.section(this, "Connection"));
        statusCard("V2 session", snapshot.optBoolean("connected", false) ? "Connected" : "Not connected");
        statusCard("Negotiated protocol", snapshot.optString("selected_protocol", CortexLocalBusProtocolV1.PROTOCOL));
        statusCard("Connector", joinNonEmpty(snapshot.optString("connector_id", ""), snapshot.optString("connector_package", "")));

        Button refresh = button("Refresh bridge state");
        refresh.setOnClickListener(v -> render());
        body.addView(refresh, spaced());

        body.addView(CortexUi.section(this, "Safe round-trip"));
        TextView policyHelp = CortexUi.text(this,
                "Sends a no-op mechanical policy probe: keep 72h forensic retention and disable no noise rules. It changes only the policy version so the request/result path can be proven.",
                11, CortexUi.MUTED);
        body.addView(policyHelp);
        Button policy = button("Test policy round-trip");
        policy.setEnabled(snapshot.optBoolean("connected", false));
        policy.setOnClickListener(v -> {
            long version = System.currentTimeMillis();
            boolean sent = CortexRelayBridgeV2.updateMechanicalPolicy(
                    this, version, 72, new JSONArray());
            toast(sent ? "Policy probe sent to Relay" : "No active Relay V2 session");
            body.postDelayed(this::render, 500L);
        });
        body.addView(policy, spaced());
        if (policyResult != null) jsonCard("Last policy result", policyResult);

        body.addView(CortexUi.section(this, "Latest V2 signal"));
        if (lastSignal == null || lastSignal.length() == 0) {
            TextView empty = CortexUi.text(this,
                    "No V2 signal has reached Cortex in this process yet. Receive a real notification through Relay, then refresh.",
                    12, CortexUi.MUTED);
            body.addView(empty);
        } else {
            statusCard("Source", lastSignal.optString("source_package", "—"));
            statusCard("Signal type", lastSignal.optString("signal_type", "—"));
            statusCard("Logical signal", lastSignal.optString("logical_signal_id", "—"));
            long signalId = lastSignal.optLong("signal_id", 0L);
            statusCard("Cortex signal", signalId > 0 ? "#" + signalId : "—");

            JSONArray actions = lastSignal.optJSONArray("action_capabilities");
            body.addView(CortexUi.section(this, "Available Android actions"));
            if (actions == null || actions.length() == 0) {
                body.addView(CortexUi.text(this,
                        "This notification did not expose any executable Android action to Relay.",
                        12, CortexUi.MUTED));
            } else {
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject action = actions.optJSONObject(i);
                    if (action != null) addAction(action, lastSignal.optString("logical_signal_id", ""));
                }
            }
        }

        if (actionResult != null) {
            body.addView(CortexUi.section(this, "Last action result"));
            jsonCard("Relay execution result", actionResult);
        }
    }

    private void addAction(JSONObject action, String logicalSignalId) {
        String capabilityId = action.optString("capability_id", "").trim();
        String kind = action.optString("kind", "ANDROID_ACTION").trim();
        String label = action.optString("label", "").trim();
        boolean needsText = action.optBoolean("requires_text_input", false);
        if (capabilityId.isEmpty() || logicalSignalId.isEmpty()) return;

        LinearLayout card = CortexUi.card(this, 18);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = CortexUi.plain(this,
                label.isEmpty() ? kind.replace('_', ' ') : label,
                14, CortexUi.TEXT);
        CortexUi.medium(title);
        card.addView(title);
        TextView sub = CortexUi.text(this,
                kind + (needsText ? " · text input required" : "") + "\n" + capabilityId,
                10, CortexUi.MUTED);
        sub.setPadding(0, dp(3), 0, dp(8));
        card.addView(sub);
        Button run = button("Confirm & send to Relay");
        run.setOnClickListener(v -> confirmAction(kind, label, logicalSignalId, capabilityId, needsText));
        card.addView(run);
        body.addView(card, spaced());
    }

    private void confirmAction(String kind, String label, String logicalSignalId,
                               String capabilityId, boolean needsText) {
        String name = label == null || label.trim().isEmpty() ? kind.replace('_', ' ') : label.trim();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Run " + name + "?")
                .setMessage("Cortex will ask Relay to execute this exact Android capability on the currently live notification. No other action will be chosen automatically.")
                .setNegativeButton("Cancel", null);

        EditText input = null;
        if (needsText) {
            input = new EditText(this);
            input.setHint("Reply text");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(2);
            int pad = dp(18);
            input.setPadding(pad, dp(8), pad, dp(8));
            dialog.setView(input);
        }
        final EditText finalInput = input;
        dialog.setPositiveButton("Execute", (d, which) -> {
            String text = finalInput == null ? null : finalInput.getText().toString();
            if (needsText && (text == null || text.trim().isEmpty())) {
                toast("Reply text is required");
                return;
            }
            String requestId = "cortex_action_" + UUID.randomUUID();
            boolean sent = CortexRelayBridgeV2.requestAction(
                    this, requestId, logicalSignalId, capabilityId, text);
            toast(sent ? "Action request sent to Relay" : "Relay V2 session is not available");
            body.postDelayed(this::render, 700L);
        });
        dialog.show();
    }

    private void statusCard(String key, String value) {
        LinearLayout c = CortexUi.card(this, 16);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14), dp(11), dp(14), dp(11));
        TextView k = CortexUi.text(this, key, 11, CortexUi.MUTED);
        c.addView(k, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = CortexUi.plain(this, value == null || value.isEmpty() ? "—" : value, 12, CortexUi.TEXT);
        CortexUi.medium(v);
        c.addView(v);
        body.addView(c, spaced());
    }

    private void jsonCard(String title, JSONObject json) {
        LinearLayout c = CortexUi.card(this, 16);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(11), dp(14), dp(11));
        TextView t = CortexUi.plain(this, title, 13, CortexUi.TEXT);
        CortexUi.medium(t);
        c.addView(t);
        TextView j = CortexUi.text(this, json == null ? "—" : json.toString(), 10, CortexUi.MUTED);
        j.setTextIsSelectable(true);
        j.setPadding(0, dp(5), 0, 0);
        c.addView(j);
        body.addView(c, spaced());
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(8));
        return p;
    }

    private String joinNonEmpty(String a, String b) {
        a = a == null ? "" : a.trim();
        b = b == null ? "" : b.trim();
        if (a.isEmpty()) return b.isEmpty() ? "—" : b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
