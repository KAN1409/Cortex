package com.kareem.cortex.prime.capture.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class VoiceCaptureController {
    private VoiceCaptureController() {}

    public static void start(Context context) {
        Intent intent = new Intent(context, PrimeVoiceRecordingService.class)
                .setAction(PrimeVoiceRecordingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, PrimeVoiceRecordingService.class)
                .setAction(PrimeVoiceRecordingService.ACTION_STOP);
        context.startService(intent);
    }

    public static void recoverPending(Context context) {
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            VoiceEvidenceCommitter.recoverPending(app);
            VoiceTranscriptionProcessor.recoverUntranscribed(app);
        }, "prime-voice-recovery");
        thread.setDaemon(true);
        thread.start();
    }
}
