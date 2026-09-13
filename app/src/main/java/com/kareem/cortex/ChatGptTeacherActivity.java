package com.kareem.cortex;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** ChatGPT judgment teacher plus the live Gmail adjudication bridge. */
public final class ChatGptTeacherActivity extends Activity {
    private static final int REQ_BRIDGE_ACCOUNT = 4511;
    private static final int REQ_BRIDGE_CONSENT = 4512;
    private static final String BRIDGE_PREFS = "chatgpt_gmail_bridge";
    private static final String BRIDGE_ACCOUNT = "account";

    private TextView status, launch, impact, promotion, rollback;
    private TextView bridgeStatus, bridgeHandshake, bridgePoll;

    private int dp(int x) { return CortexUi.dp(this, x); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CortexUi.applyWindow(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        CortexChatGptAppTeacher.ImportResult result = CortexChatGptAppTeacher.importClipboardIfReady(this);
        if (result.ok) Toast.makeText(this, "ChatGPT policy activated in Cortex", Toast.LENGTH_LONG).show();
        refresh(result);
        refreshBridge();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = CortexUi.plain(this, "‹", 34, CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView h = CortexUi.plain(this, "ChatGPT Teacher", 28, CortexUi.TEXT);
        CortexUi.medium(h);
        titles.addView(h);
        TextView sub = CortexUi.text(this, "External adjudication + bounded judgment teaching · Cortex keeps evidence and execution local.", 11, CortexUi.MUTED);
        titles.addView(sub);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        body.addView(CortexUi.section(this, "Gmail bridge · test adjudication"));
        TextView bridgeInfo = CortexUi.text(this,
                "For every grounded case Cortex keeps original pipeline input, Cortex output, and reference evidence separate. ChatGPT judges the same case independently, then returns a correlated PASS/WARN/FAIL verdict. Gmail is transport only.",
                12, CortexUi.TEXT);
        bridgeInfo.setPadding(0, dp(4), 0, dp(8));
        body.addView(bridgeInfo);
        bridgeStatus = CortexUi.text(this, "", 12, CortexUi.MUTED);
        bridgeStatus.setTextIsSelectable(true);
        body.addView(bridgeStatus);

        TextView choose = addAction(body, "CHOOSE GMAIL ACCOUNT", CortexUi.TEXT, false, 44, 8);
        choose.setOnClickListener(v -> chooseBridgeAccount());
        bridgeHandshake = addAction(body, "SEND LIVE CORTEX HANDSHAKE", CortexUi.LIME, true, 46, 8);
        bridgeHandshake.setOnClickListener(v -> authorizeAndHandshake());
        bridgePoll = addAction(body, "POLL CHATGPT VERDICTS", CortexUi.TEXT, false, 44, 8);
        bridgePoll.setOnClickListener(v -> pollBridgeVerdicts());

        body.addView(CortexUi.section(this, "Bounded teaching"));
        TextView route = CortexUi.text(this,
                "1. Cortex builds a normalized Context Pack.\n2. ChatGPT proposes a bounded Policy Pack.\n3. Cortex validates it and runs a shadow comparison.\n4. A rollback-safe canary can then be staged for CortexAttentionJudge.\n\nThe Gmail test bridge does not auto-apply teaching.",
                12, CortexUi.TEXT);
        body.addView(route);
        launch = addAction(body, "TEACH WITH CHATGPT", CortexUi.LIME, true, 46, 10);
        launch.setOnClickListener(v -> launchChatGpt());
        TextView paste = addAction(body, "PASTE POLICY FROM CLIPBOARD", CortexUi.TEXT, false, 46, 8);
        paste.setOnClickListener(v -> {
            CortexChatGptAppTeacher.ImportResult r = CortexChatGptAppTeacher.importClipboardIfReady(this);
            refresh(r);
            Toast.makeText(this, r.ok ? "Policy activated" : "Import failed: " + r.error, Toast.LENGTH_LONG).show();
        });

        body.addView(CortexUi.section(this, "Status"));
        status = CortexUi.text(this, "", 12, CortexUi.TEXT);
        status.setTextIsSelectable(true);
        body.addView(status);
        body.addView(CortexUi.section(this, "Teacher impact"));
        impact = CortexUi.text(this, "", 12, CortexUi.MUTED);
        body.addView(impact);
        body.addView(CortexUi.section(this, "Policy promotion"));
        promotion = CortexUi.text(this, "", 12, CortexUi.MUTED);
        body.addView(promotion);
        rollback = addAction(body, "ROLL BACK TO PREVIOUS POLICY", CortexUi.MUTED, false, 44, 8);
        rollback.setOnClickListener(v -> {
            boolean ok = CortexPolicyPromotion.rollback(this, "manual rollback from teacher screen");
            Toast.makeText(this, ok ? "Previous policy restored" : "No previous policy available", Toast.LENGTH_LONG).show();
            refresh(null);
        });

        body.addView(CortexUi.section(this, "Safety boundary"));
        body.addView(CortexUi.text(this,
                "ChatGPT cannot create canonical facts, classify raw notifications, execute actions, or directly surface UI. Bridge verdicts are external adjudication records. Policy teaching remains bounded and rollback-safe.",
                12, CortexUi.MUTED));

        setContentView(root);
        CortexUi.fitSystemBars(this, root);
        refresh(null);
        refreshBridge();
    }

    private TextView addAction(LinearLayout body, String text, int color, boolean filled, int height, int topMargin) {
        TextView view = CortexUi.action(this, text, color, filled);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(height));
        p.setMargins(0, dp(topMargin), 0, 0);
        body.addView(view, p);
        return view;
    }

    private void chooseBridgeAccount() {
        Intent picker = AccountManager.newChooseAccountIntent(
                null,
                null,
                new String[]{AccountManagerGmailTokenProvider.GOOGLE_ACCOUNT_TYPE},
                "Choose Cortex Bridge Gmail account",
                null,
                null,
                null);
        startActivityForResult(picker, REQ_BRIDGE_ACCOUNT);
    }

    private String bridgeAccount() {
        return getSharedPreferences(BRIDGE_PREFS, MODE_PRIVATE).getString(BRIDGE_ACCOUNT, "");
    }

    private void authorizeAndHandshake() {
        String account = bridgeAccount();
        if (account.isEmpty()) { chooseBridgeAccount(); return; }
        setBridgeBusy(true, "Requesting Gmail authorization…");
        new Thread(() -> {
            try {
                AccountManagerGmailTokenProvider provider = new AccountManagerGmailTokenProvider(getApplicationContext(), account);
                provider.getAccessToken();
                ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(
                        getApplicationContext(), new GmailBridgeTransport(provider, account));
                JSONObject original = new JSONObject()
                        .put("kind", "BRIDGE_HANDSHAKE")
                        .put("instruction", "Judge this grounded transport request independently; do not create canonical facts.")
                        .put("package", getPackageName());
                JSONObject cortex = new JSONObject()
                        .put("bridgeReady", true)
                        .put("transport", "GMAIL_REST")
                        .put("teachingEnabled", false);
                JSONObject reference = new JSONObject()
                        .put("expectedTransport", "GMAIL_REST")
                        .put("expectedTeachingEnabled", false);
                ChatGptBridgeCoordinator.DispatchResult result = coordinator.dispatchTest(
                        "bridge-handshake-" + System.currentTimeMillis(),
                        "transport-handshake",
                        original, cortex, reference);
                runOnUiThread(() -> {
                    setBridgeBusy(false, result.sent
                            ? "Handshake sent from Cortex · Gmail id " + result.gmailMessageId
                            : "Handshake failed · " + result.error);
                    refreshBridge();
                });
            } catch (AccountManagerGmailTokenProvider.AuthRequiredException e) {
                runOnUiThread(() -> {
                    setBridgeBusy(false, "Google authorization required");
                    if (e.consentIntent != null) startActivityForResult(e.consentIntent, REQ_BRIDGE_CONSENT);
                    else Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> setBridgeBusy(false,
                        "Bridge error · " + e.getClass().getSimpleName() + " · " + safeMessage(e)));
            }
        }, "cortex-gmail-bridge-handshake").start();
    }

    private void pollBridgeVerdicts() {
        String account = bridgeAccount();
        if (account.isEmpty()) { chooseBridgeAccount(); return; }
        setBridgeBusy(true, "Polling Gmail for ChatGPT verdicts…");
        new Thread(() -> {
            try {
                AccountManagerGmailTokenProvider provider = new AccountManagerGmailTokenProvider(getApplicationContext(), account);
                provider.getAccessToken();
                ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(
                        getApplicationContext(), new GmailBridgeTransport(provider, account));
                ChatGptBridgeCoordinator.PollResult result = coordinator.pollVerdicts(
                        System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L, 100);
                runOnUiThread(() -> {
                    setBridgeBusy(false, result.ok
                            ? "Verdicts · " + result.accepted + " accepted · " + result.rejected + " rejected · " + result.seen + " seen"
                            : "Poll failed · " + result.error);
                    refreshBridge();
                });
            } catch (AccountManagerGmailTokenProvider.AuthRequiredException e) {
                runOnUiThread(() -> {
                    setBridgeBusy(false, "Google authorization required");
                    if (e.consentIntent != null) startActivityForResult(e.consentIntent, REQ_BRIDGE_CONSENT);
                    else Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> setBridgeBusy(false,
                        "Poll error · " + e.getClass().getSimpleName() + " · " + safeMessage(e)));
            }
        }, "cortex-gmail-bridge-poll").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BRIDGE_ACCOUNT && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (name != null && !name.trim().isEmpty()) {
                getSharedPreferences(BRIDGE_PREFS, MODE_PRIVATE).edit().putString(BRIDGE_ACCOUNT, name.trim()).apply();
                refreshBridge();
                authorizeAndHandshake();
            }
        } else if (requestCode == REQ_BRIDGE_CONSENT) {
            refreshBridge();
            if (resultCode == RESULT_OK) authorizeAndHandshake();
        }
    }

    private void refreshBridge() {
        if (bridgeStatus == null) return;
        String account = bridgeAccount();
        try {
            JSONObject s = new ChatGptBridgeStore(getApplicationContext()).summary();
            bridgeStatus.setText((account.isEmpty() ? "Gmail account: not selected" : "Gmail account: " + account)
                    + "\nPending requests: " + s.optInt("pending", 0)
                    + "\nAccepted verdicts: " + s.optInt("verdicts", 0)
                    + "\nRejected responses: " + s.optInt("rejected", 0)
                    + "\nTeaching over Gmail bridge: DISABLED");
            boolean configured = !account.isEmpty();
            bridgeHandshake.setEnabled(configured);
            bridgePoll.setEnabled(configured);
        } catch (Throwable e) {
            bridgeStatus.setText("Bridge store unavailable · " + e.getClass().getSimpleName());
        }
    }

    private void setBridgeBusy(boolean busy, String text) {
        bridgeStatus.setText(text);
        bridgeHandshake.setEnabled(!busy && !bridgeAccount().isEmpty());
        bridgePoll.setEnabled(!busy && !bridgeAccount().isEmpty());
    }

    private void refresh(CortexChatGptAppTeacher.ImportResult latest) {
        if (status == null) return;
        status.setText(statusText(latest));
        JSONObject latestImpact = CortexTeacherImpact.latest(this);
        if (latestImpact.length() == 0) impact.setText("No shadow comparison yet.");
        else if (latestImpact.optInt("candidateCount", 0) == 0) {
            impact.setText(CortexTeacherImpact.summary(this)
                    + "\nNo canonical candidates were available. This is NOT counted as a passed behavioral test.");
        } else impact.setText(CortexTeacherImpact.summary(this));
        promotion.setText(CortexPolicyPromotion.status(this));
        rollback.setEnabled(CortexPolicyPromotion.canRollback(this));
    }

    private void launchChatGpt() {
        launch.setEnabled(false);
        launch.setText("PREPARING…");
        new Thread(() -> {
            try { CortexChatGptAppTeacher.launch(this); }
            catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not open ChatGPT: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> {
                    launch.setEnabled(true);
                    launch.setText("TEACH WITH CHATGPT");
                    refresh(null);
                });
            }
        }, "cortex-chatgpt-app-teacher").start();
    }

    private String statusText(CortexChatGptAppTeacher.ImportResult latest) {
        StringBuilder b = new StringBuilder();
        b.append("Active policy: ").append(CortexPersonalPolicy.version(this));
        b.append("\nPending ChatGPT request: ").append(CortexChatGptAppTeacher.pending(this) ? "YES" : "NO");
        b.append("\nLifecycle: ").append(CortexPolicyLifecycle.state(this));
        String reason = CortexPolicyLifecycle.reason(this);
        if (!reason.isEmpty()) b.append(" · ").append(reason);
        if (latest != null && latest.ok) b.append("\nLast import: SUCCESS · ").append(latest.version);
        JSONObject i = CortexTeacherImpact.latest(this);
        if (i.length() > 0) b.append("\nLive comparison candidates: ").append(i.optInt("candidateCount", 0));
        b.append("\nPromotion: ").append(CortexPolicyPromotion.status(this).replace("\n", " · "));
        return b.toString();
    }

    private static String safeMessage(Throwable t) { return t.getMessage() == null ? "" : t.getMessage(); }
}
