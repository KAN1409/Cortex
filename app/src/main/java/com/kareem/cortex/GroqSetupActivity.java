package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** One-time setup for the user's own free Groq API key. */
public final class GroqSetupActivity extends Activity {
    private final int bg = Color.rgb(16, 17, 20);
    private final int panel = Color.rgb(24, 26, 31);
    private final int text = Color.rgb(243, 244, 246);
    private final int muted = Color.rgb(165, 168, 176);
    private final int accent = Color.rgb(143, 169, 255);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean forced = Intent.ACTION_VIEW.equals(getIntent().getAction());
        if (!forced && GroqKeyStore.isConfigured(this)) {
            openCortex();
            return;
        }
        buildUi();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String value, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        return b;
    }

    private void buildUi() {
        boolean existing = GroqKeyStore.isConfigured(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        scroll.addView(root);

        TextView title = label("CORTEX PRIME", 28, text);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView heading = label("Free cloud voice setup", 20, text);
        heading.setTypeface(null, 1);
        heading.setPadding(0, dp(18), 0, dp(8));
        root.addView(heading);

        TextView copy = label(
                "Cortex now transcribes directly with Groq Whisper Large v3.\n\n" +
                "• No Cortex backend\n" +
                "• No local speech model\n" +
                "• Your Groq key is encrypted with Android Keystore\n" +
                "• The key is never committed to GitHub or baked into the APK\n" +
                "• Arabic + English language switching stays in the original script",
                15, muted);
        copy.setLineSpacing(0f, 1.15f);
        root.addView(copy);

        if (existing) {
            TextView status = label("✓ A Groq key is already stored on this device.", 14, accent);
            status.setPadding(0, dp(18), 0, dp(8));
            root.addView(status);
        }

        EditText key = new EditText(this);
        key.setHint(existing ? "Paste a replacement Groq API key" : "Paste Groq API key (gsk_…)");
        key.setHintTextColor(muted);
        key.setTextColor(text);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setBackgroundColor(panel);
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-1, dp(54));
        kp.setMargins(0, dp(18), 0, dp(10));
        root.addView(key, kp);

        Button getKey = button("GET FREE GROQ KEY");
        getKey.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")));
            } catch (Exception e) {
                Toast.makeText(this, "Open console.groq.com/keys in your browser", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(getKey, new LinearLayout.LayoutParams(-1, dp(52)));

        Button save = button(existing ? "SAVE REPLACEMENT & OPEN CORTEX" : "SAVE KEY & OPEN CORTEX");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.setMargins(0, dp(10), 0, 0);
        root.addView(save, sp);
        save.setOnClickListener(v -> {
            String value = key.getText().toString().trim();
            if (value.length() < 20) {
                Toast.makeText(this, "Paste your Groq API key first", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                GroqKeyStore.save(this, value);
                key.setText("");
                Toast.makeText(this, "Groq key encrypted on this device", Toast.LENGTH_SHORT).show();
                openCortex();
            } catch (Exception e) {
                Toast.makeText(this, "Could not store key: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        if (existing) {
            Button open = button("OPEN CORTEX WITHOUT CHANGING KEY");
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(52));
            op.setMargins(0, dp(10), 0, 0);
            root.addView(open, op);
            open.setOnClickListener(v -> openCortex());

            Button clear = button("REMOVE SAVED GROQ KEY");
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
            cp.setMargins(0, dp(18), 0, 0);
            root.addView(clear, cp);
            clear.setOnClickListener(v -> {
                GroqKeyStore.clear(this);
                Toast.makeText(this, "Saved Groq key removed", Toast.LENGTH_SHORT).show();
                recreate();
            });
        }

        TextView footer = label(
                "Tip: later you can reopen this screen with the Cortex ASR settings link (cortex://asr-settings).",
                12, muted);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(22), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void openCortex() {
        Intent intent = new Intent(this, BrainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
