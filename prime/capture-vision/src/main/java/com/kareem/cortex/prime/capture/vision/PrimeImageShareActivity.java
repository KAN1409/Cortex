package com.kareem.cortex.prime.capture.vision;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Receives images from Android Share and commits them before any model analysis. */
public final class PrimeImageShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showProgress();

        List<Uri> uris = sharedUris(getIntent());
        if (uris.isEmpty()) {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new Thread(() -> {
            int stored = 0;
            for (Uri uri : uris) {
                try {
                    ImageEvidenceCapture.captureSharedImage(this, uri);
                    stored++;
                } catch (Exception ignored) {
                    // Preserve successful siblings when a multi-share contains one bad URI.
                }
            }
            int finalStored = stored;
            runOnUiThread(() -> {
                if (finalStored == 0) {
                    Toast.makeText(this, "Cortex Prime could not save the image", Toast.LENGTH_LONG).show();
                } else {
                    String text = finalStored == 1 ? "Image saved to Cortex Prime" : finalStored + " images saved to Cortex Prime";
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }, "cortex-prime-image-intake").start();
    }

    private void showProgress() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        ProgressBar progress = new ProgressBar(this);
        TextView label = new TextView(this);
        label.setText("Saving image to Cortex Prime…");
        label.setPadding(0, 24, 0, 0);
        root.addView(progress);
        root.addView(label);
        setContentView(root);
    }

    @SuppressWarnings("deprecation")
    private static List<Uri> sharedUris(Intent intent) {
        if (intent == null) return Collections.emptyList();
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            return uris == null ? Collections.emptyList() : uris;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            return uri == null ? Collections.emptyList() : Collections.singletonList(uri);
        }
        return Collections.emptyList();
    }
}
