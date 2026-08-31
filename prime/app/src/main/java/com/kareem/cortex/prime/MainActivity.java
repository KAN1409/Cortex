package com.kareem.cortex.prime;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kareem.cortex.prime.capture.voice.VoiceCaptureController;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 71;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VoiceCaptureController.recoverPending(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(247, 247, 247));

        TextView title = new TextView(this);
        title.setText("Cortex Prime");
        title.setTextSize(30f);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Evidence first. Models propose. Cortex validates.\n\nNotification capture: ready\nVoice capture: 16 kHz mono WAV → immutable evidence");
        subtitle.setTextSize(16f);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 24, 0, 32);

        Button startVoice = new Button(this);
        startVoice.setText("Record voice evidence");
        startVoice.setOnClickListener(v -> requestOrStartVoice());

        Button stopVoice = new Button(this);
        stopVoice.setText("Stop recording");
        stopVoice.setOnClickListener(v -> {
            VoiceCaptureController.stop(this);
            Toast.makeText(this, "Finishing voice evidence…", Toast.LENGTH_SHORT).show();
        });

        root.addView(title);
        root.addView(subtitle);
        root.addView(startVoice, matchWrap());
        root.addView(stopVoice, matchWrap());
        setContentView(root);
    }

    private void requestOrStartVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            VoiceCaptureController.start(this);
            Toast.makeText(this, "Recording voice evidence", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
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

    private static ViewGroup.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}
