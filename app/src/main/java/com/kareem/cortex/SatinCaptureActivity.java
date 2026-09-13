package com.kareem.cortex;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Voice capture variant with Cortex's circular elapsed recording gauge. */
public class SatinCaptureActivity extends CaptureActivity {
    /**
     * v98 root repair: recording is an evidence operation, not a cloud-provider operation.
     * AudioAnalyzer now prefers Android on-device ASR and only falls back to configured cloud
     * providers. Do not block recording merely because no API key exists.
     */
    @Override void startVoice(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;
        }
        beginVoice();
    }

    @Override void showRecordingPanel(){
        choices.setVisibility(View.GONE);recordPanel=new LinearLayout(this);recordPanel.setOrientation(LinearLayout.VERTICAL);recordPanel.setGravity(Gravity.CENTER_HORIZONTAL);recordPanel.setPadding(0,dp(12),0,0);
        TextView signal=CortexUi.plain(this,"●  RECORDING SIGNAL",10,CortexUi.SIGNAL);signal.setLetterSpacing(.12f);signal.setGravity(Gravity.CENTER);recordPanel.addView(signal);
        CortexRingButton ring=new CortexRingButton(this);ring.setGlyph(CortexRingButton.Glyph.STOP);ring.setAccent(CortexUi.SIGNAL);ring.setProgress(0f);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(108),dp(108));rp.setMargins(0,dp(10),0,0);recordPanel.addView(ring,rp);
        timer=CortexUi.plain(this,"00:00",28,CortexUi.TEXT);CortexUi.medium(timer);timer.setGravity(Gravity.CENTER);timer.setPadding(0,dp(4),0,0);recordPanel.addView(timer);
        TextView hint=CortexUi.plain(this,"TAP RING TO STOP & ANALYZE",9,CortexUi.MUTED);hint.setLetterSpacing(.10f);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(5),0,0);recordPanel.addView(hint);
        TextView discard=CortexUi.action(this,"Discard",CortexUi.SIGNAL,false);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(44));dpv.setMargins(0,dp(14),0,0);recordPanel.addView(discard,dpv);
        ring.setOnClickListener(v->finishVoice(true));discard.setOnClickListener(v->finishVoice(false));sheet.addView(recordPanel);
        tick=new Runnable(){public void run(){long elapsed=Math.max(0,System.currentTimeMillis()-recordingStarted),sec=elapsed/1000;if(timer!=null)timer.setText(String.format(Locale.US,"%02d:%02d",sec/60,sec%60));ring.setProgress((elapsed%60000L)/60000f);if(recorder.isRunning())handler.postDelayed(this,120);}};handler.post(tick);
    }
}
