package com.kareem.cortex;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** Device-facing setup and live diagnostics for the Cortex ↔ ChatGPT Gmail bridge. */
public final class ChatGptGmailBridgeActivity extends Activity {
    private static final int REQ_ACCOUNT = 4101;
    private static final int REQ_CONSENT = 4102;
    private static final String PREFS = "chatgpt_gmail_bridge";
    private static final String KEY_ACCOUNT = "account";

    private TextView status;
    private Button choose;
    private Button handshake;
    private Button poll;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CortexUi.applyWindow(this);
        build();
        refresh();
    }

    private int dp(int v) { return CortexUi.dp(this, v); }

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
        TextView title = CortexUi.plain(this, "ChatGPT Gmail Bridge", 24, CortexUi.TEXT);
        CortexUi.medium(title);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(head);

        TextView explainer = CortexUi.text(this,
                "Gmail is transport only. Cortex sends a grounded test request; ChatGPT judges the same original input independently, then returns a correlated PASS/WARN/FAIL verdict. No bridge message becomes canonical evidence.",
                12, CortexUi.MUTED);
        explainer.setPadding(0, dp(8), 0, dp(14));
        body.addView(explainer);

        LinearLayout card = CortexUi.card(this, 18);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        status = CortexUi.text(this, "Not configured", 13, CortexUi.TEXT);
        card.addView(status);
        body.addView(card);

        choose = new Button(this);
        choose.setText("CHOOSE GOOGLE ACCOUNT");
        choose.setAllCaps(false);
        choose.setOnClickListener(v -> chooseAccount());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(-1, dp(52));
        p1.setMargins(0, dp(14), 0, 0);
        body.addView(choose, p1);

        handshake = new Button(this);
        handshake.setText("SEND LIVE CORTEX HANDSHAKE");
        handshake.setAllCaps(false);
        handshake.setOnClickListener(v -> authorizeAndHandshake());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, dp(52));
        p2.setMargins(0, dp(10), 0, 0);
        body.addView(handshake, p2);

        poll = new Button(this);
        poll.setText("POLL CHATGPT VERDICTS");
        poll.setAllCaps(false);
        poll.setOnClickListener(v -> pollVerdicts());
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(-1, dp(52));
        p3.setMargins(0, dp(10), 0, 0);
        body.addView(poll, p3);

        TextView auth = CortexUi.text(this,
                "Cortex never stores a Gmail password or OAuth token. Android's Google account authenticator provides an in-memory token after consent. If Google refuses the Gmail scope for this package/signing identity, the bridge reports AUTH_REQUIRED instead of pretending the send succeeded.",
                11, CortexUi.AMBER);
        auth.setPadding(0, dp(12), 0, 0);
        body.addView(auth);

        setContentView(root);
        CortexUi.fitSystemBars(this, root);
    }

    private void chooseAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(
                null, null,
                new String[]{AccountManagerGmailTokenProvider.GOOGLE_ACCOUNT_TYPE},
                false, "Choose the Gmail account used by Cortex Bridge", null, null, null);
        startActivityForResult(intent, REQ_ACCOUNT);
    }

    private void authorizeAndHandshake() {
        String account = account();
        if (account.isEmpty()) {
            Toast.makeText(this, "Choose a Google account first", Toast.LENGTH_LONG).show();
            chooseAccount();
            return;
        }
        setBusy(true, "Requesting Gmail authorization…");
        new Thread(() -> {
            try {
                AccountManagerGmailTokenProvider provider = new AccountManagerGmailTokenProvider(getApplicationContext(), account);
                provider.getAccessToken();
                GmailBridgeTransport transport = new GmailBridgeTransport(provider, account);
                ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(getApplicationContext(), transport);
                JSONObject original = new JSONObject()
                        .put("kind", "BRIDGE_HANDSHAKE")
                        .put("instruction", "Judge whether the transport request is valid and grounded. Do not create canonical facts.")
                        .put("devicePackage", getPackageName());
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
                    setBusy(false, result.sent
                            ? "Handshake sent from Cortex. Gmail message id: " + result.gmailMessageId
                            : "Handshake failed: " + result.error);
                    refresh();
                });
            } catch (AccountManagerGmailTokenProvider.AuthRequiredException auth) {
                runOnUiThread(() -> {
                    setBusy(false, "Google authorization required");
                    if (auth.consentIntent != null) startActivityForResult(auth.consentIntent, REQ_CONSENT);
                    else Toast.makeText(this, auth.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> setBusy(false, "Bridge error: " + t.getClass().getSimpleName() + ": " + safeMessage(t)));
            }
        }, "cortex-gmail-handshake").start();
    }

    private void pollVerdicts() {
        String account = account();
        if (account.isEmpty()) {
            chooseAccount();
            return;
        }
        setBusy(true, "Polling Gmail for ChatGPT verdicts…");
        new Thread(() -> {
            try {
                AccountManagerGmailTokenProvider provider = new AccountManagerGmailTokenProvider(getApplicationContext(), account);
                provider.getAccessToken();
                ChatGptBridgeCoordinator coordinator = new ChatGptBridgeCoordinator(
                        getApplicationContext(), new GmailBridgeTransport(provider, account));
                ChatGptBridgeCoordinator.PollResult result = coordinator.pollVerdicts(
                        System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L, 100);
                runOnUiThread(() -> {
                    setBusy(false, result.ok
                            ? "Verdicts: " + result.accepted + " accepted · " + result.rejected + " rejected · " + result.seen + " seen"
                            : "Poll failed: " + result.error);
                    refresh();
                });
            } catch (AccountManagerGmailTokenProvider.AuthRequiredException auth) {
                runOnUiThread(() -> {
                    setBusy(false, "Google authorization required");
                    if (auth.consentIntent != null) startActivityForResult(auth.consentIntent, REQ_CONSENT);
                    else Toast.makeText(this, auth.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> setBusy(false, "Poll error: " + t.getClass().getSimpleName() + ": " + safeMessage(t)));
            }
        }, "cortex-gmail-poll").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ACCOUNT && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (name != null && !name.trim().isEmpty()) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ACCOUNT, name.trim()).apply();
                refresh();
                authorizeAndHandshake();
            }
        } else if (requestCode == REQ_CONSENT) {
            refresh();
            if (resultCode == RESULT_OK) authorizeAndHandshake();
        }
    }

    private String account() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ACCOUNT, "");
    }

    private void refresh() {
        if (status == null) return;
        String account = account();
        try {
            ChatGptBridgeStore store = new ChatGptBridgeStore(getApplicationContext());
            JSONObject s = store.summary();
            status.setText((account.isEmpty() ? "Google account: not selected" : "Google account: " + account)
                    + "\nPending requests: " + s.optInt("pending", 0)
                    + "\nAccepted verdicts: " + s.optInt("verdicts", 0)
                    + "\nRejected responses: " + s.optInt("rejected", 0)
                    + "\nTeaching: DISABLED");
            handshake.setEnabled(!account.isEmpty());
            poll.setEnabled(!account.isEmpty());
        } catch (Throwable t) {
            status.setText("Bridge store unavailable: " + t.getClass().getSimpleName());
        }
    }

    private void setBusy(boolean busy, String text) {
        choose.setEnabled(!busy);
        handshake.setEnabled(!busy && !account().isEmpty());
        poll.setEnabled(!busy && !account().isEmpty());
        status.setText(text);
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? "" : t.getMessage();
    }
}
